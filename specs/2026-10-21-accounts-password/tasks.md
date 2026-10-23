---
status: final
---
# Tasks: 2026-10-21-accounts-password

## Tasks
- [x] T-1 (AC-5): Read all of docs/knowledge/decisions/; check the existing migrations (to pick the next `V<n>`) and the Flyway, jOOQ codegen, springdoc and Modulith-test wiring. Add `V<n>__identity_users.sql` (users, password_credentials, lower(email) unique index), regenerate jOOQ, create the `dev.kanakku.identity` skeleton with the `api` named interface; a Testcontainers test confirms a new row defaults to email_verified=false and `ModularityTests` passes.
- [x] T-2 (AC-3, AC-4): Argon2id `PasswordEncoder` bean (OWASP parameters) and `PasswordPolicy` (8 to 128 code points); unit tests for a `$argon2id$` hash that differs from the input and for the 7/8/128/129 boundaries.
- [x] T-3 (AC-1, AC-2, AC-5): `SignUpService` creates an unverified user (default currency INR) and their credentials in one transaction; email is trimmed and unique case-insensitively (unique violation -> `EmailTaken`); Testcontainers tests cover differing letter case and two concurrent sign-ups.
- [x] T-4 (AC-10): Security baseline: `SecurityFilterChain`, `GET /api/v1/auth/csrf`, `CookieCsrfTokenRepository` with the SPA handler, 401/403 as RFC 9457 problems through platform's `problem` interface, CORS with credentials; MockMvc tests show a POST is refused without the token and accepted with it.
- [x] T-5 (AC-14): `dev.kanakku.platform` module: `V<n+3>__platform_idempotency_keys.sql`, jOOQ regen, `IdempotencyService` (scope, key, request hash without the password, 24 h TTL, 422 on a mismatch) in the `idempotency` named interface; tests for replay, mismatch and a concurrent repeat.
- [x] T-6 (AC-1, AC-2, AC-4, AC-14): `POST /api/v1/auth/sign-up` with Bean Validation, a required Idempotency-Key, 201/400/409/422 problem details, and no session started; MockMvc + Testcontainers tests.
- [x] T-7 (AC-1, AC-6, AC-8): add spring-session-jdbc, `V<n+2>__spring_session.sql` and Spring Session JDBC config (HttpOnly, SameSite=Lax cookie); `POST /api/v1/auth/sign-in` (session ID change, generic 401, dummy hash for an unknown email) and `POST /api/v1/auth/sign-out`; tests compare the two failure responses byte for byte and show a revoked cookie is refused.
- [x] T-8 (AC-9): 14-day `maxInactiveInterval`; a test ages the session row's `last_access_time` to 14 days + 1 min (rejected) and 13 days (accepted).
- [x] T-9 (AC-7): `V<n+1>__identity_sign_in_failures.sql`, jOOQ regen, `SignInThrottle` with an injected `Clock`, 429 problem with `Retry-After`; tests: the 6th try with the correct password gets 429, another IP is not limited, and sign-in succeeds after the window.
- [x] T-10 (AC-11, AC-12, AC-13): `GET`/`PUT /api/v1/me`, scoped to `id = :callerId` in jOOQ, with a version check (409), ISO 4217 and UPI validation; tests show user A cannot touch B, a stale version gets 409, and the optional UPI ID can be empty.
- [x] T-11 (AC-16): mask the password in DTO `toString` and check the log config; an OutputCapture test runs sign-up, sign-in and sign-out at DEBUG and asserts no password or SESSION value appears.
- [x] T-12 (AC-15): add springdoc-openapi plus `openapi/kanakku-v1.json` and `OpenApiSnapshotTest`; generate the web client in `web/src/api/` (openapi-typescript, openapi-fetch) with CSRF middleware, credentials and an Idempotency-Key helper; Vitest for the middleware.
- [x] T-13 (AC-15): web shell in `web/src/app/`: Tailwind, Radix and react-router if missing, tokens.css import, Tailwind theme, self-hosted fonts, router, AuthLayout, InlineProblem and a helper that turns problems into plain words; Vitest.
- [x] T-14 (AC-1, AC-2, AC-4, AC-14, AC-15): Sign-up screen `/sign-up` per auth.md (as amended); one Idempotency-Key per submit attempt; after a 201 it calls sign-in and goes to /welcome; Vitest.
- [x] T-15 (AC-6, AC-7, AC-8, AC-15): Sign-in screen `/sign-in` per auth.md: generic error, 429 countdown from `retryAfterSeconds`, `returnTo`; Vitest.
- [x] T-16 (AC-11, AC-12, AC-13, AC-15): Profile screens `/welcome` (INR preselected, editable), `/me` and `/me/profile` per me.md, with version-conflict wording and Sign out; Vitest.
- [x] T-17 (AC-15): Playwright `e2e/auth-profile.spec.ts`: sign up, sign out, sign in, edit the profile, reload and see the values.
- [x] T-18 (AC-4, AC-5, AC-15): Edit docs/design/screens/auth.md and me.md for the v1 deviations listed in plan.md Design > Web (human-approved Q2).
