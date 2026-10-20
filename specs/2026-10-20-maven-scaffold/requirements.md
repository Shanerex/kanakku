---
status: final
---
# Requirements: 2026-10-20-maven-scaffold

## Problem
Nothing runs yet. Every contributor needs a buildable, testable, self-hostable skeleton (PRD F-01) that later features build on.

## Requirements
- R-1: Maven multi-module build with the Maven wrapper; parent pom imports the Spring Boot BOM.
- R-2: `app` Spring Boot module is an API only: no web assets in its jar; it allows the web origin through configurable CORS.
- R-3: `web/` is a plain npm project (React + Vite + TypeScript), not a Maven module, with `npm run dev`, `npm test` (Vitest) and `npm run build`; no frontend-maven-plugin.
- R-4: Domain packages under `dev.kanakku` are Spring Modulith modules, with a Modulith verification test on the bare app.
- R-5: Backend version is `${revision}` in every pom; its value `0.1.0-SNAPSHOT` lives only in `.mvn/maven.config`; a release overrides it with `-Drevision=0.1.0`.
- R-6: `./mvnw -B -q test` runs backend JUnit only; Vitest runs via `npm test`.
- R-7: `./mvnw -B -q spotless:check` checks Java only; web formatting and lint run via npm (Biome).
- R-8: GitHub Actions CI has a backend job (tests, spotless) and a separate web job (Vitest, Biome), both required on every PR.
- R-9: `docker-compose.yml` runs web, app, Postgres, MinIO and Mailpit; web and app are separate services on separate ports.
- R-10: Readiness and liveness endpoints, readiness depending on Postgres.
- R-11: Web version is edited only in `web/package.json` as valid semver (`0.1.0-dev` in development, `0.1.0` at release), with `"private": true`, independent of the backend version; `web/package-lock.json` must carry the same version.

## Acceptance criteria
- AC-1: Manual, documented in README: on a clean machine, `docker compose up` after `git clone` serves the web on its port and the app on its own port within 10 minutes (R-2, R-3, R-9).
- AC-2: A PR with a failing backend test, spotless check, Vitest test or Biome check gets a failing CI check (R-8).
- AC-3: The Modulith verification test passes on the bare empty app; no fixture modules are used (R-4).
- AC-4: Readiness returns HTTP 503 when Postgres is down and 200 when it is up (R-10).
- AC-5: Liveness returns HTTP 200 while the app process runs, even with Postgres down (R-10).
- AC-6: `./mvnw -B -q test` exits 0 and runs at least one JUnit test and no Vitest test; `npm test` in `web/` exits 0 and runs at least one Vitest test (R-6).
- AC-7: `./mvnw -B -q spotless:check` exits non-zero on a misformatted Java file and ignores `web/`; the Biome check in `web/` exits non-zero on a misformatted TypeScript file (R-7).
- AC-8: `grep` finds the backend version value only in `.mvn/maven.config`, where it is `0.1.0-SNAPSHOT` (ends in -SNAPSHOT); poms reference `${revision}` (R-5).
- AC-9: The web root URL serves the React page, and that page can call the app health endpoint cross-origin (R-2, R-3).
- AC-10: Compose starts web, app, Postgres, MinIO and Mailpit, with the app able to reach the other three backing services (R-9).
- AC-11: The app jar contains no web assets (R-2).
- AC-12: A script or test in the web CI job checks that `web/package.json` version is valid semver, ends in `-dev` in development and is `"private": true`; that the `web/package-lock.json` package version equals it; and that the version appears in no other `web/` file (R-11).

## Out of scope
- Serving the web from the app.
- Release automation (version override, bump to next -SNAPSHOT, tagging, publishing).
- Mobile module and its tests.
- Fixture modules for the Modulith cross-module violation check; that check waits for real domain modules.
- Flyway migrations, jOOQ models, db-scheduler, auth, and any domain feature.
- AI providers and MCP server.
- Deployment beyond local compose.

Caveat: PRD F-01 asks for a `web` Maven module, frontend-maven-plugin, one port and Vitest/Java-TS lint in `./mvnw`; this revision overrides those per the human decision.
