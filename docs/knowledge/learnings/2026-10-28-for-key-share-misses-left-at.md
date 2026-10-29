tags: [ledger, postgres, locking]
`FOR KEY SHARE` on a member row does not block a leave, because leave only updates `left_at`, which is not a key column; use `FOR SHARE` to make a leave wait for an in-flight post.
Open: a post checks the actor is a current member but not the members named on its lines, so a removed member can slip a non-zero balance past the settled check; the expense and settle-up callers must check it. The 10k jqwik run takes about 12 min, not the 3-6 min estimated.
