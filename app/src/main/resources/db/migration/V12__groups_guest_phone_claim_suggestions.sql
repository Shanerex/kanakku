-- A guest may carry a phone number (E.164) so that a user who later verifies the same number can be
-- offered the guest's seat. merged_into_member_id is set when such a claim moves the guest's
-- balance onto the user's own member row; the guest row is then retired (left_at set).
alter table group_members
    add column guest_phone_e164 text,
    add column merged_into_member_id uuid;

alter table group_members
    add constraint group_members_merged_into_fk
        foreign key (merged_into_member_id, group_id) references group_members (id, group_id);

create index group_members_guest_phone_idx
    on group_members (guest_phone_e164)
    where guest_phone_e164 is not null and user_id is null and left_at is null;

-- "You might be <guest> in <group>": created when a user verifies a number equal to a guest's in a
-- group they belong to. Nothing merges until the user confirms.
create table claim_suggestions (
    id uuid primary key,
    group_id uuid not null references groups (id),
    member_id uuid not null,
    user_id uuid not null references users (id),
    status text not null default 'PENDING'
        constraint claim_suggestions_status_check
            check (status in ('PENDING', 'DECLINED', 'CONFIRMED')),
    version integer not null default 0,
    created_at timestamptz not null default now(),
    constraint claim_suggestions_member_fk
        foreign key (member_id, group_id) references group_members (id, group_id),
    constraint claim_suggestions_member_user_key unique (member_id, user_id)
);

create index claim_suggestions_user_idx on claim_suggestions (user_id, status);
