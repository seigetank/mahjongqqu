CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    pgs_player_id TEXT UNIQUE,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'active'
);

CREATE TABLE IF NOT EXISTS seasons (
    id UUID PRIMARY KEY,
    season_code TEXT NOT NULL UNIQUE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    fixed_seed TEXT NOT NULL,
    board_version INTEGER NOT NULL,
    score_rule_version INTEGER NOT NULL,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS game_sessions (
    id UUID PRIMARY KEY,
    player_id UUID REFERENCES players(id),
    season_id UUID REFERENCES seasons(id),
    mode TEXT NOT NULL,
    seed TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS game_results (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id),
    player_id UUID REFERENCES players(id),
    season_id UUID REFERENCES seasons(id),
    mode TEXT NOT NULL,
    score BIGINT NOT NULL,
    cleared_tiles INTEGER NOT NULL,
    cleared_boards INTEGER NOT NULL,
    last_action_ms BIGINT NOT NULL,
    action_count INTEGER NOT NULL,
    integrity_level TEXT NOT NULL,
    validation_status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS leaderboard_entries (
    season_id UUID NOT NULL REFERENCES seasons(id),
    mode TEXT NOT NULL,
    player_id UUID NOT NULL REFERENCES players(id),
    best_result_id UUID NOT NULL REFERENCES game_results(id),
    score BIGINT NOT NULL,
    rank_tiebreaker TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (season_id, mode, player_id)
);

CREATE TABLE IF NOT EXISTS player_statistics (
    player_id UUID PRIMARY KEY REFERENCES players(id),
    total_games BIGINT NOT NULL DEFAULT 0,
    total_score BIGINT NOT NULL DEFAULT 0,
    total_cleared_tiles BIGINT NOT NULL DEFAULT 0,
    best_combo INTEGER NOT NULL DEFAULT 0,
    all_time_best_random BIGINT NOT NULL DEFAULT 0,
    all_time_best_weekly BIGINT NOT NULL DEFAULT 0,
    all_time_best_endless BIGINT NOT NULL DEFAULT 0,
    weekly_top_10_count INTEGER NOT NULL DEFAULT 0,
    weekly_first_place_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_game_results_season_mode_score
    ON game_results(season_id, mode, score DESC);

CREATE INDEX IF NOT EXISTS idx_leaderboard_entries_season_mode_score
    ON leaderboard_entries(season_id, mode, score DESC);
