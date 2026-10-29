---
feature: 2026-10-28-ledger-core
phase: close
status: blocked
g1: approved:Shanerex:2026-10-28
g2: approved:Shanerex:2026-10-28
g3: approved:Shanerex:2026-10-28
bump: minor
next: only open check is architecture.md shared-file (human accepted); open the PR, human merges
---
# State: 2026-10-28-ledger-core

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-10-28 intake..plan: intake answered (prd F-05); requirements and plan drafted and approved; human added nightly repair (default on) so g1 and g2 were reset and re-approved by Shanerex
- 2026-10-28 build: rx-dev done, T-1..T-16 ticked; test_cmd green, lint green; -Pci ran the 10k property (12 min) green
- 2026-10-28 verify: qa pass, security pass (1 major, 2 minor); human chose fix first; bump minor (human: decide for me)
- 2026-10-28 verify: fix loop 1: keyed writes require a current member, scoped findInGroup/findByKey/linesOf, actor in request hash, key capped at 255; NOT_A_MEMBER added to LedgerConflict.Reason (beyond plan's 4); JournalAccessTest added
- 2026-10-28 verify: fix loop 2: isCurrentMember takes FOR SHARE on actor member row (FOR KEY SHARE does not block the left_at UPDATE); lock order member row > advisory > balances; 2 latch tests; test_cmd green, lint green
- 2026-10-28 verify: re-verify qa pass, security pass (1 minor open: line members not checked current); review.md final
- 2026-10-28 verify: g3 approved by Shanerex, minor open accepted; bump minor; phase close
- 2026-10-28 close: knowledge written (2 decisions, 1 learning); rx-check done found state.md over cap (collapsed) and architecture.md modified
- 2026-10-29 close: rx-check done only violation is architecture.md (plan-approved edit, 2 lines); human chose keep edit and accept the failing check; close stays blocked
