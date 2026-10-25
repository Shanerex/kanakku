---
status: final
---
# Tasks: 2026-10-24-groups-members

## Tasks
- [x] T-1 (AC-9): V7 `users.direct_add` (default ALLOW) and jOOQ regen; add `directAdd` to `Profile` and `PUT /api/v1/me` with its version check; integration test for toggling it and back.
- [x] T-2 (AC-6, AC-7, AC-8): `identity.api.UserDirectory` (search, summaries, allowsDirectAdd) and `util.EmailMask`; test the 3-char minimum, the 10-result limit, masking, and that invite-only and excluded ids are left out.
- [x] T-3 (AC-14): `ledger` module with V9 journal, lines and `member_balances`, `Journal.post` and the balance upsert in one tx; jqwik property 1 from plan.md (balances sum to zero per currency).
- [x] T-4 (AC-13, AC-14): `ledger.util.LargestRemainder`, `WriteOffs.writeOff` with absorbers, and `Balances.ofMember`/`ofGroup`; jqwik properties 2-5 (exact sum, single absorber takes all, parts within 1 unit, target at 0).
- [x] T-5 (AC-1, AC-2, AC-20): V8 groups, members and invites with jOOQ regen; `POST /api/v1/groups` with Idempotency-Key and validation of currency, name and cover preset; the creator becomes the only Owner.
- [x] T-6 (AC-19): `GET /api/v1/groups` and `/{groupId}` scoped by membership in SQL; a non-member gets an empty list and a 404.
- [x] T-7 (AC-15, AC-20): `PATCH` group, archive and unarchive with a version check (409 on a stale version); Owner only, a Member gets 403.
- [x] T-8 (AC-18): `groups.api.GroupAccess.requireWritable`; an archived group refuses ADD_EXPENSE and allows RECORD_SETTLEMENT, and unarchiving restores ADD_EXPENSE.
- [x] T-9 (AC-3, AC-20): `POST .../guests` with Idempotency-Key; name only or name plus email; the guest appears in `GET .../members` with balances.
- [x] T-10 (AC-7, AC-8): `GET .../user-search` (Owner only) excludes current members through `UserDirectory`; returns a problem under 3 chars.
- [x] T-11 (AC-4, AC-5, AC-6, AC-9): `POST .../members` direct-add (Owner only, Idempotency-Key); refuses invite-only users and Member callers and adds nobody; the outcome follows the toggle.
- [x] T-12 (AC-12, AC-15, AC-20): create, list and revoke invites; expiry 1/7/30 with 7 as default; the token is hashed; revoke checks the version and is Owner only.
- [x] T-13 (AC-10, AC-11): invite preview and claim scoped by token hash; the claim sets the guest row's `user_id`; journal lines posted for the guest and the balance are unchanged; expired, revoked or used gives 410 and no member.
- [x] T-14 (AC-13, AC-14, AC-15, AC-17): `DELETE` member and `POST .../write-offs` with `absorberMemberIds`; 409 `member-has-balance` lists the amounts; removal works after a write-off; non-owner and last-Owner attempts are refused.
- [x] T-15 (AC-16, AC-17): `POST .../leave`; a Member at zero balance can leave (also right after a direct-add); a non-zero balance gives 409 with the amounts; the Owner is refused.
- [x] T-16 (AC-19, AC-15): cross-group access test for every groups endpoint (non-member and Member roles) plus the Modulith verification test.
- [x] T-17 (AC-21): regenerate the `openapi/kanakku-v1.json` snapshot and `web/src/api/schema.d.ts`.
- [x] T-18 (AC-21): web `GroupsPage` (list and create, sends Idempotency-Key) with Vitest.
- [x] T-19 (AC-21, AC-14): web `GroupPage` with members, balances, guest add, archive, write-off dialog (all active absorbers preselected) and `MemberPicker`, with Vitest.
- [x] T-20 (AC-21): web `InvitesPanel` (create, copy, revoke) and `ClaimInvitePage` (`/invite#token`) with Vitest.
- [x] T-21 (AC-9, AC-21): web "who can add me directly" toggle in `ProfileForm` with Vitest.
- [x] T-22 (AC-21): Playwright `e2e/groups.spec.ts` for the full AC-21 flow.
