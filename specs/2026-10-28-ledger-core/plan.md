---
status: final
---
# Plan: 2026-10-28-ledger-core

## Approach
- The ledger module already exists (V9, V13; JournalService, WriteOffs, MemberMerges, Balances). This feature hardens it in place rather than rebuilding it.
- Already met, only needs tests: append-only triggers (R-2, AC-5), one-transaction post with sorted locks (R-4, AC-7), member-keyed balances (R-11), caller-scoped balance SQL (R-9).
- Added: durable posting keys (R-7), reversal and replace links plus a total order (R-3, AC-6, AC-14), a scoped history read (R-9, R-10), expense and settlement line builders (R-12, R-13), ISO 4217 checks (R-5), and the nightly check that detects and then repairs under a per-group lock (R-8, R-14, R-15).
- The jqwik stateful property (AC-1..AC-3) runs against Testcontainers Postgres through `ledger.api` only: 10,000 tries in CI, 1,000 locally (Q3).

## Options considered
- A (chosen, Q1): extend `journal_entries` with link, order and key columns in one forward migration. Fewest joins, and the unique indexes enforce once-only reversal and once-only posting in the database. Cost: an ALTER on an append-only table, which row triggers do not block.
- B: add separate append-only `journal_links` and `ledger_posting_keys` tables. Leaves V9's table untouched, but every history read and replay needs extra joins, and the module owns two more tables.
- C: reuse platform `IdempotencyService` (24h TTL, HTTP-response shaped) for R-7. No new key column, but after the TTL a replay double-posts, and it stores responses instead of entry ids. Rejected for the ledger; the HTTP layer of later features still uses it.
- Repair lock (R-15): a per-group advisory xact lock (chosen; see Design), rather than `SELECT ... FOR UPDATE` on `groups`. The row lock would lock another module's table, would make posts to one group serialise against each other, and would collide with groups' own row locks.

