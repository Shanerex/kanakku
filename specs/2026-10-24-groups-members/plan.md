---
status: final
---
# Plan: 2026-10-24-groups-members

## Approach
- New `groups` module owns groups, members (users and guests), invites and roles (R-1..R-9, R-11, R-14..R-19).
- A minimal `ledger` module lands now: an append-only journal and per-member balances keyed by `member_id`, plus write-off. It covers the balance reads and write-off that R-10, R-12, R-13 and R-16 need. Expenses and settlements post to it later (F-04/F-05).
- Journal lines reference `group_members.id`, never `user_id`. A claim (R-9) only sets `user_id` on the guest's member row, so history and balance carry over with no data moves (R-10, AC-10).
- `identity` gains the "who can add me directly" setting and a named-interface `UserDirectory` for search, names and policy (R-5..R-7).
- AC-18 and the expense part of AC-10 are proven now at `GroupAccess` and through ledger posts. F-04/F-05 cover them end to end.
- Web: groups list/create, group detail (members, picker, invites, archive, write-off), invite claim and the account toggle (R-20, AC-21).

## Options considered
- A (chosen): minimal `ledger` module now (journal, balances, write-off, jqwik). Trade-off: F-04 must build on this schema, but money maths stays in ledger as architecture.md requires.
- B: a balance table inside `groups`, moved to ledger later. Trade-off: faster, but breaks "ledger owns all money maths" and forces a data migration later.
- C: split off the balance and write-off ACs and ship them after F-04. Trade-off: smallest change, but F-03 acceptance stays open and R-12 would be untestable.

## Design
- Modules: `dev.kanakku.groups` (owner). It calls `identity.api` (`CurrentUser`, new `UserDirectory`) and `ledger.api` (new `Balances`, `Journal`, `WriteOffs`). It also uses `platform` idempotency and problem. `ledger` calls only `platform`, so the dependency graph has no cycle.
- groups layers: controller `GroupController`, `MemberController`, `InviteController`, `InviteClaimController`; service `GroupService`, `MemberService`, `InviteService`; repository `GroupRepository`, `MemberRepository`, `InviteRepository`; domain records `Group`, `Member`, `Invite`, `Role`, `GroupType`, `CoverPreset`; api `GroupAccess`.
- identity: `domain.DirectAdd {ALLOW, INVITE_ONLY}`; `api.UserDirectory` with `search(q, excludeUserIds, limit)`, `summaries(ids)` and `allowsDirectAdd(id)`; `util.EmailMask`.
- ledger: api `Journal.post(entry)`, `Balances.ofMember(groupId, memberId)`, `Balances.ofGroup(groupId)` and `WriteOffs.writeOff(groupId, memberId, absorberIds, actorUserId)`; service `JournalService`; repository `JournalRepository`; util `LargestRemainder`.
- Migration `V7__identity_users_direct_add.sql`: `users.direct_add text not null default 'ALLOW'` with a check constraint.
- Migration `V8__groups_members_invites.sql`: `groups`, `group_members` and `group_invites`.
  - `groups`: id, name, type, currency char(3), cover, archived_at, version.
  - `group_members`: id, group_id, user_id null, guest_name, guest_email, role, joined_at, left_at, version; partial unique (group_id, user_id) where left_at is null.
  - `group_invites`: id, group_id, member_id, token_hash unique, expires_at, revoked_at, used_at, used_by, version.
- Migration `V9__ledger_journal.sql`: `journal_entries` (id, group_id, kind, actor_user_id, created_at), `journal_lines` (entry_id, group_id, member_id, currency char(3), amount_minor bigint) and `member_balances` (group_id, member_id, currency, balance_minor bigint, PK). No update or delete paths on journal tables. Regenerate jOOQ.
- Group endpoints:
  - `POST /api/v1/groups` (Idempotency-Key, scope `group-create`), `GET /api/v1/groups`, `GET /api/v1/groups/{groupId}`.
  - `PATCH /api/v1/groups/{groupId}`, `POST .../archive` and `POST .../unarchive` (each needs `version`, 409 on a stale one).
- Member endpoints:
  - `GET /api/v1/groups/{groupId}/members` (with balances per currency).
  - `POST .../guests` (Idempotency-Key) and `POST .../members` direct-add by `userId` (Idempotency-Key, Owner only).
  - `GET .../user-search?q=` (Owner only, rules per R-7).
  - `DELETE .../members/{memberId}?version=` and `POST .../leave`.
  - `POST .../members/{memberId}/write-offs` (Idempotency-Key, Owner only, body `absorberMemberIds`: non-empty, active members other than the target).
- Invite endpoints:
  - `POST .../invites` (Idempotency-Key; `memberId` and `expiryDays` in {1, 7, 30}, default 7), `GET .../invites`, `POST .../invites/{inviteId}/revoke` (version).
  - `POST /api/v1/invites/preview` and `POST /api/v1/invites/claim`: the token is in the body, never in the URL or a log line.
