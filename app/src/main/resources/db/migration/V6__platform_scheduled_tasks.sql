-- db-scheduler's task table (Postgres schema from the library), used for the cleanup jobs.
create table scheduled_tasks (
    task_name text not null,
    task_instance text not null,
    task_data bytea,
    execution_time timestamptz not null,
    picked boolean not null,
    picked_by text,
    last_success timestamptz,
    last_failure timestamptz,
    consecutive_failures integer,
    last_heartbeat timestamptz,
    version bigint not null,
    priority smallint,
    primary key (task_name, task_instance)
);

create index scheduled_tasks_execution_time_idx on scheduled_tasks (execution_time);
create index scheduled_tasks_last_heartbeat_idx on scheduled_tasks (last_heartbeat);
create index scheduled_tasks_priority_execution_time_idx
    on scheduled_tasks (priority desc, execution_time asc);
