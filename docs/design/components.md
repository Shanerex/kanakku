# Components

Every screen is built from these. Web names are React components in `web/src/ui/`; mobile
names are composables in `mobile/shared/.../ui/`. Same name, same props in spirit, same look.
Visual reference: [mocks/components.html](mocks/components.html).

Shared rules for all components: tokens only (no raw values), visible `:focus-visible` ring,
≥ 44 px hit area, disabled state at 40% opacity and not focusable, loading state that keeps
the component's size, and labels that come from the message catalog.

## Actions

**Button** — `variant`: `primary` (violet fill, white text), `secondary` (surface fill,
`border-strong` edge, `text`), `ghost` (no fill, `primary-text`), `danger` (`danger` fill,
white text), `positive` (`positive` fill, white text; only for "Confirm draft" and "Mark as
paid"). `size`: `sm` 32, `md` 44, `lg` 52. Radius `md`, or `pill` for the hero CTA on a
screen. Optional leading icon. `loading` shows a spinner in place of the icon and keeps the
label. One primary button per view.

**IconButton** — 44 px circle, `ghost` by default, required `label` (tooltip on web,
`contentDescription` on mobile).

**AddButton** — the one way in to adding anything. Web ≥ 768 px: primary pill "＋ Add" in
the top bar. Below 768 px and on mobile: a 56 px violet circle centred in the bottom bar,
raised 12 px above it, `shadow.fab`, plus icon that rotates 45° when the Add sheet is open.
Opens the Add sheet ([patterns.md](patterns.md#add-sheet)). Keyboard shortcut `n` on web.

**Menu** — overflow `DotsThree` icon button opening a `surface-raised` popover (web,
Radix DropdownMenu) or a bottom sheet list (mobile). Destructive items last, in `danger-text`,
after a divider.

## Inputs

**TextField / TextArea** — label above (always visible, never placeholder-only), 44 px
input, `surface-sunken` fill, `border` edge that turns `primary` on focus, helper text below
in `text-muted`, error text below in `danger-text` with a `WarningCircle` icon, and
`aria-describedby` wiring both.

**MoneyInput** — the amount field. Big variant (`amount-lg`, centred, currency chip at left)
for the expense and settlement forms; compact variant for split rows. Accepts digits and one
decimal separator up to the currency's minor-unit digits; rejects more. Parses straight to
minor units as a string-to-long conversion, never through a float. Indian grouping is applied
on blur, not while typing. The currency chip opens a currency picker (recent currencies first,
group currency pinned). `inputmode="decimal"` on web; decimal keyboard on mobile.

**Select / Combobox** — Radix Select on web; bottom-sheet list on mobile. Searchable when
more than 7 options.

**MemberPicker** — multi- or single-select list of group members: avatar, name, "you" tag,
guest tag. Has "Everyone" and "Clear" shortcuts at the top for multi-select. Search by name and
nickname. Used for payer, participants, settle-up parties and AI name disambiguation (where it
opens with the ambiguous phrase quoted: "Who is 'Ar'?").

**DateField** — native date input styled as a TextField on web; Compose date picker on
mobile. Quick chips beneath: Today, Yesterday, pick a date.

**SearchField** — `MagnifyingGlass` leading icon, clear button, `/` shortcut on web.

**Checkbox, Radio, Switch** — 20 px visuals inside 44 px hit areas; Switch is used only for
settings that apply immediately.

**SegmentedControl** — 2–4 options in a `surface-sunken` track, selected segment
`surface` with `shadow.sm`. Used for split type (Equal, Exact, %, Shares) and chart period.

**Chip** — pill, caption type. `filter` (toggle, `primary-soft` when on with a check),
`choice` (single select), `info` (static). Special chips: **DraftChip** (`draft-soft`,
`draft-text`, `Sparkle` icon, "Draft"), **GuestChip**, **YouChip**, **OfflineChip**.

**Stepper** — − / value / + for shares; 44 px buttons; value editable by typing.

**FileDrop** — dashed `border-strong` tile, `UploadSimple` icon, "Drop a file or browse",
accepted types and size limit in helper text. On mobile it becomes two buttons: Camera and
Choose file.

**SecretField** — password input used only for statement PDF passwords and sign-in. For
statements it shows the helper "Used once to open this file. Never saved." with a `LockKey`
icon, and has `autocomplete="off"`.

## Display

**Amount** — renders money from `{ minor: long, currency }`. Props: `size`
(`xl|lg|md|sm`), `tone` (`neutral|positive|negative|muted`), `showSign`, `compact` (hide
.00, hero only). Always tabular. Always has a worded accessible label. The only component
allowed to format money.

**BalanceSummary** — hero card on Home and in a group header. Big `Amount` (`xl`) of the net
balance with words above ("You are owed" / "You owe" / "All settled up"), then a two-column
breakdown "you owe ₹x · you are owed ₹y" when both are non-zero, then the primary action
("Settle up") and a secondary ghost action ("Explain", when F-21 is on). Multi-currency:
the net in the user's default currency plus a "+2 currencies" chip that expands per-currency
rows.

**Avatar / AvatarStack / GroupBadge / CategoryBadge** — see
[design-system.md](design-system.md#identity-groups-and-people). All accept `size`
(`xs|sm|md|lg|xl`).

**ListRow** — the workhorse. Leading slot (badge or avatar, 36–48 px), primary line (body
600), secondary line (body-sm, `text-muted`), trailing slot (Amount stack or chevron),
min-height 64 px, full-row tap target, 16 px horizontal padding. Variants:

- **ExpenseRow** — leading CategoryBadge; primary: description; secondary: "Priya paid
  ₹1,200.00" + date chip if not today's section; trailing: top line your effect ("you lent" /
  "you borrowed" + Amount `sm`, coloured) and bottom line nothing. A receipt icon if a receipt
  is attached; a `Repeat` icon if from a recurring rule; a DraftChip and dashed border if
  draft.
- **BalanceRow** — leading Avatar; primary: name; secondary: "owes you" / "you owe";
  trailing: Amount `md` coloured, and a "Settle" small secondary button on ≥ 768 px.
- **SettlementRow** — leading `HandCoins` badge in mint; "Arun paid Priya"; method chip
  (UPI, Cash, Other); Amount neutral.
- **GroupRow** — leading GroupBadge; primary: group name; secondary: member AvatarStack +
  "4 people"; trailing: your balance in that group (coloured, worded small caption above).
- **ActivityRow** — leading actor Avatar with a tiny action icon at bottom-right (plus,
  pencil, trash, undo, sparkle for AI, key for API token); text "Priya added *Dinner at
  Thalassa*" with the object in 600 weight; caption with time and group; trailing Amount when
  relevant.
- **MemberRow** — Avatar, name, role chip (Owner), guest chip, balance, overflow menu.

**SectionHeader** — h3 or caption-style label with optional trailing link ("See all").
Expense lists group by day: "Today", "Yesterday", "Sat, 18 Oct".

**Card** — `surface`, `radius.lg`, `shadow.sm`, 16–20 px padding. **IdentityCard** is a Card
with a 4 px top bar in the group's `solid` colour and the group tint behind its header.

**Tabs** — underline tabs on web (`primary` 3 px indicator, pill-rounded ends); on mobile
inside a group, scrollable top tabs with the same indicator. Arrow-key navigable.

**Skeleton** — `skeleton` blocks matching the final layout's shape (rows, avatars, amounts),
gentle 1.2 s shimmer, none under reduced motion. Shown after 300 ms of waiting, never as a
flash.

**EmptyState** — illustration (120–160 px), h2 headline in Baloo 2, one-line body, one
primary action. Content per screen in [patterns.md](patterns.md#empty-states).

**QRCode** — UPI QR, black modules on a white rounded tile even in dark mode (scanners need
it), 200 px on mobile and 240 px on web, payee name and amount printed beneath.

**Chart** — Recharts on web, a small Compose canvas chart on mobile. Two kinds only:
**CategoryDonut** (category solids, centre label total, legend with Amounts) and
**MonthBars** (rounded bars in `primary`, the current month highlighted, others
`primary-soft`). Every chart has a table toggle ("Show as table") for screen readers and
exact values.

## AI and drafts

**DraftCard** — a Card with a 1.5 px dashed `draft` border and a `draft-soft` header strip:
`Sparkle` icon, DraftChip, source label ("From your message", "From a receipt", "From the
Goa chat, 14 Oct", "From statement row", "From Claude via token *Laptop*"), and age
("expires in 12 days"). Body is the same layout as the confirmed expense or settlement,
with **ConfidenceMarkers** on uncertain fields. Footer: `positive` "Confirm" (or "Confirm and
add"), secondary "Edit", ghost "Discard". Confirm is disabled until every required field is
filled and every ambiguity resolved; a caption says what is missing.

**ConfidenceMarker** — a field-level tag: `draft-text` dotted underline beneath the field
value and a small `Question` icon; tapping it shows why ("Amount not found in the message",
"Two members match 'Ar'"). Missing values show as a `draft-soft` empty slot reading
"Add amount", never a guessed value.

**SourceQuote** — the evidence the AI used: a `surface-sunken` block with a 3 px `draft` left
bar, quoted text in body-sm, sender and time for chat lines, a receipt thumbnail for scans,
the statement row's date / amount / counterparty / UTR for statements. Expandable when long.
Quoted text is rendered as plain text, never as HTML or Markdown (it is untrusted).

**AIBusy** — a calm in-place state while a model works: three identity-coloured dots that
pulse in sequence, caption "Reading your receipt…" and a Cancel button. Never blocks the
rest of the app; long jobs (chat import, statements) move to a background job row instead.

## Feedback

**Toast** — bottom-centre (above the bottom bar on mobile, bottom-left on desktop), `text`
on `surface-raised`, `shadow.lg`, 4 s, one at a time, with an optional single action. Every
destructive or posting action gets an **Undo** toast ("Expense deleted · Undo", "Settlement
recorded · Undo") that calls the API's undo (F-08). Announced via a polite live region.

**Banner** — full-width strip under the top bar: `info` (offline, "Showing saved data"),
`draft` ("3 drafts need your review" → link), `danger` (blocking errors). Dismissible unless
blocking.

**InlineProblem** — renders an RFC 9457 problem in place: icon, title in words, detail, and
field errors mapped onto fields. Copy per problem type in [patterns.md](patterns.md#errors).

**ConfirmDialog** — for irreversible or money-moving actions only (remove member, revoke
link, write off balance, delete group). Title states the action and object; body states the
consequence with amounts; buttons "Cancel" (secondary) and the verb ("Remove Arun") in
`danger`. Deleting an expense is not one of these: it goes to Trash with Undo instead.

## Navigation

**Sidebar** (web ≥ 768 px, 264 px) — logo wordmark "kanakku" in Baloo 2 800 violet at top;
items Home, Drafts (count badge), Activity, Search; section "Groups" with GroupBadge + name +
your balance dot (positive / negative / none), "＋ New group"; bottom: Me (avatar + name),
notifications bell with count. Selected item: `primary-soft` pill, `primary-text` label,
Fill icon.

**TopBar** — 64 px. Web: page title (h1) left, SearchField centre on ≥ 1024 px, AddButton
and bell right. Mobile: back arrow or screen title left, contextual icon buttons right.
Inside a group the TopBar takes the group tint and shows GroupBadge + name.

**BottomBar** (< 768 px and mobile apps) — 64 px + safe area. Home, Groups, [Add FAB],
Activity, Me. Fill icon and `primary-text` caption for the selected tab; badges for drafts
(on Home) and unread activity. Hidden while the keyboard is open and inside full-screen flows
(camera, item assignment).

**Wizard / ProgressSteps** — for multi-step imports (Splitwise, chat, statements): numbered
dots with labels at top, Back and Next in a sticky footer, step state kept if the user leaves
and returns within the session.

## Mobile-only

**SyncBadge** — on rows created offline: `CloudArrowUp` icon + "Waiting to sync" caption in
`info-text`; becomes "Synced" for 2 s then disappears; becomes "Needs attention" in
`danger-text` on a conflict (opens the conflict sheet, [screens/mobile.md](screens/mobile.md)).

**CameraView** — full-screen camera with a receipt-shaped guide frame, shutter, flash toggle,
"Choose from gallery" and "PDF" options; auto-crop preview before upload.

**UpiAppPicker** — on Android, the system intent chooser; on iOS, a sheet of installed UPI
apps detected via URL schemes (GPay, PhonePe, Paytm, BHIM), each a 56 px tile; QR and "Copy
UPI ID" always offered beneath.
