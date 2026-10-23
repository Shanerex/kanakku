---
status: final
---
# Review: 2026-10-21-accounts-password

## QA
Re-verified after the 6 security-minor fixes. `./mvnw -B -q test` (JDK 25) exit 0: 99 tests, 0 failures, 0 skipped, incl. ModularityTest. Vitest 84 pass (run directly; not part of `./mvnw test`).
- AC-1 to AC-6, AC-9 to AC-14, AC-16 pass, each with a named passing test.
- AC-7 pass: SignInThrottleTest (6th try 429 + Retry-After 600; other IP or email not limited; sign-in works at 10m01s); SignInThrottleConcurrencyTest caps parallel guesses at 5. Key matches Postgres lower() (U+0130 test); failed_at indexed; prune error cannot lose a count.
- AC-8 pass: SESSION cookie HttpOnly + SameSite=Lax; sign-out revokes server side; sign-in changes session ID and rotates CSRF token.
- AC-15 advisory: e2e/auth-profile.spec.ts not re-run (needs live stack); no CI job guards it. Follow-up.
- Kanakku checks: every endpoint has a web screen; no double/float in app/src/main; no money or AI AC.
- T-1..T-18 all ticked, tests exist and pass.

## Security
Scope: working tree against `main` (feature uncommitted). No blockers.
- resolved (6 of 6 prior minors): short sign-in tx with Argon2 outside it; failed_at index + post-commit prune; SQL lower() key; unknown-email hash cap (503 sign-in-busy); CSRF rotation at sign-in; safeReturnTo control chars.
- checked, no finding: counting the try before hashing fails closed; lockout still per (email, IP).
- minor (new): PasswordSignIn.java:61-66. Unknown-email permit taken in reserve() but try/finally starts at line 66; a commit failure leaks it. 4 leaks make every unknown-email sign-in 503 until restart. Fix: release in a try/finally that starts right after reserve().
- minor (new): PasswordSignIn.java:97-100, IdentityProblemAdvice.java:45-55. 503 only for unknown emails, so a flood tells 503 (no account) from 401 (account); probes are not counted by the throttle. Low impact: sign-up 409 already shows existence (plan.md:76). Fix: count the try before the limiter check; note the oracle at plan.md:76.
- Invariants pass: SQL scoping, secrets masked, idempotency, version check, licences; no new dependencies.

## Verdict
pass: no blocking IDs. Two new minors and the AC-15 CI job are follow-ups.
