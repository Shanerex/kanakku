create table users (
    id uuid primary key,
    email text not null,
    email_verified boolean not null default false,
    display_name text not null,
    default_currency char(3) not null default 'INR',
    upi_id text,
    version integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index users_email_lower_key on users (lower(email));

create table password_credentials (
    user_id uuid primary key references users (id),
    password_hash text not null,
    updated_at timestamptz not null default now()
);
