---
paths:
  - "app/src/main/resources/db/migration/**"
  - "**/db/migration/**"
---
# Flyway migration rules

- Migrations are forward-only. Never edit, rename or delete a migration that is already on `main` (a hook blocks it); fix it with a new migration.
- Name new files `V<n>__<snake_case_summary>.sql`, with `<n>` one more than the highest existing version. Check the folder and `main` first so two branches do not take the same number.
- Money columns are `bigint` minor units next to a `char(3)` currency column. Never `numeric` or `real` for stored amounts; rates may be `numeric`.
- Journal tables are append-only: no `UPDATE` or `DELETE` paths, and no `ON DELETE CASCADE` into them.
- Add a unique constraint where the PRD requires once-only behaviour: idempotency keys, UPI reference (UTR), one recurring occurrence per period.
- Every table that holds group data carries `group_id`, so queries can scope by membership in SQL.
- After a schema change, regenerate the jOOQ classes in the same change.
