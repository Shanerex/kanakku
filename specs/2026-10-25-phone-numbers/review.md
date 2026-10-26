---
status: final
---
# Review: 2026-10-25-phone-numbers

Round 3, after fix loop 2. S-1 accepted by the human (R-7 partial search kept); the 422 for numbers outside the calling-code allow-list accepted by the human. S-2..S-11 fixed.

## QA
Ran `./mvnw -B clean test`: 516 tests, 0 failures; ModularityTest 4/4 green. Web: tsc clean, Vitest 182 passed. No double or float in the diff.
- AC-1..AC-23 pass, each backed by a test QA read; AC-3, AC-5, AC-19, AC-22 re-checked, no regression.
- New limits tested: PUT /me/phone 10/user and 30/IP an hour (PhoneProfileTest:215-259), phone-taken oracle limits (PhoneProfileTest:232, PhoneSignUpTest:294), SmsPolicy buckets (PhoneSmsPolicyTest:106,142), webhook http rule (WebhookSmsGatewayTest).
- AC-17: jqwik zero-sum per currency holds; STALE path (V14) tested.
- OpenAPI and schema.d.ts: no unrelated break. Only GET user-search removed (POST replaces it, web updated); Profile.phoneVerified and UserMatch.directAdd/matchedBy newly required.
- Web handles 429 on PUT /me/phone: alert with wait time, form stays usable.
- Non-blocking: no Vitest case for a 429 on save; the copy says "codes" for a number-save limit.
- Non-blocking: no Playwright spec for the phone flows (AC-22 allows Vitest).

## Security
Scope: uncommitted tree vs develop (51aec21).
- S-8 fixed: sign-up sends capped at ceil(N/2) of the ceiling; a refusal rolls back all counters.
- S-9 fixed: shared per-number lock recorded as an accepted trade-off (plan.md:87).
- S-10 fixed: PUT /me/phone 10/user and 30/IP, sign-up 30/IP before Argon2; taken, not-taken and replay give the same 429.
- S-11 fixed: odd URL forms (decimal, octal, hex IPs, userinfo, trailing dot, brackets) fail closed on JDK 25; redirects off.
- S-12 minor: WebhookSmsGateway.java:70-72: a dotless webhook name goes through OS search domains and could reach a public host over http. Admin-set URL; document in .env.example or refuse non-private results at send time.
- S-13 minor: SmsPolicy.java:119-121: profile sends can use the whole ceiling and starve sign-up. Needs about 20 verified accounts on distinct IPs. Give profile its own share or accept.
- S-14 minor: AuthController.java:185, SignUpService.java:101: with no SMS provider the phone-taken answer is not counted. Count whenever a phone is given.
- S-15 minor: per-IP keys treat an IPv6 /64 as one address, so a /48 holder gets up to 65536 keys; sign-up phone-taken has no instance-wide bound. Add a global cap or accept.
- Clean: no new dependencies; reads scoped in SQL; logs carry no number, address, URL or token; lock order cannot deadlock.

## Verdict
pass: no blocking items. S-12..S-15 and the QA notes are non-blocking minors.
