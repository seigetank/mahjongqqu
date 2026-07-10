package com.mahjongqqu.server

import com.mahjongqqu.engine.BoardConfig
import com.mahjongqqu.engine.GameEngine
import com.mahjongqqu.engine.GameMode
import com.mahjongqqu.engine.GameStatus
import com.mahjongqqu.engine.ScoreRules
import com.mahjongqqu.engine.Seed
import com.mahjongqqu.engine.Selection
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        mahjongTenModule()
    }.start(wait = true)
}

fun Application.mahjongTenModule(
    clock: Clock = Clock.systemUTC(),
    sessionStore: SessionStore = InMemorySessionStore(),
    leaderboardStore: LeaderboardStore = InMemoryLeaderboardStore(),
) {
    val boardConfig = BoardConfig()
    val scoreRules = ScoreRules()
    val engine = GameEngine(boardConfig, scoreRules)
    val signer = SessionSigner(secret = System.getenv("SESSION_SECRET") ?: "local-dev-secret-change-me")
    val requireIntegrity = System.getenv("REQUIRE_PLAY_INTEGRITY") == "true"
    val secureRandom = SecureRandom()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("server_error", cause.message ?: "Server error"))
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", now = clock.instant().toString()))
        }

        post("/v1/game-sessions") {
            val request = call.receive<CreateGameSessionRequest>()
            val mode = request.mode.toGameMode()
            val seedBytes = ByteArray(16)
            secureRandom.nextBytes(seedBytes)
            val seed = Seed.fromBytes(seedBytes)
            val now = clock.instant()
            val expiresAt = now.plusSeconds(5 * 60)
            val seasonId = currentSeasonId(clock)
            val sessionId = UUID.randomUUID().toString()
            val token = signer.sign(sessionId, seed.toBase64(), expiresAt)

            val record = SessionRecord(
                sessionId = sessionId,
                mode = mode,
                seasonId = seasonId,
                seed = seed.toBase64(),
                boardVersion = boardConfig.boardVersion,
                scoreRuleVersion = scoreRules.scoreRuleVersion,
                issuedAt = now,
                expiresAt = expiresAt,
                sessionToken = token,
            )
            sessionStore.put(record)

            call.respond(
                CreateGameSessionResponse(
                    sessionId = record.sessionId,
                    mode = record.mode.name,
                    seasonId = record.seasonId,
                    seed = record.seed,
                    boardVersion = record.boardVersion,
                    scoreRuleVersion = record.scoreRuleVersion,
                    issuedAt = record.issuedAt.epochSecond,
                    expiresAt = record.expiresAt.epochSecond,
                    sessionToken = record.sessionToken,
                ),
            )
        }

        post("/v1/game-results") {
            val request = call.receive<SubmitGameResultRequest>()
            val session = sessionStore.get(request.sessionId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("session_not_found", "Session was not found."))

            val now = clock.instant()
            val mode = request.mode.toGameMode()
            val validation = validateResult(
                request = request,
                session = session,
                mode = mode,
                now = now,
                signer = signer,
                requireIntegrity = requireIntegrity,
                engine = engine,
            )

            if (!validation.accepted) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    SubmitGameResultResponse(
                        accepted = false,
                        validationStatus = validation.status,
                        officialScore = validation.officialScore,
                        rank = null,
                    ),
                )
            }

            if (!sessionStore.markSubmitted(request.sessionId)) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("duplicate_submission", "This session already has an official result."),
                )
            }

            val rank = leaderboardStore.upsert(
                LeaderboardEntry(
                    seasonId = session.seasonId,
                    mode = mode,
                    playerId = request.playerId.ifBlank { "local-player" },
                    score = validation.officialScore,
                    clearedTiles = validation.officialClearedTiles,
                    clearedBoards = validation.officialClearedBoards,
                    lastActionMs = request.actions.maxOfOrNull { it.elapsedMs } ?: 0L,
                    updatedAt = now,
                ),
            )

            call.respond(
                SubmitGameResultResponse(
                    accepted = true,
                    validationStatus = validation.status,
                    officialScore = validation.officialScore,
                    rank = rank,
                ),
            )
        }

        get("/v1/leaderboards/{seasonId}/{mode}") {
            val seasonId = call.parameters["seasonId"] ?: currentSeasonId(clock)
            val mode = (call.parameters["mode"] ?: "RANDOM").toGameMode()
            call.respond(
                LeaderboardResponse(
                    seasonId = seasonId,
                    mode = mode.name,
                    entries = leaderboardStore.top(seasonId, mode, limit = 100).mapIndexed { index, entry ->
                        LeaderboardEntryDto(
                            rank = index + 1,
                            playerId = entry.playerId,
                            score = entry.score,
                            clearedTiles = entry.clearedTiles,
                            clearedBoards = entry.clearedBoards,
                            lastActionMs = entry.lastActionMs,
                        )
                    },
                ),
            )
        }
    }
}

