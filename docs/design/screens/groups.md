# Groups (F-03, row 4)

## Groups list — `/groups` · mobile: Groups tab

Search field at top (filters locally by name), then segmented "Active | Archived". GroupRow
list (phone) or IdentityCard grid (≥ 1024, 3 columns). "＋ New group" as a secondary button in
the TopBar and as the last card in the grid. Empty: as Home's empty state.

## New group — `/groups/new` · mobile: sheet

A one-screen form with a live preview card at top that updates as you type (badge, name,
colour band):

1. **Type**: choice chips Trip 🏖️, Home 🏠, Couple 💞, Event 🎉, Other 👥 (sets the default
   emoji and, for Home, suggests recurring rent later).
2. **Name** (required, 1–60 chars), placeholder by type: "Goa, Dec 2026" / "Flat 4B".
3. **Emoji**: the type's default; tap to open an emoji picker (a curated grid of ~60 travel,
   home, food and celebration emoji plus a "more" button using the platform picker).
4. **Colour**: 10 identity swatches as 44 px circles in a radiogroup (each labelled with its
   name, "Mango"), the next unused colour preselected.
5. **Currency**: Select, the user's default preselected.
6. **Cover image** (optional): FileDrop; crop to 3:1; EXIF stripped server-side.
7. **People**: "Add people now" — rows of name + optional email; each becomes a guest member
   who can later claim via invite. "You" is listed first, not removable.

Primary "Create group" (sends `Idempotency-Key`) → the new group's Expenses tab with an
"Invite people" card on top: copy invite link / share sheet.

## Group settings — `/groups/:id/settings` · mobile: group ⋯ → Settings

Sections as Cards, each with a heading:

- **Group**: name, emoji, colour, currency, cover, type. Owner only edits; members see values.
  Currency change is allowed only while there are no expenses (else disabled with caption
  "Currency is fixed once expenses exist").
- **Members** (see below).
- **Invites and links**: invite link (see below) and guest share links (see
  [share-link.md](share-link.md)).
- **Settle-up**: Switch "Simplify debts" with helper "Suggest fewer payments by netting across
  the group. Nobody pays more in total." (F-06).
- **AI** (F-14+): Switch "Use AI in this group" with helper "Quick add, receipt scans and chat
  import. Turn off to keep this group's data away from the AI provider." Owner only.
- **Recurring**: link to the rules list ([recurring.md](recurring.md)).
- **Import and export**: [import-export.md](import-export.md).
- **Trash**: link with count "3 deleted expenses" ([activity.md](activity.md#trash)).
- **Danger zone** (owner): Archive group (ConfirmDialog: "Archive Goa Trip? It becomes
  read-only. Share links stop working."), Delete group (only when every balance is zero and
  typed-name confirmation).
- Members see "Leave group" instead (refused with the amount if they have a balance).

## Members — within settings, and the group header's AvatarStack → Members sheet

MemberRow list: avatar, name, "you", Owner chip, Guest chip, their balance in the group,
overflow menu:
- Owner actions: Make owner / Remove owner role, Remove from group, Write off balance,
  Rename guest, Send invite (guest with email).
- **Remove with balance**: the API refuses with the amount; show the problem dialog "Arun
  still owes ₹1,240.00" with Settle up / Write off (owner) / Cancel. Write off is a
  ConfirmDialog naming who absorbs it: "Write off ₹1,240.00? It will be split equally among the
  other 4 members." and posts as a visible journal entry.
- "＋ Add person": name + optional email → guest member.

## Find people (member picker, F-27) — group page, Owner only

One search field "Find people": a name, an email or a phone number. Digits only (spaces,
dashes and a leading + allowed) is a phone search and needs 4 digits; anything else needs 3
characters. Helper: "Search by name, email or phone number. A part of a number works."

Each result is a row: Avatar, display name, and one masked line for the field that matched,
the email ("a***@example.com") or the number ("+91 ••••• ••10"), never both. The button is:
- **Add** (secondary, sm) for someone who allows direct add.
- **Invite** (secondary, sm) for someone who chose invite-only. It creates a guest named after
  them and a single-use invite, then the row is replaced by "Invite ready for Bala" with the
  link in a read-only field and Copy; they join only when they open the link.

Errors:
- Adding someone whose number on file is not verified is refused. A toast (see Toast in
  components) reads "Can't add Bala: their number is not verified. Send an invite link
  instead." and the row's button switches to **Invite**.
- Searching more than 20 times a minute: InlineProblem "You searched too often. Try again in
  40 seconds."
- No match: "No one found for that search. Add them as a guest instead."

Search sends the query in a POST body so a number never sits in a URL.

## Add a guest — phone (F-27)

The guest form gains "Mobile number (optional)" with the helper "If they sign up later and
verify this number, we'll suggest their seat to them." Owners see it masked on the guest's
member row ("Guest · +91 ••••• ••10"); other members never see it.

## Claim suggestions banner (F-27) — top of `/groups`

One banner card per pending suggestion, above the group list, in `primary-soft` with a
`UserSwitch` icon: "You might be Bala (guest) in Goa Trip." and the caption "Claiming moves
their balance onto your own entry. Nothing changes until you choose." Two buttons: primary sm
**Claim**, ghost sm **Not me**.

- Claim: the row shows a spinner, then a toast "Claimed. Bala (guest)'s balance is now yours."
  and the banner leaves.
- Not me: the banner leaves with the toast "Got it. We won't suggest this seat again."
- 409 (answered elsewhere, guest gone, group archived): InlineProblem in the banner, "This
  suggestion no longer applies." and the banner is removed on the next load.
- Nothing is shown when there are no suggestions.

API: `GET /claim-suggestions`, `POST /claim-suggestions/{id}/confirm` and `/decline`, each with
the suggestion's `version`.

## Invite link

Card: the link in a read-only mono field, Copy button (toast "Link copied"), Share (Web Share
API or system sheet), QR toggle (for people sitting together), expiry Select (1 day, 7 days,
30 days; default 7), "Revoke and make a new link" (ConfirmDialog). Shows "Expires in 6 days"
and "Used by 2 people". The claim flow is in [share-link.md](share-link.md#invite-claim).
