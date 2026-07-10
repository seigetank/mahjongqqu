# AGENTS.md

## Working Rules

Before changing code or project files:

- State assumptions explicitly when the request has ambiguity.
- If multiple interpretations are plausible, surface them before choosing.
- Prefer the smallest change that satisfies the request.
- Do not add speculative features, abstractions, or configurability.
- Touch only files that directly support the requested work.
- Match existing style once code exists.
- Define success criteria and verify them with tests, scripts, or inspection.
- Do not hide uncertainty. Ask when a risky assumption cannot be resolved locally.

For implementation work:

- Use `rg` or `rg --files` first for repository search.
- Keep game rules separate from Android UI.
- Add or update tests when behavior changes.
- Do not refactor unrelated code while implementing a feature.
- Remove only unused code introduced by the current change.
- Never commit credentials, local Android SDK paths, keystores, auth files, or raw Codex app state.

## Project Context

This repository is for the Android game currently named **마탱이**.

Core product rule:

- The player drags a rectangle over numbered mahjong tiles.
- Selected non-cleared tiles are summed by tile value.
- If the sum is exactly `10`, selected tiles are removed.
- Cleared cells remain empty. No gravity or automatic reflow is applied.
- A tile is selected when its center point is inside the logical selection rectangle.

Primary technical decisions:

- Android native app, not Unity.
- Kotlin, Jetpack Compose, Compose Canvas.
- Pure Kotlin game engine independent of Android APIs.
- Deterministic board generation from seed, board config, weights, and algorithm version.
- Official leaderboard scores are recalculated and validated on the server.
- Rule changes require explicit `boardVersion` or `scoreRuleVersion` changes.
- Ranking gameplay must not be affected by paid items or ads.

Planned modules:

```text
:app
:core:common
:core:designsystem
:core:network
:core:database
:core:analytics
:game:engine
:game:renderer
:game:random
:game:replay
:feature:home
:feature:play
:feature:result
:feature:leaderboard
:feature:profile
:feature:settings
```

## Portability Rules

This project is expected to move across multiple computers.

- Repository-relative paths are preferred in docs, scripts, tests, and project settings.
- Do not write machine-specific user-home absolute paths into committed source files.
- Android SDK, JDK, keystore, and machine-specific values belong in ignored local files or environment variables.
- Use `git rev-parse --show-toplevel`, `$PSScriptRoot`, `$HOME`, or environment variables in scripts.
- Codex handoff archives belong under `codex/conversations/`.
- Do not commit raw files from `$HOME/.codex`, especially `auth.json`, SQLite databases, logs, attachments, or unfiltered session JSONL.

See [docs/codex-portability.md](docs/codex-portability.md) for the Codex conversation transfer workflow.
