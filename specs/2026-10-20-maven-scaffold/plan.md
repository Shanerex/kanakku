---
status: final
---
# Plan: 2026-10-20-maven-scaffold

## Approach
- Greenfield: nothing exists under the source_globs. Two independent builds: a Maven reactor (root pom + `app`) and a plain npm project in `web/` (R-1, R-3).
- `app` is API only: no static resources, CORS from one property (R-2, AC-9, AC-11). Modulith verification on the bare app (AC-3); Actuator probes (AC-4, AC-5).
- Lint: Spotless for Java only (AC-7); Biome via npm for web. CI jobs: `backend`, `web`, `compose-smoke` (AC-2, AC-10).
- Versions: backend `${revision}` from `.mvn/maven.config` (AC-8); web version only in `web/package.json` (AC-12).
- Compose: separate `web` (:3000) and `app` (:8080) images, plus Postgres, MinIO and Mailpit (AC-1, AC-10). No domain code, migrations, `/api/v1` or AI.
- Human decisions (2026-10-20): option A (nginx-unprivileged); runtime `config.json`; AC-9 by curl Origin check + Vitest; web port 3000; google-java-format; Biome.

## Options considered
How the `web` container serves the built SPA (R-9):
- A (chosen): `nginxinc/nginx-unprivileged` (BSD-2) serves `dist/`, with SPA fallback. Small, production-grade image; needs an nginx config.
- B: `vite preview` in a Node image. No extra config, but Vite says it is not for production, and the image is heavier.
- C: Caddy (Apache-2.0) serving files. Simple config; less familiar to contributors, and it gives no gain over A here.

## Design
Backend build (R-1, R-5, AC-8)
- `pom.xml` (`kanakku-parent`, packaging pom, `<version>${revision}</version>`, module `app` only). The parent imports `spring-boot-dependencies` and `spring-modulith-bom`.
- pluginManagement pins spring-boot-maven-plugin, spotless-maven-plugin and flatten-maven-plugin (resolves `${revision}`).
- `.mvn/maven.config`: `-Drevision=0.1.0-SNAPSHOT`. `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` pin Maven 3.9.x. A release passes `-Drevision=0.1.0` (not automated).
- `scripts/check-backend-version.sh`: maven.config holds a `-SNAPSHOT` value; every pom references `${revision}`.
  - It also checks that `git grep -F <value>` matches only `.mvn/maven.config`, excluding `specs/` (requirements.md quotes the value).

app module (R-2, R-4, R-10, AC-11)
- `app/pom.xml`: starters web, actuator, jdbc; postgresql driver; spring-modulith-starter-core. There is no `src/main/resources/static|public`.
- Tests: spring-boot-starter-test, spring-modulith-starter-test, spring-boot-testcontainers, Testcontainers Postgres.
- Files in `app/src/main/java/dev/kanakku/` (root package only, so there are no Modulith modules yet):
  - `KanakkuApplication.java`.
  - `CorsConfig.java`: binds `kanakku.cors.allowed-origins` (env `KANAKKU_CORS_ALLOWED_ORIGINS`, list, no `*`) to MVC CORS for `/api/**`.
  - `InfraHealthIndicators.java`: `minio` (HTTP GET `/minio/health/live`) and `smtp` (TCP connect). Each is active only when `kanakku.minio.endpoint` / `kanakku.smtp.host` is set (AC-10).
- `app/src/main/resources/application.yml`:
  - Exposes only `health`, with `show-details: never` and probes enabled. Liveness group `livenessState`; readiness group `readinessState,db`.
  - `management.endpoints.web.cors.allowed-origins` is set from the same property, GET only (AC-9).
  - Datasource comes from env. Hikari stays lazy, so the app starts with Postgres down (AC-5).
- Probes: `/actuator/health/liveness`, `/actuator/health/readiness` on port 8080 (not under `/api/v1`).
- Tests in `app/src/test/java/dev/kanakku/`:
  - `ModularityTest`: `ApplicationModules.of(KanakkuApplication.class).verify()` (AC-3).
  - `ProbesDbDownTest`: JDBC URL at a closed port, 1s Hikari timeout. Readiness returns 503 (AC-4) and liveness 200 (AC-5).
  - `ProbesDbUpTest`: Testcontainers postgres:17 via `@ServiceConnection`; readiness returns 200 (AC-4).
  - `CorsTest`: an allowed Origin on health gets `Access-Control-Allow-Origin`; another origin is refused (AC-9).
  - `NoWebAssetsTest`: no `static/`, `public/`, `META-INF/resources/` or `index.html` on the app classpath (AC-11).
  - `InfraHealthIndicatorsTest`: a JDK HttpServer and a ServerSocket stub, each tested up and down (AC-10).
- Spotless (R-7, AC-7): google-java-format on `app/src/**/*.java` only. `web/` is not a Maven module, so it is never scanned.

web project (R-3, R-6, R-11)
- `web/package.json`: `"version": "0.1.0-dev"`, `"private": true`.
  - Scripts: `dev`, `build`, `test` (`vitest run`), `lint` (`biome ci .`), `format`, `check:version`.
