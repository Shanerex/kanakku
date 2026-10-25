---
status: final
---
# Review: 2026-10-24-groups-members

## QA
Ran `./mvnw -B -q test` (355 Java tests, 0 failed, 0 skipped), `npx vitest run` (127 passed), e2e (3/3 passed after the fixes).
- AC-1..AC-21 pass, each proven by a named test (GroupCreateTest, GuestAndListTest, MemberAddTest, InviteTest, MemberRemoveTest, GroupUpdateTest, GroupAccessTest, CrossGroupAccessTest, e2e/groups.spec.ts).
- AC-10 proven with journal lines (no expense table yet); AC-18 proven at `GroupAccess` level (no expense endpoint yet).
- T-7 fixed in fix loop 1: Owner-only "Edit group" dialog on `GroupPage` (hidden for Members and archived groups), sends the loaded version, 409 keeps the dialog open; 6 Vitest cases and an e2e rename.
- Note: the server cannot clear a cover (null means unchanged), so the edit dialog hides "Clear cover".
- jqwik properties and ModularityTest (4 tests) ran; no `double`/`float` in the diff.

## Security
Fixed after g3 discussion: invite token is now HMAC-SHA256(secret, invite id), idempotency record holds no token and a replay recomputes it; `WriteOffRequest` capped at 50 absorbers; absorber rows locked `for update` in joined_at, id order. Secret `KANAKKU_INVITE_TOKEN_SECRET` needs 32+ chars, has no default in `application.yml`, never logged.
- minor (new): `MemberService.java:162-169` locks the target before the sorted absorbers; two crossing write-offs can deadlock (Postgres aborts one, likely a 500; no corruption). Lock all rows in one ordered query or lock the group row first.
- minor (new): `docker-compose.yml:69` falls back to a publicly known dev secret when `.env` sets none; a non-member holding an invite id could forge that token. Consider refusing the dev value outside a dev profile.
- minor (deferred by human): exact-email search has no rate limit (`UserRepository.java:138-153`).
- minor (deferred by human): jqwik 1.9.3 is EPL-2.0 (test scope, `app/pom.xml:123-128`); needs a recorded exception.
- plan.md risk line about the plaintext token in the idempotency record is stale.

## Verdict
pass: no blocking IDs. Four minor security items remain open for follow-up.