private fun validateResult(
    request: SubmitGameResultRequest,
    session: SessionRecord,
    mode: GameMode,
    now: Instant,
    signer: SessionSigner,
    requireIntegrity: Boolean,
    engine: GameEngine,
): ValidationResult {
    if (session.submitted) {
        return ValidationResult.rejected("duplicate_submission")
    }
    if (now > session.expiresAt) {
        return ValidationResult.rejected("session_expired")
    }
    if (mode != session.mode || request.seasonId != session.seasonId || request.seed != session.seed) {
        return ValidationResult.rejected("session_mismatch")
    }
    if (request.boardVersion != session.boardVersion || request.scoreRuleVersion != session.scoreRuleVersion) {
        return ValidationResult.rejected("rule_version_mismatch")
    }
    if (!signer.verify(session.sessionId, session.seed, session.expiresAt, request.sessionToken)) {
        return ValidationResult.rejected("bad_session_token")
    }
    if (requireIntegrity && request.playIntegrityToken.isBlank()) {
        return ValidationResult.rejected("missing_play_integrity")
    }

    val expectedHash = hashActionLog(request.actions)
    if (request.logHash.isNotBlank() && request.logHash != expectedHash) {
        return ValidationResult.rejected("bad_log_hash")
    }

    var state = engine.newGame(mode, Seed.fromBase64(request.seed))
    var expectedSequence = 0

    for (action in request.actions.sortedBy { it.sequence }) {
        if (action.sequence != expectedSequence) {
            return ValidationResult.rejected("bad_sequence", officialScore = state.score)
        }
        if (action.boardIndex != state.boardIndex) {
            return ValidationResult.rejected("bad_board_index", officialScore = state.score)
        }
        val outcome = engine.applySelection(
            state = state,
            selection = Selection(action.startRow, action.startColumn, action.endRow, action.endColumn),
            elapsedMs = action.elapsedMs,
        )
        if (!outcome.evaluation.valid) {
            return ValidationResult.rejected("invalid_action", officialScore = state.score)
        }
        state = outcome.nextState
        if (state.status == GameStatus.NO_MOVES && state.mode == GameMode.ENDLESS) {
            state = engine.nextEndlessBoard(state)
        }
        expectedSequence += 1
    }

    if (
        state.score != request.finalScore ||
        state.clearedTileCount != request.clearedTiles ||
        state.boardIndex + 1 != request.clearedBoards
    ) {
        return ValidationResult.rejected(
            status = "score_mismatch",
            officialScore = state.score,
            officialClearedTiles = state.clearedTileCount,
            officialClearedBoards = state.boardIndex + 1,
        )
    }

    return ValidationResult(
        accepted = true,
        status = if (requireIntegrity) "verified" else "verified_without_integrity",
        officialScore = state.score,
        officialClearedTiles = state.clearedTileCount,
        officialClearedBoards = state.boardIndex + 1,
    )
}

private fun String.toGameMode(): GameMode =
    GameMode.valueOf(trim().uppercase())

private fun currentSeasonId(clock: Clock): String {
    val zone = ZoneId.of("Asia/Seoul")
    val date = LocalDate.now(clock.withZone(zone))
    val weekFields = WeekFields.ISO
    val year = date.get(weekFields.weekBasedYear())
    val week = date.get(weekFields.weekOfWeekBasedYear())
    return "%04d-W%02d".format(year, week)
}

private fun hashActionLog(actions: List<GameActionDto>): String {
    val canonical = actions.sortedBy { it.sequence }.joinToString(";") {
        listOf(
            it.sequence,
            it.boardIndex,
            it.startRow,
            it.startColumn,
            it.endRow,
            it.endColumn,
            it.elapsedMs,
        ).joinToString(",")
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

class SessionSigner(secret: String) {
    private val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    fun sign(sessionId: String, seed: String, expiresAt: Instant): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val payload = "$sessionId|$seed|${expiresAt.epochSecond}"
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
    }

    fun verify(sessionId: String, seed: String, expiresAt: Instant, token: String): Boolean {
        val expected = sign(sessionId, seed, expiresAt)
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))
    }
}

interface SessionStore {
    fun put(record: SessionRecord)
    fun get(sessionId: String): SessionRecord?
    fun markSubmitted(sessionId: String): Boolean
}

class InMemorySessionStore : SessionStore {
    private val sessions = ConcurrentHashMap<String, SessionRecord>()

