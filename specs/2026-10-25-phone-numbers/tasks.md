---
status: final
---
# Tasks: 2026-10-25-phone-numbers

## Tasks
- [x] T-1 (AC-1, AC-2): platform.phone named interface: `PhoneNumbers.parse` (libphonenumber, region IN) and `searchDigits`; licence check; unit tests for the three AC-1 forms and invalid input.
- [x] T-2 (AC-6, AC-7, AC-10): platform.ratelimit named interface: V11 rate_limit_hits, `RateLimiter` with advisory lock and retry-after, cleanup job; tests on the injected Clock.
- [x] T-3 (AC-4, AC-23): SMS port: `SmsGateway`, `WebhookSmsGateway`, `NoSmsGateway`, code generator + HMAC with `KANAKKU_SMS_CODE_SECRET` (required only when the webhook is set), GET /auth/sign-up-options; `FakeSmsGateway` in tests, no network.
- [x] T-4 (AC-1, AC-2, AC-3, AC-5): V10 users phone columns + verified-unique index; PUT /me/phone with version check; Profile gains phone and phoneVerified; OpenAPI snapshot.
- [x] T-5 (AC-4, AC-5, AC-6, AC-7): profile codes: POST /me/phone/verification (Idempotency-Key) and /confirm; phone_challenges; lock and resend limits keyed to the user.
- [x] T-6 (AC-5, AC-6, AC-7, AC-19, AC-21): two-step sign-up: pending_sign_ups, 202 from /auth/sign-up with a provider, /sign-ups/{id}/verify and /resend (Idempotency-Key), per-number limits and 10/hour per-IP (/64) send cap, purge job.
- [x] T-7 (AC-19, AC-20): sign-up without a provider (phone optional, unverified) and /sign-ups/{id}/finish-unverified after a failed send.
- [x] T-8 (AC-8, AC-9, AC-10): user search: POST body, phone partial match from 4 digits incl. unverified, invite-only included, `matchedBy`/`maskedPhone`/`directAdd`, `PhoneMask`, 20/min/user limit.
- [x] T-9 (AC-11, AC-12, AC-13): POST /groups/{id}/user-invites (Owner, Idempotency-Key): guest + invite in one tx, no member until claimed; direct-add of invite-only still refused.
- [x] T-10 (AC-14): `UserDirectory.directAddStatus`; addMember returns 409 phone-unverified; a user with no number is added as before.
- [x] T-11 (AC-2, AC-16): V12 guest_phone_e164 + claim_suggestions; addGuest takes an optional phone; `PhoneVerified` event and same-tx listener creating suggestions only in the user's groups.
- [x] T-12 (AC-17): V13 MERGE kind and ledger.api `MemberMerges` (one MERGE entry per currency) with jqwik properties (zero-sum per currency, exact B moved, guest at 0).
- [x] T-13 (AC-16, AC-17): GET /claim-suggestions, confirm (locks both rows, MemberMerges, guest retired with merged_into) and decline, both version-checked.
- [x] T-14 (AC-18): log-capture test across every flow above plus invite links asserting no phone digits; request records hide the number in toString.
- [x] T-15 (AC-19, AC-20, AC-21, AC-22): web sign-up: phone field, PhoneCodeStep, resend, skip and finish-unverified paths; auth.md spec section; Vitest.
- [x] T-16 (AC-3, AC-4, AC-22): web profile: PhoneSection add/replace/verify and Unverified status; me.md spec section; Vitest.
- [x] T-17 (AC-11, AC-15, AC-22): web picker: POST search, masked phone, Invite button, ui/Toast with the "number is not verified" toast, guest phone field; groups.md picker section; Vitest.
- [x] T-18 (AC-16, AC-17): web Groups page: ClaimSuggestionsBanner with Claim / Not me on GroupsPage; groups.md banner section; Vitest for confirm and decline.