## Design
- Module: `dev.kanakku.ledger` only. Calls `platform.idempotency` (named interface) for `IdempotencyService.hash` only. Its callers stay `groups` (WriteOffs, MemberMerges, Balances) and, later, `expenses`/`settlement`. No module internals are touched.
- Migration `V16__ledger_entry_links_and_keys.sql` adds these to `journal_entries`: `seq bigint generated always as identity` (unique; history order, since `created_at` is identical inside one tx), `reverses_entry_id uuid` (FK, unique partial index, so an entry has at most one reversal), `replaces_entry_id uuid` (FK), `idempotency_key text`, `request_hash bytea`, and a unique partial index `(group_id, idempotency_key)`. It also adds a check that a REVERSAL has `reverses_entry_id` and nothing else does.
- Migration `V17__ledger_balanced_entry_check.sql` (Q2): a deferred constraint trigger on `journal_lines` that rejects an entry whose lines do not sum to zero per currency at commit. It is a database backstop for R-1/AC-4.
- jOOQ classes are regenerated in the same change as V16/V17. Repair needs no migration: advisory locks have no table.
- `ledger.api` changes, all additive. Existing 4-arg `JournalEntry` callers compile unchanged.
  - `JournalEntry`: adds static factories `expense(groupId, actor, currency, payerMemberId, LinkedHashMap<memberId, shareMinor>)` (payer +total, each participant -share; R-12, AC-12) and `settlement(groupId, actor, currency, fromMemberId, toMemberId, amountMinor)` (payer +amount, payee -amount; R-13, AC-13).
  - `Journal`: adds `Posted post(JournalEntry, String idempotencyKey)`, `Posted reverse(UUID groupId, UUID entryId, UUID actor, String key)` and `Posted replace(UUID groupId, UUID entryId, JournalEntry next, String key)`. `Posted(UUID entryId, boolean replayed)`. The old `post(entry)` remains for WRITE_OFF/MERGE (unkeyed system entries inside groups' own idempotent requests).
  - `History.ofGroup(UUID groupId, UUID callerId, Long afterSeq, int limit)` returns `List<HistoryEntry>` (id, seq, kind, actor, createdAt, reversesEntryId, replacesEntryId, lines), ascending by seq, with limit capped at 200 (AC-6).
  - `LedgerConflict` (RuntimeException, with a reason enum): `KEY_REUSED`, `ALREADY_REVERSED`, `NOT_REVERSIBLE`, `NOT_IN_GROUP`. Later features map it to a 409/422 problem.
- Posting rules (`service/JournalService`):
  - Reject an empty or unbalanced entry, a zero-amount line, or an unknown ISO 4217 code (via `java.util.Currency`, matching `platform.validation.ValidCurrency`). Use `addExact` throughout.
  - EXPENSE, SETTLEMENT and their reversal/replace must carry a key. WRITE_OFF and MERGE may omit it.
- Idempotency (R-7, AC-8, Q1): the key is stored on the entry and never expires. Insert uses `ON CONFLICT (group_id, idempotency_key) DO NOTHING`. On conflict, the service loads the stored `request_hash` (SHA-256 of kind plus canonical lines). The same hash returns `Posted(existingId, true)` and touches no balances. A different hash throws `KEY_REUSED`. A concurrent duplicate waits on the unique index, then replays.
- Reverse (R-3, AC-14, Q5): `NOT_IN_GROUP` unless the original is in `groupId`. Only EXPENSE and SETTLEMENT can be reversed or replaced; any other kind throws `NOT_REVERSIBLE`. The reversal posts the negated lines as REVERSAL with `reverses_entry_id`. The unique index makes a second reversal fail with `ALREADY_REVERSED`; this is the ledger's optimistic check for edits, while `expenses` keeps its own row-version check later.
- Replace (R-3, AC-6): reversal plus new entry (`replaces_entry_id`) in one transaction. Keys are `<key>:rev` and `<key>`, so a replay is whole.
- Scoping (R-9, AC-10): `JournalRepository.history` and both balance reads keep `callerIsMember` (EXISTS on `group_members`, `left_at is null`) inside the SQL. A non-member gets an empty list. The nightly check is a system job with no caller and returns nothing to any client.
- Group lock (R-15):
  - `JournalRepository.lockGroupShared(groupId)` is `pg_advisory_xact_lock_shared(k)`; `lockGroupExclusive(groupId)` is `pg_advisory_xact_lock(k)`. The key is `k = hashtextextended('ledger:' || group_id, 0)`.
  - Why: the lock belongs to the ledger and touches no other module's table. Posts take it shared, so posts to one group still run in parallel (as today they serialise only on shared balance rows), while repair takes it exclusive and so excludes them all. It is released at commit. A hash collision only adds waiting, never lost data.
  - Order: every write path takes the shared group lock first, before any balance row lock:
    - `JournalService.postInTransaction`;
    - `WriteOffService` and `MemberMergeService`, before `lockBalancesOfMember`.
  - Repair takes the exclusive lock first, before any row lock, so no lock cycle exists. Advisory xact locks are re-entrant, so the nested `journal.post` re-taking the shared lock is harmless.
- Nightly check (R-8, R-14, R-15, AC-9, AC-15, Q4):
  - `ledger/config/BalanceCheckJob`: db-scheduler `RecurringTask` on `Schedules.cron(kanakku.ledger.balance-check.cron, UTC)`, default `0 30 2 * * *`. `kanakku.ledger.balance-check.repair` defaults to true. Both are bound in `ledger/config/BalanceCheckProperties` and set in `application.yml`. db-scheduler runs the job on one instance.
  - `ledger/service/BalanceCheckService`:
    - First, `JournalRepository.mismatchedGroups()`: one aggregate SQL that is statement-consistent, because a post commits journal and balances together. It does a full outer join of `sum(journal_lines)` by (group, member, currency) against `member_balances` and returns the distinct group ids where they differ.
    - Then, per group, in its own `TransactionTemplate` tx:
      - if repair is on, `lockGroupExclusive`;
      - recompute with `JournalRepository.mismatchesOf(groupId)`, which reads after the lock so it sees every committed post;
      - per (group, currency), one SLF4J `atError()` line with key-values `groupId`, `currency`, `members` (count), `repaired` (bool), and no amounts, plus a counter increment;
      - if repair is on, `JournalRepository.repairBalances(groupId, rows)`: one `INSERT .. ON CONFLICT DO UPDATE SET balance_minor = excluded.balance_minor` for the mismatched rows only (a balance with no lines becomes 0, never deleted).
    - A group that is clean on recheck logs nothing.
    - With repair off, no lock is taken and nothing is written.
    - A failure in one group's tx is logged and the job moves on to the next group.
  - Metric: Micrometer counter `kanakku.ledger.balance.mismatches`, +1 per (group, currency), with no group tag to keep cardinality low. A clean ledger logs nothing at ERROR, counts nothing and writes nothing.
  - Layering: all lock and upsert SQL lives in `JournalRepository`; the service holds no `DSLContext`.
- REST/OpenAPI: none (R-10). `openapi/kanakku-v1.json` stays unchanged and OpenApiSnapshotTest guards it.
- Web screen: none. R-10 and the requirements' Out of scope defer the balances/history screen to the expense UI feature, so no route, `docs/design/screens/` spec, mock or components apply. Mobile: none.
- Money: the ledger does no conversion, so it has no `BigDecimal` and no rounding. Shares arrive as exact `long` minor units. The property builds expense shares with `ledger/util/LargestRemainder` (existing), in stable member order.
- jqwik (test `ledger/LedgerProperties`): one stateful `ActionChain` over add, edit (replace), delete (reverse) and settle, in INR, JPY and KWD, with 2-6 members and a fresh group per try. Invariants are checked after every chain:
  - zero-sum per group per currency (AC-1);
  - debits equal credits per entry (AC-2);
  - rebuilt balances equal `member_balances` (AC-3, via `mismatchesOf`);
  - replaying a key changes nothing (AC-8).
  - Plus a small property: reverse restores the prior balances (AC-14).
- Property sizing (Q3): one shared body, two entry points.
  - `@Property(tries = 1000) @Tag("ledger-quick")` and `@Property(tries = 10000) @Tag("ledger-full")`.
  - `app/pom.xml` surefire defaults to `excludedGroups=ledger-full`.
  - Maven profile `ci` sets `excludedGroups=ledger-quick` and `kanakku.ci=true`. It is activated by `<property><name>env.CI</name></property>`, so CI's `./mvnw -B -q test` runs 10k; `-Pci` forces it locally.
  - `LedgerCiProfileGuardTest` fails when env `CI` is set but `kanakku.ci` is not.
- Tests use Testcontainers Postgres.
  - AC-5 issues raw UPDATE/DELETE.
  - AC-7: `@MockitoSpyBean JournalRepository` throws on `addToBalance`.
  - AC-9: the log line via a ListAppender, the counter via `SimpleMeterRegistry`.
  - AC-15 uses two real threads and latches, in both orders:
    - (a) repair holds the lock (paused after `lockGroupExclusive`) while a post waits;
    - (b) a post holds the shared lock in an open tx while repair waits.
    - After both finish, balances equal the journal sums and each currency sums to zero.
- AI: none. No draft path; no provider is used.

## Architecture changes
- Proposed (needs approval) under Invariants: "Ledger postings are idempotent by a durable key stored on the journal entry; an entry is reversed at most once."
- Proposed: replace architecture.md:11's "nightly rebuild check (detect only: ERROR log + metric, never rewrites balances)" with "nightly rebuild check: detects (ERROR log + metric per group and currency), then repairs balances from the journal (`kanakku.ledger.balance-check.repair`, default on) under a per-group advisory lock that every post also takes".

## Risks
- V16/V17 number collides with another branch: check `main` before merge and renumber the new files (they are not on main yet).
- The 10k run is slow (estimated 3-6 min): it runs only under the `ci` profile, with one fresh group per try and short chains (at most 12 actions). The guard test catches a profile that fails to activate.
- Repair could hide a posting bug: the ERROR line and counter still fire first. With repair on, the journal stays the truth and only the derived table is rewritten.
- A deadlock could arise between repair and write-off/merge: every path takes the group advisory lock before any balance row lock (AC-15 tests both orders).
- Repair blocks a group's posts while it runs: one short tx per group, only for groups that mismatch.
- Adding the identity column rewrites `journal_entries`: row triggers do not fire on ALTER, and the table is small pre-1.0.
- The nightly full scan grows with the journal: a single aggregate query is fine at self-hosted scale. Revisit with per-group batching if it is slow.
- Unkeyed WRITE_OFF/MERGE could double-post on retry: they run inside groups' idempotent HTTP requests in one tx (unchanged behaviour).