- `PUT /api/v1/me` adds `directAdd` to `UpdateProfileRequest`/`Profile` and keeps its existing version check.
- OpenAPI: regenerate `openapi/kanakku-v1.json` (OpenApiSnapshotTest) and `web/src/api/schema.d.ts`. All the above are additive.
- Idempotency: every create above runs inside `IdempotencyService.execute`. The request hash covers the path ids and the body, so a replay returns the first 201 body.
- Version checks: every edit runs `update ... where id = ? and version = ? returning`. Empty means 409 `stale-version`.
- Problems: 409 `member-has-balance` carries `balances: [{currency, amountMinor}]` (AC-13, AC-16). Other 409s are `last-owner`, `group-archived` and `already-member`; 410 `invite-unusable` covers expired, revoked and used (AC-11); 404 `group-not-found` covers non-members (AC-19).
- Scoping:
  - Every groups read joins `group_members gm on gm.group_id = ? and gm.user_id = :caller and gm.left_at is null` in the same jOOQ query. A non-member gets 0 rows, which becomes 404.
  - Owner-only writes add `gm.role = 'OWNER'` to the guarding SQL.
  - Invite preview and claim are scoped by `token_hash` in SQL (token-bearer read). Preview returns only the group name and the guest name.
  - `ledger.api` reads take a group id that groups' scoped SQL has already checked. Only `groups` calls them, and ledger has no endpoint of its own.
- Claim: one transaction locks the invite `for update` and checks it is not expired, revoked or used. It then refuses if the caller is already a member, sets `group_members.user_id`, and sets `used_at` and `used_by`.
- Remove and leave: one transaction locks the member row, reads `Balances.ofMember` and refuses if any balance is non-zero. Otherwise it sets `left_at`; the row is kept for history. The sole Owner is refused (R-16).
- Archive: `GroupAccess.requireWritable(groupId, callerId, Action.ADD_EXPENSE | RECORD_SETTLEMENT)` returns the caller's member id. It refuses ADD_EXPENSE when `archived_at` is set, and refuses every groups write except unarchive.
- Money (write-off, R-13):
  - The Owner picks one or more absorbing members; the web preselects every active member except the target.
  - Per currency with balance B on the target, one `WRITE_OFF` entry posts -B to the target and splits +B equally across the absorbers. A single absorber takes the whole amount.
  - Rounding happens once, in `ledger.util.LargestRemainder`: exact BigDecimal shares, floored to minor units, remainder units by largest remainder, ties broken by stable member order (joined_at, id).
  - The journal post and the `member_balances` upsert share one transaction.
  - A write-off is not a correction tool. A wrong split is fixed by editing the expense (F-04), and a payment made outside the app is recorded as a settlement (F-05).
- jqwik in `ledger`:
  - (1) Any sequence of posts keeps group balances at zero per currency.
  - (2) The absorber split sums exactly to B.
  - (3) A single absorber takes the full B.
  - (4) The absorber parts differ by at most 1 minor unit.
  - (5) After a write-off the target's balance is 0.
- Web (`web/src/features/groups/`):
  - `GroupsPage` (list and create) at `/groups`.
  - `GroupPage` (members, balances, guest add, archive, write-off dialog with an absorber multi-select) at `/groups/:groupId`.
  - `MemberPicker` (search and direct-add) and `InvitesPanel`.
  - `ClaimInvitePage` at `/invite#<token>`; the fragment keeps the token out of server logs and the Referer header.
  - The `features/me/ProfileForm` toggle for directAdd.
  - Playwright `e2e/groups.spec.ts`. Screens follow `docs/design/screens/` (groups spec) and tokens only.
- Mobile: none (out of scope).

## Architecture changes
- Modules: `ledger` keys balances by group member id (not user id), so guest claims keep their history. Dependencies: expenses/settlement -> groups.api + ledger.api; groups -> identity.api + ledger.api; ledger -> platform only.
- Invariants: a token-bearer read (invite preview and claim) is scoped by token hash in SQL instead of by membership.
- Invariants: module API reads (ledger balances) take a group id already checked by the caller-facing module's scoped SQL.
- Flows: Write-off: groups -> ledger WriteOffs -> journal + balances (one tx); the Owner-chosen absorbers split the amount equally by largest remainder.

## Risks
- A concurrent ledger post between the balance check and removal: the remove locks the member row and future posts must check `left_at` through `GroupAccess`. Tested with two transactions.
- Invite tokens: we store only the SHA-256 hash, but the create's idempotency record holds the link for 24 h. To reshare after that, revoke the invite and create a new one.
- The F-04 schema depends on V9: kept minimal and additive, and recorded in the architecture change.
- User search is an enumeration surface: Owner only, at least 3 chars, at most 10 results, masked emails, invite-only users hidden.
- AC-18 and the expense part of AC-10 are proven at `GroupAccess` and through ledger posts until F-04/F-05 add end-to-end tests.