    override fun put(record: SessionRecord) {
        sessions[record.sessionId] = record
    }

    override fun get(sessionId: String): SessionRecord? = sessions[sessionId]

    override fun markSubmitted(sessionId: String): Boolean {
        while (true) {
            val current = sessions[sessionId] ?: return false
            if (current.submitted) return false
            if (sessions.replace(sessionId, current, current.copy(submitted = true))) return true
        }
    }
}

interface LeaderboardStore {
    fun upsert(entry: LeaderboardEntry): Int
    fun top(seasonId: String, mode: GameMode, limit: Int): List<LeaderboardEntry>
}

class InMemoryLeaderboardStore : LeaderboardStore {
    private val entries = ConcurrentHashMap<String, ConcurrentHashMap<String, LeaderboardEntry>>()

    override fun upsert(entry: LeaderboardEntry): Int {
        val key = "${entry.seasonId}:${entry.mode.name}"
        val modeEntries = entries.computeIfAbsent(key) { ConcurrentHashMap() }
        modeEntries.compute(entry.playerId) { _, current ->
            when {
                current == null -> entry
                compareEntry(entry, current) < 0 -> entry
                else -> current
            }
        }
        return top(entry.seasonId, entry.mode, Int.MAX_VALUE).indexOfFirst { it.playerId == entry.playerId } + 1
    }

    override fun top(seasonId: String, mode: GameMode, limit: Int): List<LeaderboardEntry> {
        val key = "$seasonId:${mode.name}"
        return entries[key]
            ?.values
            ?.sortedWith(::compareEntry)
            ?.take(limit)
            .orEmpty()
    }

    private fun compareEntry(left: LeaderboardEntry, right: LeaderboardEntry): Int {
        return compareValuesBy(left, right,
            { -it.score },
            { -it.clearedTiles },
            { it.lastActionMs },
            { it.updatedAt },
        )
    }
}

data class SessionRecord(
    val sessionId: String,
    val mode: GameMode,
    val seasonId: String,
    val seed: String,
    val boardVersion: Int,
    val scoreRuleVersion: Int,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val sessionToken: String,
    val submitted: Boolean = false,
)

data class LeaderboardEntry(
    val seasonId: String,
    val mode: GameMode,
    val playerId: String,
    val score: Long,
    val clearedTiles: Int,
    val clearedBoards: Int,
    val lastActionMs: Long,
    val updatedAt: Instant,
)

data class ValidationResult(
    val accepted: Boolean,
    val status: String,
    val officialScore: Long = 0L,
    val officialClearedTiles: Int = 0,
    val officialClearedBoards: Int = 0,
) {
    companion object {
        fun rejected(
            status: String,
            officialScore: Long = 0L,
            officialClearedTiles: Int = 0,
            officialClearedBoards: Int = 0,
        ) = ValidationResult(
            accepted = false,
            status = status,
            officialScore = officialScore,
            officialClearedTiles = officialClearedTiles,
            officialClearedBoards = officialClearedBoards,
        )
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val now: String,
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)

@Serializable
data class CreateGameSessionRequest(
    val mode: String,
)

@Serializable
data class CreateGameSessionResponse(
    val sessionId: String,
    val mode: String,
    val seasonId: String,
    val seed: String,
    val boardVersion: Int,
    val scoreRuleVersion: Int,
    val issuedAt: Long,
    val expiresAt: Long,
    val sessionToken: String,
)

@Serializable
data class SubmitGameResultRequest(
    val sessionId: String,
    val playerId: String = "",
    val mode: String,
    val seasonId: String,
    val seed: String,
    val boardVersion: Int,
    val scoreRuleVersion: Int,
    val finalScore: Long,
    val clearedTiles: Int,
    val clearedBoards: Int,
    val actions: List<GameActionDto>,
    val logHash: String = "",
    val sessionToken: String,
    val playIntegrityToken: String = "",
)

@Serializable
data class GameActionDto(
    val sequence: Int,
    val boardIndex: Int,
    val startRow: Int,
    val startColumn: Int,
    val endRow: Int,
    val endColumn: Int,
    val elapsedMs: Long,
)

@Serializable
data class SubmitGameResultResponse(
    val accepted: Boolean,
    val validationStatus: String,
    val officialScore: Long,
    val rank: Int?,
)

@Serializable
data class LeaderboardResponse(
    val seasonId: String,
    val mode: String,
    val entries: List<LeaderboardEntryDto>,
)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val playerId: String,
    val score: Long,
    val clearedTiles: Int,
    val clearedBoards: Int,
    val lastActionMs: Long,
)
