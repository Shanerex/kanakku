create table sign_in_failures (
    id bigserial primary key,
    email_key text not null,
    ip inet not null,
    failed_at timestamptz not null
);

create index sign_in_failures_key_idx on sign_in_failures (email_key, ip, failed_at);
