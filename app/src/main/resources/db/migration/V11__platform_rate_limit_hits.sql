-- One row per counted event (a wrong code, an SMS send, a search). A limit counts the rows of one
-- (bucket, key) inside its window; the cleanup job deletes old rows.
create table rate_limit_hits (
    bucket text not null,
    key text not null,
    at timestamptz not null
);

create index rate_limit_hits_bucket_key_at_idx on rate_limit_hits (bucket, key, at);
create index rate_limit_hits_at_idx on rate_limit_hits (at);
