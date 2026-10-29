tags: [ledger, locking, repair]
Decision: every ledger write takes a shared per-group advisory xact lock (`hashtextextended('ledger:' || group_id, 0)`) before any balance row lock; the nightly repair takes it exclusive.
Why: the lock touches no other module's table, posts to one group stay parallel, repair excludes them all, and one fixed order means no lock cycle. Keyed post, reverse and replace also take `FOR SHARE` on the actor's member row first, so a concurrent leave waits for the post.
