-- A suggestion is STALE when, at confirm time, the user no longer holds the verified number or the
-- guest's number no longer matches it. Nothing merges; the suggestion leaves the user's list.
alter table claim_suggestions drop constraint claim_suggestions_status_check;
alter table claim_suggestions
    add constraint claim_suggestions_status_check
        check (status in ('PENDING', 'DECLINED', 'CONFIRMED', 'STALE'));
