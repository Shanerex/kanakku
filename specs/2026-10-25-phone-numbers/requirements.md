---
status: final
---
# Requirements: 2026-10-25-phone-numbers

## Problem
Indian users find friends by mobile number, not email, and WhatsApp runs on it. Owners can only search by email, and guests cannot be linked to an account by phone. Source: PRD F-27.

## Requirements
- R-1: A user can add, replace or remove one mobile number on their profile; a guest can carry an optional one, which an Owner can set, change or clear.
- R-2: Numbers are parsed with libphonenumber, default country +91, and stored in E.164; codes are texted only to calling codes on the admin allow-list (default 91), and a number outside it is refused for a code (422).
- R-3: A number is verified by an SMS one-time code via an SMS provider port (admin-configured generic HTTP webhook, none bundled); with no provider, numbers stay unverified and the profile shows that.
- R-4: A verified number belongs to exactly one account, at signup and on the profile.
- R-5: Five wrong codes lock verification for 15 minutes, at signup (keyed to the number) and on the profile (keyed to the user).
- R-6: A code is 6 digits, expires after 10 minutes, and can be resent at most 3 times per hour, at signup and on the profile.
- R-7: Only a group Owner can search (F-03); search also matches phone numbers, partial matches and unverified numbers included, from 4 digits, with F-03's cap of 10 results, excluding members, rate limited to 20 searches a minute per user.
- R-8: A search result shows the display name and the matched field masked: the number as country code plus last 2 digits, or the email as in F-03.
- R-9: A found invite-only user (the per-user setting, F-03 R-6; found by number or email) is shown with an Invite button instead of Add; Invite creates a guest (F-03 R-4) and a single-use invite (F-03 R-8) bound to that user, so only they can claim it (F-03 R-9). A user who allows direct add is added at once as a Member (F-03 R-5); direct-add of an invite-only user stays refused.
- R-10: An unverified number never blocks adding a user; a phone match marks the result "Unverified" so the Owner knows the number is unproven. Features that message a number (F-13 outreach) treat an unverified one as unusable and say so.
- R-11: When a user verifies a phone matching a guest's, or a guest is given a number a member has verified, Kanakku suggests that member claim the guest, only in groups the user belongs to; nothing merges until they confirm, then the guest's expenses and balance move onto their member row (F-03 R-10). Changing a guest's number withdraws its pending suggestions. An unverified number never triggers a suggestion.
- R-12: Phone numbers never appear in logs or share links.
- R-13: The sign-up form has an optional phone field; with a provider, a given number leads to a code step where the user may confirm the code or finish without verifying at any time, leaving it unverified; with none it is saved unverified.
- R-14: With a provider and a phone given, signup creates the account only on a confirmed code or on finishing without verifying; a wrong code, an abandoned code step or a number verified elsewhere creates no account.
- R-15: Web screens: sign-up phone field and code step, profile add/verify/status (also for users who skipped), and the member picker with Invite button and toast.

## Acceptance criteria
- AC-1: "98765 43210", "+91 98765 43210" and "09876543210" store as the same E.164 value (R-2).
- AC-2: An invalid number is rejected with an RFC 9457 problem and nothing is stored (R-1, R-2).
- AC-3: Adding a second number replaces the first and resets it to unverified; removing it leaves the profile with no number (R-1).
- AC-4: A correct code verifies a profile number, a wrong code does not; with no provider no code is sent and the profile shows "unverified" (R-3).
- AC-5: A number already verified elsewhere is refused on the profile and at signup, the first account is unchanged and signup creates no account (R-4, R-14).
- AC-6: After 5 wrong codes the 6th attempt is refused even with the right code until 15 minutes pass, on the profile and at signup (R-5).
- AC-7: A correct code is refused after 10 minutes and the 4th resend within an hour is refused, on the profile and at signup (R-6).
- AC-8: A partial number of 4 or more digits returns matching users, verified or not; fewer than 4 digits is rejected with a problem (R-7).
- AC-9: Results are at most 10 and exclude group members; a phone match shows display name and a number like "+91 ••••• ••10", an email match shows display name and masked email, nothing else (R-7, R-8).
- AC-10: The 21st search within a minute by one user is rejected with a 429 problem, and a search by a non-Owner is refused (R-7).
- AC-11: An invite-only user found by number or email shows an Invite button and no Add (R-9).
- AC-12: Invite creates a guest and a single-use invite and adds no member until claimed; another user's claim of it fails and adds nobody; direct-add of an invite-only user is still refused (R-9).
- AC-13: Adding a user who allows direct add makes them a Member at once (R-9).
- AC-14: Direct-adding a user with an unverified number on file makes them a Member; a phone match is marked unverified or verified, other matches carry no phone state (R-10).
- AC-15: A web test shows the "Unverified" mark on a phone match and adds that user with no error (R-10, R-15).
- AC-16: Verifying a number equal to a guest's, or giving a guest a member's verified number, creates a suggestion and changes nothing; an unverified number or a non-member group creates none; changing the guest's number withdraws it (R-11).
- AC-17: Declining leaves the guest unmerged; confirming carries over expenses and balance, and group balances still sum to zero per currency (R-11).
- AC-18: Log lines and share-link URLs from every flow here contain no phone number (R-12).
- AC-19: With or without a provider signup works without a phone; with a provider a given phone completes only after the correct code or finishing without verifying (R-13, R-14).
- AC-20: Finishing without verifying completes signup with the number unverified, whether or not the text was sent (R-13).
- AC-21: A wrong code or an abandoned code step leaves no user row, and the same email can sign up again (R-14).
- AC-22: Vitest or Playwright tests cover the sign-up flow and skip path, the profile add/verify and the picker Invite button (R-15).
- AC-23: Tests use a fake provider through the port, with no network (R-3).
- AC-24: An Owner can set, change or clear a guest's number with a version check; a non-owner cannot; an invalid number is refused (R-1).

## Out of scope
- Bundling any SMS provider or DLT registration; several numbers per user; WhatsApp messaging and guest outreach; silent guest merges.
- Email-triggered claim suggestions (return when email verification is built); OIDC sign-ins (backlog row 3; they use the profile flow).

## Deviations
- R-7 departs from PRD F-27 (Requirements and Acceptance) and the backlog done-when ("search matches only verified numbers exactly"): partial and unverified numbers are searchable, so that done-when is not met by design.
- R-9 departs from groups-members R-7, AC-6 and AC-8: invite-only users now appear in search with an Invite button; their direct-add refusal is unchanged.
- Claim suggestions (R-11) stay verified-only; search is the only relaxed path. Confirming merges into the member's own row (ledger MERGE), unlike an F-03 invite claim, since suggestions only reach members.
- R-9's user-bound invite departs from F-03 R-9 (any signed-in holder may claim); guest invites keep F-03 R-9.
