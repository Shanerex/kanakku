---
status: final
---
# Plan: 2026-10-21-accounts-password

## Approach
- New Modulith module `identity` owns users, password credentials, sign-in throttling and sessions (R-1 to R-15).
- Spring Security plus Spring Session JDBC: sessions live in Postgres (no Redis), revoked server-side (R-8, AC-8, AC-9); SPA CSRF uses a cookie token sent back in a header (R-9, AC-10).
- Argon2id via `Argon2PasswordEncoder` (human decision). The hash sits in its own table, so profile queries never select it (R-2, R-12, R-15).
- Throttle and idempotency are Postgres tables. Time comes from an injected `Clock`, so AC-7 and AC-9 run without sleeps.
- springdoc generates the OpenAPI document; a committed snapshot drives the generated web client; web ships five screens (R-14, AC-15).
- The no-verification deviation (requirements.md) also changes wording in auth.md and me.md; see Design > Web.

## Options considered
- A (chosen): Spring Security + Spring Session JDBC. Standard code, server-side revoke, find-by-principal ready for a later sessions list; costs two Spring Session tables in our Flyway.
- B: our own opaque-token session table and filter in identity. Full control, but we would hand-write security-critical code.
- C: stateless JWT cookie. No server-side revoke, so it fails R-8. Rejected.

## Design
Modules
- Owner: `dev.kanakku.identity` (new). `identity.controller` holds the controllers, `identity.service` the services and `identity.repository` the jOOQ repositories. `identity.api` is a named interface `api` that exposes `AuthenticatedUser(UUID id)` and `CurrentUser` for later modules.
- `dev.kanakku.platform` (new; human-approved Q1): named interface `idempotency` (`IdempotencyService`) and `problem` (RFC 9457 mapping, shared `Clock` bean). Every later create reuses it.
- identity calls only platform's `idempotency` and `problem` named interfaces. `ModularityTests` (`ApplicationModules.verify()`) covers both modules.

Migrations (forward-only new files; n = highest existing version + 1, check the folder and `main`)
- `V<n>__identity_users.sql`: `users` (id uuid pk, email text, email_verified bool default false, display_name text, default_currency char(3) default 'INR', upi_id text null, version int default 0, created_at, updated_at), unique index on `lower(email)`; `password_credentials` (user_id pk fk users, password_hash text, updated_at).
- `V<n+1>__identity_sign_in_failures.sql`: `sign_in_failures` (id bigserial, email_key text, ip inet, failed_at timestamptz), index (email_key, ip, failed_at).
- `V<n+2>__spring_session.sql`: the Spring Session JDBC Postgres schema, copied verbatim; `spring.session.jdbc.initialize-schema=never`.
- `V<n+3>__platform_idempotency_keys.sql`: `idempotency_keys` (scope text, key text, request_hash bytea, status int, response jsonb, created_at, pk (scope, key)).
- jOOQ classes are regenerated in the same task as each migration.

REST (under /api/v1, Bean Validation, RFC 9457 application/problem+json)
- `GET /auth/csrf` -> 200 {headerName, token}; also sets the `XSRF-TOKEN` cookie.
- `POST /auth/sign-up` {email, password, displayName}, `Idempotency-Key` required -> 201 Profile, with no session (Q5); 400 for validation or a missing key, 409 when the email is taken, 422 when a key is reused with a different body.
- `POST /auth/sign-in` {email, password} -> 200 Profile plus the `SESSION` cookie; 401 one generic problem (AC-6); 429 with `Retry-After` and `retryAfterSeconds`.
- `POST /auth/sign-out` -> 204; the session row is deleted.
- `GET /me` -> Profile {id, email, emailVerified, displayName, defaultCurrency, upiId, version}.
- `PUT /me` {displayName, defaultCurrency, upiId?, version} -> 200 Profile; 409 on a stale version.
- OpenAPI: add springdoc-openapi (Apache-2.0). The snapshot `openapi/kanakku-v1.json` is checked by `OpenApiSnapshotTest`; web (and later mobile) clients are generated from it.

Idempotency and versions
- Sign-up: one row per (scope "sign-up", key), 24 h TTL. request_hash = SHA-256 of the normalised email plus displayName, never the password. The same key with the same hash replays the stored 201. Key insert and account insert share one transaction; concurrent repeats serialise on the pk (AC-14).
- `PUT /me`: `update users set ..., version = version + 1 where id = :callerId and version = :v`; 0 rows -> 409 (AC-13).

