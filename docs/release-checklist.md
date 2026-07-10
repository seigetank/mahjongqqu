# Release Checklist

This repository now contains a playable offline Android app and a local verification server. The following external setup is still required before a real public launch.

## Google Play Console

- Create the Android application record.
- Configure package name `com.mahjongqqu.app`.
- Create app signing key setup.
- Configure Play Games Services v2.
- Configure Play Integrity API.
- Prepare store listing, screenshots, content rating, Data safety, privacy policy, and account deletion URL.

## Firebase

- Create Firebase project.
- Add Android app.
- Add Crashlytics.
- Add Performance Monitoring or replace with first-party performance logs.
- Add Remote Config keys for board size, timer, combo window, score multipliers, ad frequency, minimum app version, and maintenance mode.
- Add `google-services.json` locally only after Firebase plugin wiring is introduced. Do not commit secrets.

## Backend

- Replace in-memory session and leaderboard stores with PostgreSQL and Redis adapters.
- Store `SESSION_SECRET` in Secret Manager.
- Validate Play Games server auth codes.
- Validate Play Integrity verdicts and bind tokens to submitted payload hashes.
- Add admin-only season rollover endpoint used by Cloud Scheduler.
- Add moderation flow for suspicious scores.
- Add account deletion/anonymization endpoint.

## Infrastructure

- Build and push the server container image.
- Apply Terraform with a real Google Cloud project.
- Configure Cloud SQL PostgreSQL or managed PostgreSQL.
- Configure Memorystore Redis or compatible Redis.
- Configure Cloud Scheduler for Monday 00:00 Asia/Seoul.
- Configure Cloud Logging, Error Reporting, alerting, and dashboards.

## Android Release

- Wire app networking to request official sessions before ranked games.
- Submit action logs to `/v1/game-results`.
- Cache failed submissions with idempotency keys.
- Disable official ranked submission when server session or integrity verification is unavailable.
- Add production mahjong tile art, sound effects, music, and final icon assets.
- Run UI tests on phone, tablet, foldable, large text, and 60/120Hz devices.
- Build Android App Bundle.

## Security Review

- Confirm no secrets are committed.
- Run portable path check.
- Run dependency vulnerability scan.
- Verify R8 release build.
- Verify official score comes only from server recalculation.
- Verify paid items do not affect ranked gameplay.
