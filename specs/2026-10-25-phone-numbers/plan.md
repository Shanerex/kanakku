---
status: final
---
# Plan: 2026-10-25-phone-numbers

## Approach
- identity owns the number (R-1..R-6, R-13, R-14): E.164 column on users, a webhook SMS port, and a two-step sign-up whose account row is written only on confirm (R-14, AC-21).
- groups owns guest phones, the extended picker (R-7..R-10) and claim suggestions (R-11); it reaches identity only through identity.api and the ledger only through ledger.api.
- Shared bits go to platform behind named interfaces: phone parsing (R-2) and a Postgres rate limiter (R-5, R-6, R-7) that also backs a per-IP SMS cap at sign-up.
- Confirming a suggestion when the user is already a member merges the guest's balance into the user's row with a ledger MERGE entry (AC-17); the journal stays append-only.
- Phones leave identity only masked (R-8, R-12); the user search moves to a POST body so digits never sit in URLs.

## Options considered
- A (chosen, human 2026-10-25): ledger.api `MemberMerges` posts one MERGE entry per currency (-B guest, +B user's member), guest row retired with `merged_into_member_id`. Honest history; one ledger migration.
- B: reuse `WriteOffs.writeOff(guest, [userMember])` (single absorber takes all). No ledger change, but the history reads "write-off" for what is a claim.
- C: rebind the guest row to the user (as F-03 claim) and retire the user's own row, only when that row has no journal lines; else refuse. No ledger change, but most real cases get refused.

## Design
Modules
- identity (dev.kanakku.identity): numbers, codes, pending sign-ups, SMS port. Calls platform.phone, platform.ratelimit, platform.idempotency, platform.problem.
- groups (dev.kanakku.groups): guest phone, user-invites, suggestions. Calls identity.api (UserDirectory, PhoneVerified), ledger.api (Balances, MemberMerges), platform.phone, platform.ratelimit.
- ledger (dev.kanakku.ledger): new ledger.api `MemberMerges.merge(groupId, fromMemberId, intoMemberId, actorUserId)`.
- platform: new named interfaces `platform.phone` (`PhoneNumbers.parse(raw)` -> E.164, default region IN, libphonenumber Apache-2.0; `searchDigits(raw)`) and `platform.ratelimit` (`RateLimiter.check/hit(bucket, key, max, window)` -> retry-after, advisory xact lock per key, `RateLimitCleanupJob`).

Migrations (forward-only, new files, jOOQ regenerated in the same task)
- V10__identity_phone_numbers.sql: users.phone_e164, users.phone_verified_at, unique index on phone_e164 where phone_verified_at is not null (R-4); phone_challenges (user_id pk, phone_e164, code_hash, expires_at); pending_sign_ups (id, email, display_name, password_hash, phone_e164, code_hash, code_expires_at, sms_failed, expires_at).
- V11__platform_rate_limit_hits.sql: rate_limit_hits (bucket, key, at), index (bucket, key, at).
- V12__groups_guest_phone_claim_suggestions.sql: group_members.guest_phone_e164, group_members.merged_into_member_id; claim_suggestions (id, group_id, member_id, user_id, status PENDING|DECLINED|CONFIRMED, version, created_at), unique (member_id, user_id).
- V13__ledger_merge_kind.sql: replace journal_entries_kind_check to add MERGE.

SMS port and codes
- identity/service `SmsGateway` port; identity/repository `WebhookSmsGateway` (POST JSON {to, text}, Bearer `KANAKKU_SMS_WEBHOOK_TOKEN`, 5 s timeout, 2xx = sent, logs status only); `NoSmsGateway` when `KANAKKU_SMS_WEBHOOK_URL` is unset (R-3).
- Codes: SecureRandom 6 digits, stored as HMAC-SHA256(`KANAKKU_SMS_CODE_SECRET`, 32+ chars, required at startup only when the webhook URL is set, no default), constant-time compare, 10 min expiry (R-6).
- Limits via RateLimiter: wrong codes 5 per 15 min keyed `number:<e164>` at sign-up and `user:<id>` on the profile (R-5); sends 4 per hour (first + 3 resends) on the same keys (R-6); sign-up sends 10 per hour per IP, IPv6 bucketed by /64 via ThrottleIp, against SMS pumping.
- Tests: `FakeSmsGateway` (test sources) records codes and can be told to fail; no network (AC-23).

REST under /api/v1 (OpenAPI snapshot openapi/kanakku-v1.json and web/src/api/schema.d.ts updated per task)
- GET /auth/sign-up-options -> {phoneVerification}: public, tells forms whether a provider is set.
- POST /auth/sign-up (changed): adds `phone`; required when a provider is set, optional otherwise. No provider -> 201 Profile as today. Provider -> 202 PendingSignUp {id, maskedPhone, codeExpiresAt, smsSent}; 409 phone-taken / email-taken; 429 limits. Idempotency-Key as today (scope sign-up; replay returns the stored 202, no resend).
- POST /auth/sign-ups/{id}/verify {code} -> 201 Profile with a verified number; Idempotency-Key (scope sign-up-verify). Creates user + credentials and deletes the pending row in one tx; unique-index violation -> 409 and no account (AC-5).
- POST /auth/sign-ups/{id}/resend -> 202; Idempotency-Key (scope sign-up-resend); 429 on the 4th resend.
- POST /auth/sign-ups/{id}/finish-unverified -> 201 Profile, only when sms_failed (AC-20), else 409; Idempotency-Key (scope sign-up-finish).
- PUT /me/phone {phone, version}: edit, optimistic users.version; replaces and resets to unverified (AC-3); 400 invalid, 409 phone-taken or stale.
- POST /me/phone/verification -> 202 {codeExpiresAt}: sends a code; Idempotency-Key (scope phone-code); 409 sms-not-configured; 429 limits.
- POST /me/phone/verification/confirm {code} -> Profile: an action on the caller's own challenge, no version; 422 wrong, 410 expired, 429 locked.
- Profile gains `phone` (own number, E.164, nullable) and `phoneVerified`.
- POST /groups/{groupId}/user-search {q} replaces GET ?q= (no Idempotency-Key: a read). UserMatch gains `matchedBy` (NAME|EMAIL|PHONE), `maskedPhone`, `directAdd`; `maskedEmail` nullable (null on a phone match, AC-9). 429 at 21/min/user (AC-10).
- POST /groups/{groupId}/user-invites {userId, expiryDays} -> 201 CreatedInvite: Owner only; guest named after the user + invite in one tx (R-9); Idempotency-Key (scope user-invite), stored body is the invite only, token recomputed (decision invite-token-hmac).
- POST /groups/{groupId}/guests (changed): optional `phone`; Member gains `guestPhoneMasked` for the Owner only.
- POST /groups/{groupId}/members (changed): 409 phone-unverified (R-10) via `UserDirectory.directAddStatus`.
- GET /claim-suggestions (the caller's pending ones across their groups, with group and guest name); POST /claim-suggestions/{id}/confirm {version} and /decline {version}: edits with the suggestion's optimistic version. Served by groups.

identity.api changes
- `UserDirectory.search` includes invite-only users and phone matches (digits-only query: min 4 digits; else min 3 chars); `directAddStatus(userId)` -> ALLOWED|INVITE_ONLY|PHONE_UNVERIFIED|UNKNOWN; `verifiedPhone(userId)`.
- `PhoneVerified(userId)` event (no number in it), published in the confirm tx; groups handles it with a synchronous `@EventListener` in the same tx (no event registry is on the classpath).
- Masking in identity/util `PhoneMask` ("+91 ••••• ••10"); sign-up and phone request records override toString to hide the number (R-12).

Membership scoping in SQL
- Suggestion insert: guests with guest_phone_e164 = number, user_id null, left_at null, joined to Scope.member(group_id, userId) (AC-16).
- Suggestion list/confirm/decline: claim_suggestions joined to Scope.member(group_id, caller) and user_id = caller.
- Search: guard.owner plus the existing membership-scoped activeUserIds exclusion; user rows are not group data.
- Merge: both member rows locked via activeIdsInOrderLocked (caller-scoped) before MemberMerges.merge.

Money
- MERGE moves each currency's whole balance B, so nothing divides and no rounding happens; no BigDecimal needed.
- jqwik in ledger: merge keeps every group's sum at zero per currency, leaves the guest at 0, adds exactly B to the target, and posts no entry when B = 0.

Web (web/src, own task each; specs in docs/design/screens/auth.md, me.md, groups.md get a phone section first)
- features/auth/SignUpPage.tsx + new PhoneCodeStep.tsx: phone field, code step, resend, "Finish without verifying" only when smsSent is false.
- features/me/ProfilePage.tsx + new PhoneSection.tsx: add/replace, send/confirm, Unverified badge.
- features/groups/MemberPicker.tsx: masked phone, Invite button for INVITE_ONLY, toast on phone-unverified; new ui/Toast.tsx (Radix Toast, tokens); add-guest dialog gets an optional phone field.
- features/groups/GroupsPage.tsx + new ClaimSuggestionsBanner.tsx: banner per pending suggestion ("You might be <guest> in <group>"), Claim / Not me (human 2026-10-25: banner on Groups, not /me).

AI: none in this feature.

## Architecture changes
- Modules: add `platform: idempotency, RFC 9457 problems, clock, phone parsing (libphonenumber), Postgres rate limiter; named interfaces only.`
- Modules: identity adds "phone numbers (E.164), SMS code verification via a webhook port, pending sign-ups"; groups adds "guest phones, claim suggestions".
- Invariants: add "Phone numbers never reach logs or share links; outside the owner's own profile they leave identity masked."
- Ledger: new entry kind MERGE, posted only through ledger.api MemberMerges, for claim suggestions confirmed by an existing member.
- Flows: add "Phone verify: identity -> SMS port (webhook) -> confirm -> PhoneVerified (sync, same tx) -> groups suggestion -> member confirms -> ledger MERGE."
- Dependency: com.googlecode.libphonenumber (Apache-2.0).

## Risks
- SMS pumping via unauthenticated sign-up sends: 10/hour per-IP (/64) cap plus per-number cap; webhook token never logged.
- Per-number lockout lets a stranger block a number's sign-up for 15 min: accepted by R-5; per-IP send cap limits the reach.
- The per-number wrong-code lock and the 4-sends-an-hour cap are shared by sign-up and profile verification, so a third party can lock a victim's number for 15 minutes and use up its 4 sends: accepted trade-off of the per-number caps (S-2, S-9); the per-IP caps bound how many numbers one client can do this to.
- The instance send ceiling is split: sign-up sends use at most the larger half, profile sends keep the rest, and a hit ceiling logs a WARN with no phone data (S-8).
- "Number taken" answers are rate limited (PUT /me/phone: 10 per user and 30 per IP an hour; sign-up: 30 per IP an hour) so they are not a free oracle (S-10).
- The SMS webhook may use plain http only for a loopback/private IP literal or a dotless name; hosts are never resolved (S-11).
- 409 phone-taken at sign-up reveals a registered number: same trade-off as email (decision no-email-verification-v1).
- Pending sign-ups hold an Argon2 hash: deleted on completion and purged at expiry by a db-scheduler job.
- Changing GET user-search to POST breaks the generated client: web is the only consumer; both change in T-8 and T-17.
- No Playwright harness exists yet (no web/e2e): AC-22 is met with Vitest; Playwright flows left to a follow-up.
