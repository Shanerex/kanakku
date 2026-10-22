# Mobile apps (F-26, rows 27–28)

Android and iOS share every screen above through Compose Multiplatform with the same tokens
(generated from [tokens.json](../tokens.json) into `KanakkuTheme`). This file covers only what
is mobile-specific. Mock: [mobile-core](../mocks/mobile-core.html).

## Shell

- Bottom bar: Home, Groups, (+) Add, Activity, Me — see
  [components.md](../components.md#navigation). The Add FAB sits in a notch of the bar.
- Edge-to-edge: content draws behind status and navigation bars; TopBar and bottom bar pad
  for safe areas. Group headers extend their tint behind the status bar.
- Back: Android system back and iOS edge swipe pop the stack; with a sheet open, back closes
  the sheet first; a dirty form asks before discarding.
- Haptics: light impact on confirm, record payment and celebration; selection tick on
  segmented controls and steppers.
- Pull to refresh on every list. App resumes refresh Home and the open group silently.

## Native sign-in (row 27)

Sign-in screen as [auth.md](auth.md) with provider buttons opening the system browser
(Custom Tabs / ASWebAuthenticationSession) with PKCE; Apple on iOS uses the native Sign in
with Apple sheet. Email + password also available. After the token exchange, a short
"Signing you in…" state, then Home. On refresh-token reuse (family revoked): sign out cleanly
to the sign-in screen with an info banner "You were signed out for security. Please sign in
again." Unsynced outbox items survive sign-out only if the same account signs back in
(otherwise the user is warned before signing out: "2 expenses haven't synced yet").

## Offline outbox (row 27)

- Adding an expense or settlement offline always works: the row appears immediately in the
  group with a SyncBadge "Waiting to sync" and a 3 px `info` left bar. It never uses the
  draft look: it is the member's own entry, not AI output.
- Group and Home show an `info` Banner "Offline · showing data from 10:42" and balances say
  "Not counting 1 unsynced expense".
- Edit and delete of synced items while offline are queued the same way; AI features need a
  connection (tiles show "Needs internet" and disable).
- On reconnect the outbox drains in creation order; each badge turns to "Synced" for 2 s.
  A server 4xx (e.g. member removed) marks the item "Needs attention" with the reason and
  Fix / Discard.
- **Conflict**: an offline edit to an expense that changed on the server opens the conflict
  sheet ([patterns.md](../patterns.md#edit-conflicts-409)) when the user taps "Needs attention";
  nothing is dropped silently. A Home banner counts items needing attention.
- Me → "Sync" row shows the outbox: pending count, last sync time, "Sync now".

## Camera (row 28)

CameraView from [components.md](../components.md#mobile-only); permission asked on first use
with a pre-prompt card ("Kanakku uses the camera only to scan receipts"). Denied: a card with
"Open settings" and "Choose a photo instead".

## Push (row 28)

Permission pre-prompt as in [patterns.md](../patterns.md#notifications). Tapping a push deep
links to the object. Notification channels on Android: Expenses, Settlements, Drafts,
Reminders (each mapped to the preference matrix). iOS uses one category per type with a
"View" action; no actions that post (no "Confirm draft" from a notification).

## UPI pay (row 28)

- Android: "Pay with UPI" fires an `ACTION_VIEW` intent with the `upi://pay` URI; the system
  chooser lists installed UPI apps. iOS: the UpiAppPicker sheet of detected apps (GPay,
  PhonePe, Paytm, BHIM via their URL schemes); if none, the sheet leads with the QR and Copy
  UPI ID.
- On return to the app, the "Paid?" confirm row is focused (see
  [settle-up.md](settle-up.md#pay-with-upi)).

## Widgets and extras (later, not in v1.0)

Home-screen widget with your net balance; share-sheet target "Add to Kanakku" for receipt
images and chat text. Listed so the design leaves room; not specified yet.
