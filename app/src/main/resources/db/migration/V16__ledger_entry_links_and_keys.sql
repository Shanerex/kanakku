-- Links, total order and durable posting keys on the append-only journal. ALTER is not a row
-- operation, so the append-only triggers from V9 do not fire; existing rows are only numbered.

-- History order: created_at is the same for every entry posted inside one transaction.
alter table journal_entries
    add column seq bigint generated always as identity;
alter table journal_entries
    add constraint journal_entries_seq_key unique (seq);

-- A REVERSAL names the entry it undoes; a replacement names the entry it supersedes.
alter table journal_entries
    add column reverses_entry_id uuid references journal_entries (id),
    add column replaces_entry_id uuid references journal_entries (id);

-- An entry is reversed at most once.
create unique index journal_entries_reverses_key
    on journal_entries (reverses_entry_id) where reverses_entry_id is not null;

alter table journal_entries
    add constraint journal_entries_reversal_link_check
        check ((kind = 'REVERSAL') = (reverses_entry_id is not null));

-- Posting keys never expire: a replay, however late, returns the stored entry. The request hash
-- tells a true replay from a reused key with different content.
alter table journal_entries
    add column idempotency_key text,
    add column request_hash bytea;

alter table journal_entries
    add constraint journal_entries_key_hash_check
        check ((idempotency_key is null) = (request_hash is null));

create unique index journal_entries_idempotency_key
    on journal_entries (group_id, idempotency_key) where idempotency_key is not null;
