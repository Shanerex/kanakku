---
status: final
---
# Review: 2026-10-28-ledger-core

Re-verified after two fix loops (membership check, actor in request hash, key length cap, membership race lock).

## QA
`./mvnw -B -q test` exit 0; 558 tests, 0 failures, 0 errors, 0 skipped; ModularityTest 4/4; no @Disabled.
- AC-1 pass: LedgerProperties zero-sum. QA ran 1000 tries; the 10k `ledger-full` run (`-Pci`, ~12 min) is green only per the developer's log in state.md.
- AC-2 pass: per-entry debits equal credits (LedgerProperties, JournalPropertiesTest).
- AC-3 pass: rebuild from journal equals member_balances.
- AC-4 pass: JournalServiceTest empty, zero, unbalanced, bad currency; V17 raw-insert test.
- AC-5 pass: DB rejects UPDATE and DELETE on journal rows.
- AC-6 pass: JournalHistoryTest order original, reversal, new entry.
- AC-7 pass: JournalRollbackTest.
- AC-8 pass: JournalIdempotencyTest (replay, KEY_REUSED, concurrent same key).
- AC-9 pass: BalanceCheckServiceTest (repair on, off, clean); BalanceCheckJobTest.
- AC-10 pass: non-member reads empty; LedgerTablesOwnershipTest; JournalAccessTest (7).
- AC-11 pass: separate zero-sum per currency (INR, JPY, KWD).
- AC-12, AC-13 pass: ExpenseAndSettlementTest (example tests; property covers invariants).
- AC-14 pass: JournalReversalTest plus jqwik reversal property.
- AC-15 pass: BalanceRepairConcurrencyTest, real threads, no deadlock.
- Fix loop 2 pass: `isCurrentMember` takes FOR SHARE on actor's member row first in the tx; two latch tests green.
- T-1..T-16 ticked and test-backed. No REST endpoint, no screen owed. No double/float on amounts.
- Plan deviation, non-blocking: `LedgerConflict.Reason` adds NOT_A_MEMBER beyond plan's 4 reasons.

## Security
Earlier findings all fixed: membership on write path and SQL reads; actor in request hash; key cap 255; membership race (FOR SHARE conflicts with leave/remove FOR UPDATE and markLeft FOR NO KEY UPDATE). Lock order member row, advisory lock, FK key-share, balance rows; no cycle found.
- minor (open): JournalService.java:227: actor checked as current member but line members are not. A remove locking member B first plus a concurrent post naming B lets B leave with a non-zero balance, bypassing requireSettled. Check line members' `left_at IS NULL` in SQL, or require calling modules to.
- note, outside diff: MemberRepository.java:160-163 javadoc now understates the conflict (write-off lock waits on an absorber's in-flight post).
- clean: bind parameters only; no secrets, amounts in logs, AI, upload, REST or dependency changes.

## Verdict
pass. No blocking items. One open minor (line members not checked as current); fix or accept.
