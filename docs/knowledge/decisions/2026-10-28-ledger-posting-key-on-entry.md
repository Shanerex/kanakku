tags: [ledger, idempotency]
Decision: ledger postings are idempotent by a durable key stored on `journal_entries` (unique per group, with a request hash that includes the actor), and an entry is reversed at most once by a unique index on `reverses_entry_id`.
Why: platform `IdempotencyService` expires after 24h and stores HTTP responses, so a late replay would double-post; the database index enforces once-only posting and once-only reversal.
