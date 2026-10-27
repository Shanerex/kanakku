-- An invite made for a user the Owner found by search is bound to that account: only they can
-- claim it. Invites made for a plain guest leave it null and any signed-in holder may claim them.
alter table group_invites
    add column invited_user_id uuid references users (id);
