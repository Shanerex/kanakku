create table idempotency_keys (
    scope text not null,
    key text not null,
    request_hash bytea not null,
    status integer not null,
    response jsonb,
    created_at timestamptz not null,
    primary key (scope, key)
);

create index idempotency_keys_created_at_idx on idempotency_keys (created_at);
