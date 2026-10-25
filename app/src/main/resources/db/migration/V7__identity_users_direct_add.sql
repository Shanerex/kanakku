alter table users
    add column direct_add text not null default 'ALLOW'
        constraint users_direct_add_check check (direct_add in ('ALLOW', 'INVITE_ONLY'));
