---
feature: 2026-10-21-accounts-password
phase: done
status: approved
g1: approved:Shanerex:2026-10-23
g2: approved:Shanerex:2026-10-23
g3: approved:Shanerex:2026-10-24
bump: none
next: open a PR; a human merges
---
# State: 2026-10-21-accounts-password

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-10-22..23 intake to done: F-02 part 1 planned (T-1..T-18), built, two security fix loops (2 majors, 6 minors), g1 to g3 approved, bump minor, closed with phase: done; 2 minors left as follow-ups (PasswordSignIn permit leak, 503 oracle)
- 2026-10-24 build: /code-review high found 10 issues; human chose to reopen this spec; g3 reset to none (was approved:Shanerex:2026-10-23); T-19..T-28 added; decisions: force Secure cookies with opt-out, unknown-email sign-in waits for a slot (503 dropped); phase: build
- 2026-10-24 build: rx-dev finished T-19..T-28 (28/28); mvnw test 134 green, spotless clean, Vitest 84; Playwright not run; deviations: V6 scheduled_tasks migration, sign-up hash wait times out as 429 busy; phase: verify
- 2026-10-24 verify: QA pass; security pass with 1 major (hash-slot wait exhausts Tomcat threads) and 2 minors; review.md final; awaiting human
- 2026-10-24 verify: human chose fix major + compose default first; g3 not written; T-29..T-30 added; phase: build
- 2026-10-24 build: rx-dev finished T-29..T-30 (30/30); per-IP hash cap (IpHashGuard, 429 busy), wait 300 ms, compose Secure default + docker-compose.dev.yml; mvnw test green, spotless clean, Vitest 85; Playwright not run; phase: verify
- 2026-10-24 verify: re-verify pass (security major and compose minor fixed, 3 new minors); review.md final; awaiting g3 and bump
- 2026-10-24 verify: g3 approved by Shanerex; bump: none; phase: close
- 2026-10-24 close: knowledge written (decision auth-hash-guards, learning wait-for-slot); rx-check done red only on CLAUDE.md shared-file (96e7052 approved earlier; a39066d new); blocked
- 2026-10-24 close: human approved CLAUDE.md shared-file change (96e7052 and a39066d, not this fix round); rx-check done otherwise green; phase: done
