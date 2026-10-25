tags: [ledger, groups, guests]
Decision: ledger balances and journal lines are keyed by group member id, not user id.
Why: a guest has no user; when an invite is claimed the same member row gains the user, so history and balance stay intact.
Consequence: expenses and settlement call ledger.api with a group id already checked by a membership-scoped query.
