---
status: final
---
# Requirements: 2026-10-28-ledger-core

## Problem
Every member needs to trust who owes whom. Without an append-only double-entry journal and a balance table derived from it, balances can drift and cannot be audited. This is the source of truth that expenses and settlements post to (PRD F-05).

## Requirements
- R-1: Posting an expense or settlement writes one journal entry whose debits equal its credits, per currency.
- R-2: The journal is append-only; no row is updated or deleted after insert.
- R-3: Deleting a posted item appends a pure reversal entry linked to the original; editing appends a reversal and then a new entry.
- R-4: The balance table is updated in the same transaction as the journal post; a failure rolls back both.
- R-5: Amounts are `long` minor units with an ISO 4217 currency; no cross-currency netting.
- R-6: Per group and currency, balances sum to zero.
- R-7: Posting is idempotent by `Idempotency-Key`; a replay creates no second entry.
- R-8: A nightly db-scheduler job recomputes balances from the journal and compares them to `member_balances`. On any mismatch it emits one structured ERROR line per group and currency and increments the metric (no email, no alert row). Then, when repair is on, it overwrites the mismatched balance rows with the journal sums.
- R-9: History (entries with their reversals) and balance reads are scoped to the caller's group membership in the SQL query.
- R-10: The ledger exposes posting, history and balance reads only through its `api/` interface; no REST endpoint, no screen, and no other module touches its tables.
- R-11: Balances are stored per member per currency within a group.
- R-12: An expense posts a credit to the payer and debits to the participants for their shares.
- R-13: A settlement posts as a transfer between two members.
- R-14: Repair is controlled by the property `kanakku.ledger.balance-check.repair`, default true; when false the job only detects.
- R-15: Each group is repaired in its own transaction holding a lock that excludes concurrent posts to that group, and the journal sums are recomputed inside that lock, so no posting that commits during the job is lost.

## Acceptance criteria
- AC-1: A jqwik property runs 10,000 random sequences of add, edit, delete and settle; after each sequence every group's balances sum to zero per currency (R-6).
- AC-2: In that property, every journal entry has total debits equal to total credits (R-1).
- AC-3: In that property, balances rebuilt from the journal equal the balance table exactly (R-8).
- AC-4: An unbalanced posting is rejected and leaves journal and balances unchanged (R-1, R-4).
- AC-5: An update or delete against a journal row is rejected by the database (R-2).
- AC-6: After an edit, history shows the original, its reversal and the new entry, in that order (R-3, R-9).
- AC-7: A forced failure after the journal insert leaves neither a journal row nor a balance change (R-4).
- AC-8: Posting twice with the same `Idempotency-Key` yields one entry and unchanged balances (R-7).
- AC-9: With repair on, one corrupted balance row gives one ERROR line naming the group and currency, the metric rises by 1, and the row then equals the journal sum; with repair off the row stays untouched; a clean ledger logs nothing, counts nothing and writes nothing (R-8, R-14).
- AC-10: A non-member reading a group's history or balances through `api/` gets no data (R-9, R-10).
- AC-11: A group with two currencies keeps a separate zero-sum balance per currency (R-5, R-6).
- AC-12: An expense paid by A with shares for A, B and C leaves A positive and B and C negative by their shares (R-11, R-12).
- AC-13: A settlement from B to A moves B's balance up and A's down by the amount, and the group still sums to zero (R-6, R-13).
- AC-14: Deleting a posted expense returns every member's balance to its prior value and appends one reversal entry (R-3).
- AC-15: With a posting running concurrently with the repair of the same group, after both finish balances equal the journal sums and the group sums to zero per currency (R-6, R-15).

## Out of scope
- Expense and settlement CRUD, splits, and UPI flows (later features); this feature is exercised through `api/` and tests.
- REST endpoints and web screens; the balances and history screen ships with the expense UI.
- Currency conversion and FX rates.
- Debt simplification and "who pays whom" suggestions.
- Email alerts and persisted alert rows for ledger mismatches.
