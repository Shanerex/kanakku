create table groups (
    id uuid primary key,
    name text not null,
    type text not null constraint groups_type_check check (type in ('TRIP', 'HOME', 'COUPLE', 'OTHER')),
    currency char(3) not null,
    cover text,
    archived_at timestamptz,
    version integer not null default 0,
    created_at timestamptz not null default now()
);

-- A member is a registered user (user_id set) or a guest (user_id null, guest_name set). A claim sets
-- user_id on the guest's row, so every journal line that names this row carries over unchanged.
create table group_members (
    id uuid primary key,
    group_id uuid not null references groups (id),
    user_id uuid references users (id),
    guest_name text,
    guest_email text,
    role text not null constraint group_members_role_check check (role in ('OWNER', 'MEMBER')),
    joined_at timestamptz not null default now(),
    left_at timestamptz,
    version integer not null default 0,
    constraint group_members_has_identity check (user_id is not null or guest_name is not null),
    constraint group_members_id_group_key unique (id, group_id)
);

create unique index group_members_active_user_key
    on group_members (group_id, user_id) where left_at is null and user_id is not null;
create index group_members_user_idx on group_members (user_id) where left_at is null;
create index group_members_group_idx on group_members (group_id);

-- Only the SHA-256 of the token is stored; the token itself is shown once at creation.
create table group_invites (
    id uuid primary key,
    group_id uuid not null references groups (id),
    member_id uuid not null references group_members (id),
    token_hash bytea not null constraint group_invites_token_hash_key unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    used_at timestamptz,
    used_by uuid references users (id),
    created_by uuid not null references users (id),
    created_at timestamptz not null default now(),
    version integer not null default 0
);

create index group_invites_group_idx on group_invites (group_id);
