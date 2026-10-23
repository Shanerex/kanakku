---
feature: 2026-10-21-accounts-password
phase: done
status: approved
g1: approved:Shanerex:2026-10-23
g2: approved:Shanerex:2026-10-23
g3: approved:Shanerex:2026-10-23
bump: minor
next: open a PR; a human merges
---
# State: 2026-10-21-accounts-password

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-10-22 intake: F-02 part 1; why: no identity; who: every user; done-when: sign-up, password sign-in and rate limits pass F-02 acceptance
- 2026-10-23 requirements/plan: g1 and g2 approved by Shanerex; no verification link in v1 (human decision, deviates from PRD F-02); Argon2id kept; T-1..T-18 planned; phase: build
- 2026-10-23 build: dev finished T-1..T-18; ./mvnw test green (86), web 82 Vitest, Playwright 2 passed
- 2026-10-23 build: rx-dev done, T-1..T-18 ticked, tests green; phase: verify
- 2026-10-23 verify: QA pass, security pass with 2 majors; review.md final; awaiting g3 and bump
- 2026-10-23 verify: human chose fix majors first; bump: minor; g3 not written
- 2026-10-23 build: rx-dev fixed security majors (sign-in throttle race via per-pair advisory lock; trusted-proxy config KANAKKU_TRUSTED_PROXIES); tests green, spotless clean; awaiting re-verify
- 2026-10-23 verify: re-verify pass (majors fixed, 2 new minors); review.md final; awaiting g3
- 2026-10-23 verify: g3 declined; human chose fix minors first; bump: minor kept; rx-dev fix loop (5 security minors)
- 2026-10-23 build: rx-dev fixed security minors (short sign-in tx, failed_at index and post-commit prune, Postgres-side key, unknown-email hash cap with 503, CSRF rotation at sign-in, safeReturnTo control chars); tests green, spotless clean; awaiting re-verify
- 2026-10-23 verify: re-verify 2 pass (6 minors fixed, 2 new minors); review.md final; awaiting g3
- 2026-10-23 verify: g3 approved by Shanerex; bump: minor; phase: close
- 2026-10-23 close: knowledge written; rx-check done green except CLAUDE.md shared-file (from commit 96e7052, not this feature); blocked
- 2026-10-23 close: human approved CLAUDE.md shared-file change (from 96e7052, on origin/develop, not main); rx-check done otherwise green; phase: done