Scoping
- No group data yet. Every profile read and write is `where users.id = :callerId`, with callerId taken only from the session principal. No endpoint takes a user id, and bodies carry none (AC-13).
- Sign-in looks up `lower(email)` before auth and returns only the generic result. Only `PasswordSignIn` reads `password_credentials`.

Auth details
- Argon2id: `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` (OWASP minimum), with Bouncy Castle `bcprov-jdk18on`. Length is checked in code points, 8 to 128 (AC-4).
- Unknown email: run `matches` against a fixed dummy hash so the timing equals a wrong password (AC-6).
- Throttle: before checking the password, count `sign_in_failures` for (lower(email), ip) with failed_at > now - 10 min. At 5 or more, return 429 and record nothing. Each failure inserts a row; a success does not reset the count. Rows older than 1 h are purged on insert (AC-7).
- IP is `request.getRemoteAddr()`; forwarded headers count only behind a configured trusted proxy.
- Session: `maxInactiveInterval` of 14 days. `DefaultCookieSerializer` sets HttpOnly, SameSite=Lax, and Secure on HTTPS. The session ID changes on sign-in (fixation), and the context is saved through `HttpSessionSecurityContextRepository`. Dependency: spring-session-jdbc (Apache-2.0).
- CSRF: `CookieCsrfTokenRepository` with the SPA request handler on every unsafe method, sign-up and sign-in included; a failure is a 403 problem. CORS reuses `kanakku.cors.allowed-origins`, with credentials allowed.
- Logs: request DTOs override `toString` to mask the password; request bodies are never logged; the AC-16 test captures DEBUG output.
- Currency is checked against `java.util.Currency` codes and defaults to INR, editable at /welcome and /me/profile (Q4, AC-11). UPI must match `^[\w.\-]{2,256}@[a-zA-Z]{2,64}$` (from auth.md); blank becomes null (AC-12).
- No money and no AI here, so there is no rounding, jqwik test or fake provider.

Web (web/src; only tokens.css variables, Tailwind + Radix, Phosphor icons)
- Dependencies, added if missing (Q3; all MIT): openapi-typescript, openapi-fetch, Tailwind CSS, Radix, react-router.
- `web/src/api/`: generated `schema.d.ts` and `client.ts` with credentials, CSRF middleware and an `Idempotency-Key` helper.
- `web/src/app/`: router, AuthLayout, self-hosted fonts, a problem-to-plain-words helper and InlineProblem.
- `features/auth/SignUpPage.tsx` (/sign-up) and `SignInPage.tsx` (/sign-in); `features/me/WelcomePage.tsx` (/welcome), `MePage.tsx` (/me) and `ProfilePage.tsx` (/me/profile). After a 201, sign-up calls sign-in and then goes to /welcome (Q5). Each gets a Vitest test; Playwright `e2e/auth-profile.spec.ts`.
- Design spec edits, approved for T-18 (Q2; docs/design/README.md step 4):
  - auth.md: the sign-up rule reads "8 to 128 characters", and success goes to /welcome.
  - auth.md: "Forgot password?" and the Unverified state are hidden; provider buttons and the divider are hidden while no provider is enabled.
  - me.md: email is read-only; Test link and Locale are left out; only the Profile row and Sign out are shown.

## Architecture changes
- Data: add "Web sessions live in Postgres via Spring Session JDBC; the sign-in throttle and idempotency keys are Postgres tables."
- Modules: add "platform: Idempotency-Key store and RFC 9457 problem mapping (plus the shared Clock), reused by every create" (Q1 = A).
- Conventions: add "The OpenAPI snapshot openapi/kanakku-v1.json is the client source; a test fails on drift."

## Risks
- Sign-up's 409 reveals that an account exists (AC-2 requires the refusal); sign-in stays generic and uses a dummy hash.
- Argon2 cost can tie up request threads under attack; the throttle runs before hashing, and the parameters are config.
- X-Forwarded-For can be spoofed; forwarded headers are honoured only from a trusted proxy, documented in compose.
- An attacker could lock one victim's email, but only from the attacker's own IP, because the throttle keys on the pair (R-7).
- AC-9 tests set `last_access_time` on the session row directly; Spring Session's cleanup cron removes expired rows.
- Dev server on 5173 calling 8080 sends cookies cross-origin; CORS with credentials, same-site Lax, and e2e runs against the built app.
- The architect could not list directories (rg missing); T-1 checks the migrations folder and existing wiring, and has the dev read all of docs/knowledge/decisions/.
