---
status: final
---
# Requirements: 2026-10-21-accounts-password

## Problem
Kanakku has no identity: nobody can sign up, sign in or own a profile, so no group or expense can belong to a person.
Every user needs a password account first (PRD F-02 part 1); OIDC providers follow in oidc-sign-in (F-02 part 2).

## Requirements
- R-1: A visitor can sign up with an email and password; the email must be unique, compared case-insensitively.
- R-2: Passwords are hashed with Argon2id; the plaintext is never stored or logged.
- R-3: Passwords must be 8 to 128 characters with no composition rules; others are rejected with an RFC 9457 problem detail.
- R-4: Each account records whether its email is verified; password sign-ups are recorded as unverified (see Deviations).
- R-5: A user can sign in with email and password right after sign-up; no verification step gates sign-in.
- R-6: Sign-in failures give the same response for an unknown email and a wrong password.
- R-7: Five failed sign-ins in 10 minutes for the same email and IP pair trigger a rate limit that lasts until the 10-minute window clears; it answers HTTP 429.
- R-8: A successful sign-in starts a session in an HttpOnly, SameSite cookie with a 14-day idle timeout; sign-out revokes it server-side.
- R-9: State-changing web requests carry CSRF protection; a request without a valid token is rejected.
- R-10: A signed-in user can view and edit their own profile: display name, default currency (ISO 4217) and an optional UPI ID.
- R-11: A UPI ID is accepted only if it matches the handle@psp format; there is no live lookup.
- R-12: Profile reads and writes touch only the caller's own account, scoped in the SQL query; edits use an optimistic version check.
- R-13: Sign-up is idempotent by `Idempotency-Key`; endpoints live under `/api/v1` with Bean Validation.
- R-14: The web ships sign-up, sign-in and profile screens in this change, per `docs/design/screens/auth.md` and `docs/design/screens/me.md`.
- R-15: Passwords and session IDs never appear in a log line or API response beyond their intended use.

## Acceptance criteria
- AC-1: Signing up with a valid email and password succeeds, and the same credentials then sign in (R-1, R-5).
- AC-2: Signing up with an email that already exists, in any letter case, is refused and creates no second account (R-1).
- AC-3: The stored password value is an Argon2id hash and differs from the submitted password (R-2).
- AC-4: A 7-character and a 129-character password each get a 400 problem detail and create no account; an 8-character and a 128-character password are accepted (R-3).
- AC-5: A new password account reads back as email-unverified (R-4).
- AC-6: Wrong-password and unknown-email sign-ins return identical status and body (R-6).
- AC-7: After five failed sign-ins in 10 minutes for one email and IP pair, the sixth attempt returns 429 even with the correct password; the same email from another IP is not limited; once the window clears the correct password signs in (R-7).
- AC-8: Sign-in sets a session cookie flagged HttpOnly and SameSite; after sign-out the old cookie is rejected (R-8).
- AC-9: A session unused for 14 days is rejected; one used within 14 days is accepted (R-8).
- AC-10: A POST without a valid CSRF token is rejected; with a valid token it succeeds (R-9).
- AC-11: A user updates display name, default currency and UPI ID and reads them back; an invalid currency code is rejected (R-10).
- AC-12: A UPI ID without the handle@psp shape is rejected with a 400; a well-formed one is accepted; the optional field may be left empty (R-11).
- AC-13: User A cannot read or change user B's profile; a stale version on edit returns a conflict (R-12).
- AC-14: Repeating a sign-up with the same `Idempotency-Key` creates one account and returns the original result (R-13).
- AC-15: A Playwright run completes sign-up, sign-in and a profile edit through the web screens (R-14).
- AC-16: A captured log of the sign-up and sign-in flows contains no password or session ID (R-15).

## Deviations
- Human decision: no email verification in the first version. This deviates from PRD F-02 acceptance ("a user who signs up and verifies can sign in; an unverified one cannot"); it is not met by this feature.
- No verification email, link, expiry or resend, and unverified accounts are not blocked from signing in.
- Row 3 oidc-sign-in ("link by verified email") must treat R-4 password accounts as unverified and must not auto-link them to an OIDC identity by email alone. Adding verification later is a separate change.

## Out of scope
- Google, Microsoft, Apple and admin-added OIDC providers, PKCE, ID-token checks, Apple quirks, linking by verified email (oidc-sign-in, F-02 part 2).
- Email verification flow (see Deviations), password reset, change-password and account deletion.
- Groups, invites and the mobile app.
