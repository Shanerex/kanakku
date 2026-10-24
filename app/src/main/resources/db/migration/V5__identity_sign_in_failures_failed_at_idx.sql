-- The retention prune deletes by failed_at alone; without this it scans the whole table.
create index sign_in_failures_failed_at_idx on sign_in_failures (failed_at);
