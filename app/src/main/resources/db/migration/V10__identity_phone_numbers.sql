-- A user has at most one mobile number, stored as E.164. It counts as theirs only once verified by
-- an SMS code; the partial unique index lets any number of accounts hold the same unverified
-- number but only one hold it verified.
alter table users
    add column phone_e164 text,
    add column phone_verified_at timestamptz;

create unique index users_phone_verified_key
    on users (phone_e164) where phone_verified_at is not null;

-- The code sent to a signed-in user's number (one live challenge per user). Only the HMAC of the
-- code is stored.
create table phone_challenges (
    user_id uuid primary key references users (id),
    phone_e164 text not null,
    code_hash bytea not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

-- A sign-up waiting for its SMS code. The account row is written only when the code is confirmed,
-- so an abandoned or failed sign-up leaves no user. Holds an Argon2 hash and is purged at expiry.
create table pending_sign_ups (
    id uuid primary key,
    email text not null,
    display_name text not null,
    password_hash text not null,
    phone_e164 text not null,
    code_hash bytea not null,
    code_expires_at timestamptz not null,
    sms_failed boolean not null default false,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index pending_sign_ups_expires_at_idx on pending_sign_ups (expires_at);
