---
status: final
---
# Requirements: 2026-10-24-groups-members

## Problem
Trip organizers and flatmates have no way to share costs in Kanakku. They need groups of registered users and
guests, direct-add or invite links to bring people in, and safe member removal. Every later feature depends on groups.

## Requirements
- R-1: A signed-in user can create a group with a name, a type (trip, home, couple, other), a default ISO 4217 currency and an optional cover preset (a color or an emoji).
- R-2: The creator becomes the group's Owner; every other member has the role Member.
- R-3: A member is a registered user or a guest (a name, optionally an email).
- R-4: An Owner or Member can add a guest to the group.
- R-5: Only an Owner can add a registered user directly, found by searching the instance's users by name or email.
- R-6: Each user has a setting "who can add me directly": allow (default) or invite-only; direct-add of an invite-only user is refused and they join as a guest via invite (R-4, R-8, R-9).
- R-7: User search needs at least 3 characters, returns at most 10 results with display name and masked email, and excludes invite-only users and users already in the group.
- R-8: An Owner can create a single-use invite link bound to one guest, with an expiry of 1, 7 (default) or 30 days, and can revoke it.
- R-9: A signed-in user can claim a guest's place through a valid invite link; the guest record becomes that user.
- R-10: A claimed guest keeps every expense, split and balance the guest had before claiming.
- R-11: An expired, revoked or already-used invite link is rejected.
- R-12: A member with a non-zero balance in any currency cannot be removed; the refusal states the amount owed per currency.
- R-13: An Owner can write off a member's balance, which allows removal; the write-off is a ledger entry and group balances still sum to zero per currency.
- R-14: An Owner can remove a member with a zero balance.
- R-15: A non-owner Member with a zero balance can leave the group on their own, including right after a direct-add.
- R-16: The last Owner can never leave or be removed.
- R-17: An Owner can archive and unarchive a group; an archived group is read-only except that settlements are still allowed so leftover balances can be cleared.
- R-18: Listing and reading groups, members and invites returns only groups the caller belongs to, enforced in the SQL query.
- R-19: Group and invite creation are idempotent by `Idempotency-Key`; group edits use optimistic version checks.
- R-20: The web app has screens to list and create groups, manage members and invites, search and add members (picker), archive a group, and set "who can add me directly" on the account screen.

## Acceptance criteria
- AC-1: Creating a group with valid input returns it with the caller as its only Owner (R-1, R-2).
- AC-2: An invalid currency code, empty name or unknown cover preset is rejected with an RFC 9457 problem detail (R-1).
- AC-3: A guest added with only a name appears in the member list; an optional email is stored when given (R-3, R-4).
- AC-4: An Owner direct-adding a registered user whose setting is allow succeeds and the user appears as a Member (R-5).
- AC-5: A non-owner Member's direct-add attempt is refused and adds nobody (R-5).
- AC-6: Direct-adding an invite-only user is refused, and that user is absent from search results (R-6, R-7).
- AC-7: A search query under 3 characters is rejected with a problem detail (R-7).
- AC-8: A matching search returns at most 10 results, each with display name and masked email only, excluding users already in the group (R-7).
- AC-9: The setting defaults to allow for a new user and can be toggled to invite-only and back, changing the next direct-add outcome (R-6).
- AC-10: A user who claims a valid invite takes the guest's place, every expense that included the guest still lists them, and their balance equals the guest's before the claim (R-9, R-10).
- AC-11: Claiming an expired, revoked or already-used invite fails and creates no member (R-8, R-11).
- AC-12: Creating an invite with an expiry other than 1, 7 or 30 days is rejected; omitting it gives 7 days (R-8).
- AC-13: Removing a member with a non-zero balance is refused and the response includes the amount owed and its currency (R-12).
- AC-14: After an Owner writes off a balance, removal succeeds and group balances still sum to zero per currency (R-13, R-14).
- AC-15: A non-owner cannot revoke an invite, write off a balance, remove another member or archive the group (R-8, R-13, R-14, R-17).
- AC-16: A Member with a zero balance can leave, including a just direct-added one; with a non-zero balance the leave is refused with the amount owed (R-12, R-15).
- AC-17: The last Owner's attempt to leave or be removed is refused (R-16).
- AC-18: In an archived group, adding an expense is refused and recording a settlement succeeds; unarchiving restores expenses (R-17).
- AC-19: A non-member gets no data for a group by listing and a not-found response by id (R-18).
- AC-20: Repeating a create with the same `Idempotency-Key` returns the first result with no duplicate; editing with a stale version is rejected with a conflict (R-19).
- AC-21: A Playwright flow creates a group, searches and adds a user, adds a guest, shares an invite, toggles the account setting and shows the member list (R-20).

## Out of scope
- Cover image upload and MinIO storage, and reusable group-join links; both deferred.
- Expenses, splits and settlement logic beyond the balance reads and write-off needed here; invite emails and push notifications.
- Transferring ownership, multiple Owners, and mobile screens.
