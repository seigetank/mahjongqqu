package com.mahjongqqu.server

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionSignerTest {
    @Test
    fun tokenVerifiesWithSamePayload() {
        val signer = SessionSigner("secret")
        val expiresAt = Instant.ofEpochSecond(1000L)
        val token = signer.sign("session", "seed", expiresAt)

        assertTrue(signer.verify("session", "seed", expiresAt, token))
    }

    @Test
    fun tokenRejectsChangedPayload() {
        val signer = SessionSigner("secret")
        val expiresAt = Instant.ofEpochSecond(1000L)
        val token = signer.sign("session", "seed", expiresAt)

        assertFalse(signer.verify("session", "different-seed", expiresAt, token))
    }
}
