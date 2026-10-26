# Me (F-02, F-11; rows 2, 3, 13)

`/me` · mobile: Me tab. A list of setting rows grouped into Cards; each opens a sub-page
(desktop: two-pane, list left, detail right).

Header: Avatar xl (identity colour), display name, email, "Edit profile".

> v1 (accounts-password): the list shows only the **Profile** row, then **Sign out**. The other
> rows below arrive with their own features.

## Profile — `/me/profile`

Display name, Email (change requires re-verification: "We'll send a link to the new
address"), Default currency, UPI ID (with "Test link" that opens a ₹1 `upi://` preview on
phones; never sends), Locale (English for now; listed so the setting exists).

> v1: Email is **read-only** ("Your email can't be changed yet."); the **Test link** and
> **Locale** rows are left out. Saving sends the version the form started from, and a 409 shows
> "Your profile was changed somewhere else. Reload it and try again." with a "Load the latest
> version" button.

### Mobile number (F-27) — a Card under the profile form

Heading "Mobile number". Friends find you by it, and a verified number lets Kanakku suggest
the guest seat you may already have in a group.

States, one at a time:
- **None**: body "Add your number so friends can find you." TextField "Mobile number" (helper
  "Starts with +91 unless you type another country code.") and secondary "Save number".
- **Saved, not verified**: the number as typed back in E.164 ("+91 98765 43210"), a
  `info-soft` chip "Unverified" with a `WarningCircle` icon. With codes on: secondary "Send
  code" and ghost "Change number". With codes off: the caption "This server can't send texts, so
  your number stays unverified." and "Change number" only.
- **Code sent**: "We texted a 6-digit code to +91 98765 43210." One-time-code field (as at
  sign-up), primary "Confirm number", ghost "Resend code" (30 s cooldown), ghost "Change
  number".
- **Verified**: `positive-soft` chip "Verified" with a `CheckCircle` icon and ghost "Change
  number". Changing it starts again from the form; the new number is unverified.

Errors: invalid ("Enter a mobile number like 98765 43210."), taken ("That number is verified on
another account."), wrong / expired / locked codes and too many texts use the same words as the
sign-up code step; a stale version shows the profile's "Load the latest version" button.

Saving the number sends the profile's current `version` and does not discard unsaved edits in
the profile form above.

API: `PUT /me/phone`, `POST /me/phone/verification` (Idempotency-Key),
`POST /me/phone/verification/confirm`.

## Appearance — `/me/appearance`

Theme radio cards with mini previews: System (default) / Light / Dark. Number format radio:
"Indian (1,23,456)" / "International (123,456)" (defaults from locale). Device-local.

## Notifications — `/me/notifications`

Matrix of events × channels with Switches. Rows: Added to an expense; Settlement recorded;
Draft awaits review; Recurring expense posted; Weekly balance reminder; Someone reminded you.
Columns: In-app (always on, shown disabled for clarity), Email, Push. Push column shows "Turn
on push on this device" when permission isn't granted. Weekly reminder day Select (default
Sunday).

## Security — `/me/security`

- **Password**: Change password (current + new + strength), or "Set a password" if the account
  only uses providers.
- **Linked accounts**: rows for Google, Microsoft, Apple (and custom OIDC) with Linked /
  Link buttons; unlinking the last sign-in method is refused ("Add a password or another
  account first"). Apple private relay emails display as-is with a caption "Apple private
  relay address".
- **Sessions**: list of web sessions and mobile devices (device name, platform icon, last
  active, "This device" chip), Sign out per row, "Sign out everywhere else" (ConfirmDialog).

## Later items in this screen

- **API tokens** (F-20) → [api-tokens.md](api-tokens.md).
- **Statements** (F-22) → [statements.md](statements.md).
- **Admin** (instance admins only) → [admin.md](admin.md).

Footer: "Sign out" (secondary, full width), app version and "Self-hosted at kanakku.example.com"
caption, links to the source code and licence.
