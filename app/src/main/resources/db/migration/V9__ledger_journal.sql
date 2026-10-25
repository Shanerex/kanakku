-- The journal is append-only: rows are inserted and never updated or deleted. A trigger enforces it,
-- so a stray UPDATE or DELETE fails loudly instead of rewriting history.
create table journal_entries (
    id uuid primary key,
    group_id uuid not null references groups (id),
    kind text not null constraint journal_entries_kind_check
        check (kind in ('EXPENSE', 'SETTLEMENT', 'WRITE_OFF', 'REVERSAL')),
    actor_user_id uuid references users (id),
    created_at timestamptz not null default now()
);

create index journal_entries_group_idx on journal_entries (group_id);

-- Lines name a group member row (never a user id) and must name a member of the same group.
create table journal_lines (
    entry_id uuid not null references journal_entries (id),
    line_no integer not null,
    group_id uuid not null references groups (id),
    member_id uuid not null,
    currency char(3) not null,
    amount_minor bigint not null,
    primary key (entry_id, line_no),
    constraint journal_lines_member_fk
        foreign key (member_id, group_id) references group_members (id, group_id)
);

create index journal_lines_member_idx on journal_lines (group_id, member_id);

-- Running balance per member and currency, updated in the same transaction as the journal post.
create table member_balances (
    group_id uuid not null references groups (id),
    member_id uuid not null,
    currency char(3) not null,
    balance_minor bigint not null,
    primary key (group_id, member_id, currency),
    constraint member_balances_member_fk
        foreign key (member_id, group_id) references group_members (id, group_id)
);

create function journal_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'journal tables are append-only (% on %)', tg_op, tg_table_name;
end;
$$;

create trigger journal_entries_append_only
    before update or delete on journal_entries
    for each row execute function journal_append_only();

create trigger journal_lines_append_only
    before update or delete on journal_lines
    for each row execute function journal_append_only();
