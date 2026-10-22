# Me (F-02, F-11; rows 2, 3, 13)

`/me` · mobile: Me tab. A list of setting rows grouped into Cards; each opens a sub-page
(desktop: two-pane, list left, detail right).

Header: Avatar xl (identity colour), display name, email, "Edit profile".

## Profile — `/me/profile`

Display name, Email (change requires re-verification: "We'll send a link to the new
address"), Default currency, UPI ID (with "Test link" that opens a ₹1 `upi://` preview on
phones; never sends), Locale (English for now; listed so the setting exists).

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
