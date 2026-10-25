---
feature: 2026-10-24-groups-members
phase: done
status: approved
g1: approved:Shanerex:2026-10-24
g2: approved:Shanerex:2026-10-24
g3: approved:Shanerex:2026-10-25
bump: minor
next: open a PR; a human merges
---
# State: 2026-10-24-groups-members

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-10-24 intake..requirements: F-03 intake logged; requirements R-1..R-20, AC-1..AC-21 after answers (single-use invites, preset covers, archived read-only except settlements, leave at zero balance) and g1 feedback (Owner direct-add, per-user invite-only); g1 approved
- 2026-10-24 plan: architect pass 1 drafted plan.md and tasks.md (22 tasks, V7-V9), 5 questions
- 2026-10-24 plan: answers: minimal ledger module now; write-off split equally over Owner-picked absorbers; expense ACs proven at access layer; ledger takes pre-checked group id; invite preview by token hash
- 2026-10-24 plan: architect pass 2 final (22 tasks, V7-V9, 4 architecture changes); awaiting g2
- 2026-10-24 plan: g2 approved by Shanerex; phase build
- 2026-10-25 build: T-18..T-22 done; web vitest and lint green; Java ITs and e2e not run (no Docker)
- 2026-10-25 build: mvnw test green (Docker up); e2e fixed exact label match for Invite link, 3/3 pass; phase verify
- 2026-10-25 verify: qa+security ran; review.md fail (T-7 no web screen for PATCH group); fix loop 1 of 2
- 2026-10-25 verify: fix loop 1 done (edit-group dialog on GroupPage, 6 Vitest, e2e rename); vitest 127 + e2e 3/3 green; re-verifying
- 2026-10-25 verify: re-verify pass (349 java, 127 vitest, e2e 3/3); review.md pass; awaiting g3 and bump
- 2026-10-25 verify: human chose approve-after-fix for g3 (not yet written), bump minor; rx-dev fixing invite token at rest, WriteOffRequest @Size, absorber for-update lock
- 2026-10-25 verify: security fix done (HMAC-derived invite token, @Size 50, absorber for update); mvnw green, e2e 3/3; re-verifying
- 2026-10-25 verify: re-verify pass (355 java, 127 vitest, e2e 3/3); 4 minor security open; g3 approved by Shanerex (fix-first condition met), bump minor; phase close
- 2026-10-25 close: knowledge written (3 decisions, 2 learnings); rx-check done; phase done
