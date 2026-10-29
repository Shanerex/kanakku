---
status: final
---
# Tasks: 2026-10-28-ledger-core

## Tasks
<!-- Each task cites at least one existing AC. Tick to - [x] when done. -->
- [x] T-1 (AC-5): V16__ledger_entry_links_and_keys.sql (seq, reverses/replaces links, idempotency_key, request_hash, unique partial indexes, REVERSAL link check) plus jOOQ regen; test that UPDATE and DELETE on journal_entries and journal_lines are rejected and existing rows got a seq.
- [x] T-2 (AC-4, AC-11): harden JournalService.post: reject an empty, unbalanced or zero-amount line and an unknown ISO 4217 code; tests that a rejected post leaves the journal and balances unchanged, and that two currencies each sum to zero.
- [x] T-3 (AC-4): V17__ledger_balanced_entry_check.sql deferred constraint trigger plus jOOQ regen; test that a raw unbalanced line insert fails at commit and leaves no rows.
- [x] T-4 (AC-12, AC-13): JournalEntry.expense and JournalEntry.settlement factories; integration tests for A/B/C shares and B-to-A settlement keeping zero-sum.
- [x] T-5 (AC-8): Journal.post(entry, key) returning Posted; durable key with request hash; tests for replay (one entry, same id, balances unchanged), different content giving LedgerConflict KEY_REUSED, and a concurrent same-key post.
- [x] T-6 (AC-14): Journal.reverse: a REVERSAL linked by reverses_entry_id; only EXPENSE and SETTLEMENT (else NOT_REVERSIBLE); tests that balances return to their prior values, one reversal is appended, a second reverse gives ALREADY_REVERSED, another group gives NOT_IN_GROUP, and a WRITE_OFF gives NOT_REVERSIBLE.
- [x] T-7 (AC-6): Journal.replace: reversal plus new entry with replaces_entry_id in one tx, keys <key>:rev and <key>, EXPENSE and SETTLEMENT only; replay test.
- [x] T-8 (AC-6, AC-10): History.ofGroup with JournalRepository.history (callerIsMember in SQL, seq order, afterSeq cursor, limit at most 200); tests for original, reversal, new order and for a non-member getting empty history and balances.
- [x] T-9 (AC-7): rollback test that a spy on addToBalance throws after the journal insert, leaving no journal row and no balance change.
- [x] T-10 (AC-15): group lock in JournalRepository (lockGroupShared/lockGroupExclusive, a per-group advisory xact lock), taken first by JournalService.postInTransaction, WriteOffService and MemberMergeService; test that existing post, write-off and merge tests still pass.
- [x] T-11 (AC-9): JournalRepository.mismatchedGroups, mismatchesOf and repairBalances, plus BalanceCheckService (per-group tx, exclusive lock when repair is on, recheck inside the lock, one ERROR line and counter +1 per group+currency, upsert only mismatched rows); tests: repair on fixes the corrupt row to the journal sum with one line and +1; repair off leaves the row untouched with one line and +1; a clean ledger logs, counts and writes nothing.
- [x] T-12 (AC-9): BalanceCheckJob plus BalanceCheckProperties (cron default `0 30 2 * * *` UTC, repair default true) in application.yml; tests that the task bean is registered, uses the cron, defaults repair to true and honours repair=false.
- [x] T-13 (AC-15): Testcontainers concurrency test with two real threads and latches. (a) Repair is paused inside the exclusive lock while a post to the same group runs. (b) A post holds the shared lock in an open tx while repair runs. In both, balances end equal to the journal sums, each currency sums to zero, and there is no deadlock (timeout).
- [x] T-14 (AC-1, AC-2, AC-3): jqwik stateful LedgerProperties over INR, JPY and KWD (add, edit, delete, settle; after each chain check zero-sum, per-entry balance and rebuild equality, plus the reverse-restores property), with one body and two entry points: tries 1000 tagged ledger-quick and tries 10000 tagged ledger-full.
- [x] T-15 (AC-1): app/pom.xml surefire excludes ledger-full by default; profile `ci` (activated by env.CI, or -Pci) excludes ledger-quick and sets kanakku.ci=true; LedgerCiProfileGuardTest fails when CI is set but kanakku.ci is not; run `-Pci` once to confirm 10k executes.
- [x] T-16 (AC-10): update ledger.api package-info; the Modulith verification test passes and a grep test shows no module outside ledger references journal_* or member_balances tables.
