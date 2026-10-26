-- A claim suggestion confirmed by a user who already has their own member row posts MERGE entries
-- that move a guest's balance onto that row. The journal itself stays append-only.
alter table journal_entries drop constraint journal_entries_kind_check;
alter table journal_entries
    add constraint journal_entries_kind_check
        check (kind in ('EXPENSE', 'SETTLEMENT', 'WRITE_OFF', 'REVERSAL', 'MERGE'));
