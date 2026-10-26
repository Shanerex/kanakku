tags: [ledger, groups, phone]
Decision: a claim suggestion confirmed by a user who is already a member posts one MERGE journal entry per currency through ledger.api MemberMerges (-B guest, +B user's member); the guest row is retired with merged_into_member_id.
Why: history stays honest and append-only; a write-off would misname a claim, and rebinding rows would refuse most real cases.
Consequence: new ledger entry kind MERGE, posted only by MemberMerges, with one ledger migration.
