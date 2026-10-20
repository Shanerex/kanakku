---
status: final
---
# Review: 2026-10-20-maven-scaffold

## QA
- Rerun under JDK 25: 14 JUnit tests, 0 failed, 0 skipped; `spotless:check` exit 0; `npm test` 20 Vitest tests; lint, `check:version`, `check-backend-version.sh` exit 0.
- AC-3 ModularityTest green. AC-4 ProbesDbDownTest 503 and ProbesDbUpTest 200 green. AC-5 liveness 200 with Postgres down green.
- AC-11 NoWebAssetsTest (classpath) green; the `jar tf` check runs only in CI, not run locally.
- AC-7 misformatted Java: spotless exit 1. Misformatted web .ts: spotless ignores it, Biome exit 1.
- AC-8, AC-12 pass.
- Not run by QA: AC-1 (manual clean-machine), AC-2 (real failing PR), AC-9/AC-10 live compose. Dev agent ran compose locally: all healthy, CORS header and /config.json ok.
- No double/float, no REST endpoint or money AC; Modulith test present.

## Security
- Fixed: pgsty/minio pinned to tag + sha256 digest (verified on Docker Hub), `.env` ignored in .gitignore and .dockerignore, Mailpit bound to 127.0.0.1.
- Exception recorded by human: jakarta.annotation-api (EPL-2.0 OR GPL-2.0+CPE).
- minor (open): floating image tags, actions pinned by tag not SHA, no Maven wrapper checksum, weak dev datasource fallback password, no nginx security headers, dev-only npm licences (MPL-2.0, ISC, BlueOak).
- CORS, health exposure, config.json: ok.

## Verdict
pass. No blocking items; six minors left open.
