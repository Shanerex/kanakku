---
status: final
---
# Tasks: 2026-10-20-maven-scaffold

## Tasks
- [x] T-1 (AC-8): Maven wrapper (3.9.x), `.mvn/maven.config` (`-Drevision=0.1.0-SNAPSHOT`), root `pom.xml` (BOM imports, pluginManagement, flatten), `scripts/check-backend-version.sh` (skips `specs/`); `./mvnw -B -q validate` and the script pass.
- [x] T-2 (AC-3, AC-6): `app` module with `KanakkuApplication` and `ModularityTest`; `./mvnw -B -q test` runs JUnit only, green.
- [x] T-3 (AC-4, AC-5): Actuator probes in `application.yml` (readiness = readinessState + db); `ProbesDbDownTest` (503/200) and `ProbesDbUpTest` (Testcontainers, 200) pass.
- [x] T-4 (AC-9): `kanakku.cors.allowed-origins` wired to `CorsConfig` (`/api/**`) and Actuator CORS (GET); `CorsTest` allowed/refused origin passes.
- [x] T-5 (AC-11): `NoWebAssetsTest` asserts no static/public/META-INF/resources/index.html on the app classpath.
- [x] T-6 (AC-7): Spotless google-java-format for `app` Java; apply once; verify non-zero on a temp misformatted `.java` and no effect from a misformatted file in `web/`.
- [x] T-7 (AC-6, AC-7, AC-9): `web/` npm project (package.json 0.1.0-dev private, lockfile, .nvmrc, vite, tsconfig, biome.json, scripts, `public/config.json`) with `src/api.ts` (`loadConfig`, `getReadiness`) + `api.test.ts`; `npm test` green; `npm run lint` non-zero on a temp misformatted `.ts`.
- [x] T-8 (AC-9, AC-6): Web screen `web/src/App.tsx` shell page: loads `/config.json` then readiness; `App.test.tsx` (fetch stubbed: config error, UP, DOWN, unreachable).
- [x] T-9 (AC-12): `web/scripts/check-version.mjs` + `check-version.test.ts` (temp dirs: valid, bad semver, missing -dev, not private, lockfile mismatch, stray literal in a hand-edited file, literal in excluded files ignored); `npm run check:version` passes.
- [x] T-10 (AC-10): `InfraHealthIndicators` (minio HTTP, smtp TCP, property-gated) with `InfraHealthIndicatorsTest` up/down stubs.
- [x] T-11 (AC-2, AC-8, AC-11, AC-12): `.github/workflows/ci.yml` `backend` and `web` jobs per plan; verify a failing JUnit, spotless, Vitest and Biome change each fail its job.
- [x] T-12 (AC-1, AC-10): `app/Dockerfile`, root `.dockerignore`, `docker-compose.yml` (app, postgres:17, minio, mailpit, healthchecks), `.env.example`; `docker compose up` gives app `/actuator/health` 200 on :8080.
- [x] T-13 (AC-1, AC-9): `web/Dockerfile` (no build-time API URL), `web/nginx/default.conf.template` (SPA fallback, `/config.json` from `KANAKKU_API_BASE_URL`, no-store), `web/.dockerignore`, compose `web` service 3000:8080; web root and `/config.json` serve.
- [x] T-14 (AC-9, AC-10): CI `compose-smoke` job: `up -d --wait --build`; curl app readiness/health 200, web root 200, web `/config.json` has `apiBaseUrl`, Origin header gets `Access-Control-Allow-Origin`.
- [x] T-15 (AC-1): README "Run locally": clean-machine steps, ports, probe URLs, `KANAKKU_API_BASE_URL`, Docker requirement, required branch checks; manual timed check recorded.