- `web/package-lock.json`, `.nvmrc` (24), `vite.config.ts` (dev port 5173), `tsconfig.json`, `biome.json`, `index.html`.
- Dev dependencies, pinned: `@biomejs/biome` (MIT/Apache), vitest, jsdom, @testing-library/react (MIT).
- `web/public/config.json`: `{"apiBaseUrl":"http://localhost:8080"}` for `npm run dev`. In the image, nginx serves `/config.json` instead (Runtime).
- `web/src/api.ts`: `loadConfig()` fetches `/config.json` (`no-store`, validates `apiBaseUrl` as a URL); `getReadiness(config)` calls `<apiBaseUrl>/actuator/health/readiness`. Tested by `api.test.ts`.
- Web screen (own task): `web/src/main.tsx`, `web/src/App.tsx`, `web/src/App.test.tsx`. App.tsx is the shell page.
  - On mount it loads config, then readiness, and shows loading, config error, or API status (UP / DOWN / unreachable).
- `web/scripts/check-version.mjs` (no dependencies), exercised by `web/scripts/check-version.test.ts` with temp dirs (AC-12). Checks:
  - Valid semver (semver.org regex), `private === true`, ends in `-dev` unless `RELEASE=1`. Lockfile `version` and `packages[""].version` equal it.
  - The literal appears in no hand-edited `web/` file. Excluded: `package.json`, `package-lock.json`, `node_modules/`, `dist/`, `coverage/`.

CI (R-8, AC-2, AC-8, AC-11, AC-12)
- `.github/workflows/ci.yml` runs on pull_request and on push to main.
- `backend` job (Temurin 25, Maven cache):
  - `./mvnw -B -q spotless:check`, `./mvnw -B -q test`, then `scripts/check-backend-version.sh`.
  - `./mvnw -B -q -DskipTests package`, then `jar tf` asserts no `static/` or `index.html` (AC-11).
- `web` job (Node from `.nvmrc`, npm cache, working-directory `web`): `npm ci`, `npm run check:version`, `npm run lint`, `npm test`, `npm run build`.
- `compose-smoke`: `docker compose up -d --wait --build`, then curl (AC-9, AC-10). It expects:
  - App readiness and health 200; web `/` 200 with `<div id="root">`; `/config.json` has `apiBaseUrl`.
  - Health with `Origin: http://localhost:3000` returns `Access-Control-Allow-Origin`.
- Required checks (`backend`, `web`) are a branch-protection setting the human applies; the README documents it.

Runtime (R-9, AC-1, AC-10)
- `app/Dockerfile` (context `.`):
  - Build: `eclipse-temurin:25-jdk`, `./mvnw -B -DskipTests -pl app -am package`, BuildKit `~/.m2` cache. Runtime: `eclipse-temurin:25-jre`, non-root.
- Root `.dockerignore` excludes `web/`, `specs/`, `**/target`.
- `web/Dockerfile`: `node:24-alpine` build (`npm ci && npm run build`, no API URL at build time); runtime nginx-unprivileged.
  - `web/nginx/default.conf.template` (envsubst at startup): SPA fallback (`try_files` to `/index.html`).
  - It serves `location = /config.json` as `{"apiBaseUrl":"${KANAKKU_API_BASE_URL}"}`, no-store. `web/.dockerignore` excludes `node_modules`, `dist`.
- `docker-compose.yml`: `web` (3000:8080, env `KANAKKU_API_BASE_URL=http://localhost:8080`), `app` (8080), `postgres:17`, `minio/minio`, `axllent/mailpit`.
  - Healthchecks on every service; the app has `depends_on: service_healthy`. Credentials are `${VAR:-dev default}`; `.env.example` lists them.
  - App env: datasource, `KANAKKU_MINIO_ENDPOINT`, `KANAKKU_SMTP_HOST`, `KANAKKU_CORS_ALLOWED_ORIGINS=http://localhost:3000`.
- README "Run locally": clean-machine steps, the two ports, the probe URLs, and how to change `KANAKKU_API_BASE_URL` (AC-1).

Kanakku checklist (all n/a for a scaffold)
- Modulith modules: none created (AC-3). No other module API is called. Flyway: none. REST under /api/v1: none. OpenAPI: no change.
- Idempotency-Key / version checks: n/a. Group-membership scoping in SQL: n/a (no domain reads; health shows no details). Money / AI: n/a. Web screen: `web/src/App.tsx` (T-8).

## Architecture changes
Proposals only; the human has not approved them yet:
- "Build: backend and web are separate; root pom imports Boot + Modulith BOMs; backend version only in .mvn/maven.config, web version only in web/package.json."
- "Ops: built-in Actuator health probes; liveness = process, readiness = process + Postgres; custom health indicators only for MinIO and SMTP."
- Note: the architecture.md Modules line "web (Maven module) ... built into the app's static resources" goes stale, as do CLAUDE.md's Stack, Test and Lint lines. The orchestrator rewords them if approved; the architect does not edit them.

## Risks
- AC-1's 10-minute bound on a cold build (Maven deps, npm ci, two images). Mitigation: BuildKit caches, tight `.dockerignore`s, `-DskipTests`.
- A wrong or missing `KANAKKU_API_BASE_URL` breaks AC-9. Mitigation: App.tsx shows a config error, api.ts validates the URL, and compose-smoke checks `/config.json`.
- CORS misconfiguration (`*` or a missing origin) breaks AC-9 or widens access. Mitigation: no wildcard, CorsTest, compose-smoke Origin check.
- ProbesDbUpTest needs Docker locally; Boot 4 moved health and test-client packages. Mitigation: README notes Docker; use Boot 4 docs.
- AC-8's grep would match `specs/`, and AC-12's would match the lockfile and generated dirs. Mitigation: the scoped exclusions above, stated in the scripts.
