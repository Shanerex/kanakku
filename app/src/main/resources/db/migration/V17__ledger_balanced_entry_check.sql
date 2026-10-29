-- Database backstop for double entry: at commit, every entry's lines must sum to zero in each
-- currency. The service checks this before it writes; this catches any path that skips it.
create function journal_entry_balanced() returns trigger language plpgsql as $$
begin
    if exists (
        select 1
        from journal_lines
        where entry_id = new.entry_id
        group by currency
        having sum(amount_minor) <> 0
    ) then
        raise exception 'journal entry % does not sum to zero per currency', new.entry_id
            using errcode = 'check_violation';
    end if;
    return null;
end;
$$;

create constraint trigger journal_lines_balanced
    after insert on journal_lines
    deferrable initially deferred
    for each row execute function journal_entry_balanced();
