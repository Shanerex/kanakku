---
status: final
---
# Review: 2026-10-21-accounts-password

## QA
Re-verified after fix loop 1 (T-29, T-30). `./mvnw -B -q test` (JDK 25) exit 0: 150 fresh tests, 0 failures, incl. ModularityTest. Vitest 85 pass (run directly).
- AC-1 to AC-14 and AC-16 pass, each with a named passing test (full round T-19..T-28 earlier: 208 tests).
- AC-6: wrong vs unknown email give identical 401; over the IP cap both get an identical 429 `busy` (IpHashCapTest). No existence oracle.
- AC-7: 6th try 429, other IP free, window clears; over-cap tries write no failure row, so the 5-in-10-min count is unchanged.
- Dev deviation (known email from a capped IP also gets 429) is consistent with AC-6 and AC-7; a member on a shared IP with 3 hashes in flight sees a transient 429 `busy`, distinct from the throttle 429.
- T-29: flood of 10 from one IP gives at most 2 x 401 and at least 8 immediate 429s. T-30: ComposeSecureCookiesTest; only docker-compose.dev.yml opts out.
- AC-15 advisory: e2e/auth-profile.spec.ts not run (needs `make up-dev`); no CI job guards it.
- T-28 minor: CleanupJobsTest checks only that the jobs are scheduled, not the job body.
- Accepted deviations: V6__platform_scheduled_tasks.sql (a plan.md note would help); sign-up 429 `busy`; test-only db-scheduler.enabled=false and max_connections=500.
- Kanakku checks: every endpoint has a web screen; no double/float in app/src/main; no money or AI AC.

## Security
Scope: working tree against `main`, T-29 and T-30 plus the earlier round. The prior major (5 s slot wait parks Tomcat threads) is fixed: IpHashGuard caps in-flight hashes at 3 per address (IPv6 by /64), refuses at once with 429, waits are 300 ms, released on every path. The compose Secure-cookie minor is fixed. X-Forwarded-For is trusted only from KANAKKU_TRUSTED_PROXIES, so it cannot bypass the cap or frame another IP.
- minor: IpHashGuard.java:24, docker-compose.yml:61. Behind a proxy with KANAKKU_TRUSTED_PROXIES unset, every client shares the proxy IP and the cap covers the whole server. Fix: warn in compose comment and README, or log a warning on X-Forwarded-For from an untrusted peer.
- minor: IpHashGuard.java:47, ThrottleIp.java:31-33. A /56 or /48 holder gets many caps; holding all ~200 threads now takes about 670 req/s, not 40. Rest belongs to an edge rate limiter.
- minor: IpHashGuard.java:53. A user on a shared CGNAT or office IP can briefly deny sign-in to others there; 429 with Retry-After 1, no counted failure, no lasting lockout. Accepted.
- minor (carried): PasswordSignIn.java:62-68 timing difference under full slots; accepted at plan.md:77.
- Invariants pass: no new SQL, secrets, AI or MCP paths, no new dependencies in T-29/T-30; Idempotency-Key still required.

## Verdict
pass: no blocking IDs, no majors. 4 minors plus AC-15 run and the T-28 job-body test are follow-ups.
