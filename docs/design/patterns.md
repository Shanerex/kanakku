# Patterns

Behaviour that spans screens. Screen specs link here instead of repeating it.

## Navigation map

```
Signed out:  /sign-in  /sign-up  /verify-email  /forgot-password  /reset-password
             /invite/:token (claim or join)   /s/:token (guest share link, no account)
             /t/:token (public trip summary, F-21)

Signed in (sidebar on web >= 768 px; bottom bar below and on mobile):
  Home  /                        balances overview, drafts banner, groups
  Groups /groups                 list; /groups/new
    Group /groups/:id            tabs: Expenses | Balances | Activity | Insights
      /groups/:id/settle         settle-up plan and record payment
      /groups/:id/expenses/:eid  expense detail and history
      /groups/:id/settings       members, invites, share links, recurring, import/export,
                                 AI on/off, simplify debts, trash, archive
      /groups/:id/summary        trip summary (F-21)
  Drafts /drafts                 review queue across groups (F-15)
  Activity /activity             everything across groups; notifications live here
  Search /search                 full-text + filters (F-10); a TopBar field on desktop
  Me    /me                      profile, appearance, notifications, security, linked
                                 accounts, sessions, API tokens (F-20), statements (F-22)
  Admin /admin                   instance admins only: AI providers, OIDC providers,
                                 budgets, usage (F-14)
```

Mobile tabs: **Home**, **Groups**, **(+) Add**, **Activity**, **Me**. Drafts and Search are
reached from Home (drafts banner, search icon in the TopBar) and from Activity. Every web
route is a deep link; mobile registers the same paths as app links (`https://<host>/…`) and
`kanakku://` for sign-in callbacks.

The Add flow always knows its target group: inside a group it is that group; elsewhere the
sheet opens with a group picker (last used group preselected).

## Add sheet

The single entry for creating anything: expenses, settlements, drafts.

```
┌───────────────────────────────────────────┐
│ Add to  [🏖️ Goa Trip ▾]                ✕  │
│ ┌───────────────────────────────────────┐ │
│ │ Say it like you would…                │ │
│ │ "1200 cab yesterday, everyone except  │ │
│ │  Arun, I paid"                        │ │
│ └───────────────────────────────────────┘ │
│                         [ ✨ Draft it  ↵ ] │
│ ───────────────── or ─────────────────── │
│  [📷 Scan receipt] [💬 Import chat]       │
│  [✎ Expense form]  [🤝 Record payment]    │
└───────────────────────────────────────────┘
```

- Opens as a bottom sheet (mobile, < 768 px) or a 560 px dialog (web). Focus starts in the
  text box. `Enter` drafts; `Shift+Enter` adds a line.
- **Draft it** sends the text to quick add (F-16). The sheet shows AIBusy in place, then
  morphs into the DraftCard for review in the same sheet. Confirm posts and closes with an
  Undo toast; Edit opens the expense form prefilled; Discard returns to the empty text box.
- If AI is off for the group (or the instance has no provider, or the user is over budget),
  the text box and AI tiles are not rendered at all and the sheet opens directly on the
  four manual tiles ("Expense form" first). Over budget shows a one-line info Banner in the
  sheet: "You've used this month's AI allowance. Manual entry still works."
- Before F-16 ships (v0.1), the sheet shows only "Expense form" and "Record payment"; build
  it as the sheet anyway so later features slot in.
- Pasting multi-line text that looks like a WhatsApp export (timestamps on each line) offers
  "This looks like a chat. Import it instead?" which hands off to chat import.

## Drafts: the AI rule made visible

AI proposes, a member posts. The UI must never let a draft look posted.

- Drafts render only as DraftCard / draft ExpenseRow (dashed `draft` border, DraftChip,
  Sparkle). They never count in any balance, total, chart or settle-up plan. Where a total is
  shown next to pending drafts, a caption reads "Not counting 2 drafts".
- Every draft shows its SourceQuote and its origin: quick add, receipt, chat, statement or an
  API token by name. History later shows "Drafted from a receipt · confirmed by Priya".
- Lifecycle: `pending` → `confirmed` (it becomes a normal expense; the row animates from
  dashed to solid) or `discarded` (fades out; Undo toast) or `expired` (after 14 days, shown
  greyed in a "Expired" filter for 7 days, then gone).
- Any member who is part of the draft can confirm. Confirm buttons disable on press and the
  request carries the draft's idempotency key, so a double tap posts once.
- Where they gather: the Drafts screen (all groups), a Banner on Home and in each group
  ("3 drafts to review"), the drafts count badge in the sidebar and on the Home tab.

## Forms

- Labels above fields; required fields are the default, optional ones say "(optional)".
- Validate on blur and on submit, never on every keystroke (except the split editor's
  running total, which is live). On submit with errors: focus the first invalid field and
  show a summary at the top for screen readers.
- **Primary button stays enabled** and explains on press what is missing, except for
  Confirm on drafts and the split editor, which disable with a caption stating why
  ("₹200.00 left to assign").
- Sticky footer on mobile for the primary action, above the keyboard.
- **Idempotency**: the client creates the `Idempotency-Key` (UUID v7) when the form opens,
  keeps it across retries of the same submission, and makes a new one only after success or
  when the user changes the form after a failure. The submit button shows loading and
  ignores further presses until the response.
- Leaving a dirty form asks "Discard this expense?" (Keep editing / Discard).

## Edit conflicts (409)

Edits send the version they started from. On 409 the API returns the current version:

```
┌ Someone changed this while you were editing ─────────────┐
│ Priya edited "Dinner at Thalassa" 2 minutes ago.          │
│                   Theirs           Yours                  │
│ Amount            ₹2,400.00        ₹2,600.00              │
│ Split             Equal, 4         Equal, 5               │
│ [ Keep theirs ]  [ Use mine ]  (re-applies your changes)  │
└──────────────────────────────────────────────────────────┘
```

Only changed fields are listed. "Use mine" resubmits against the new version. The same
sheet serves mobile offline-sync conflicts.

## Undo, trash and history

- Every post, edit, delete and settlement shows an Undo toast for 4 s (plus a "View" action
  for posts when space allows). Undo calls the server; it is not a client-side delay.
- Deleted expenses go to the group's Trash for 30 days (Group settings → Trash) with Restore.
- Expense detail has a History section: each change with actor, how (app, AI draft confirmed
  by, API token name), time and a before → after diff. Edits show as "Edited: amount ₹2,400.00
  → ₹2,600.00".

## Errors

Map RFC 9457 problem `type`s to plain words; never show the type URI, status code or stack.

| Situation | Title | Body / action |
| --- | --- | --- |
| Validation (400/422) | (none: inline on fields) | Field messages, e.g. "Percentages add up to 90%. They need to make 100%." |
| Not signed in (401) | — | Redirect to sign-in, then back to where they were |
| Not a member / not found (403/404) | "This isn't available" | "It may have been removed, or you're not in this group." · Go home |
| Version conflict (409) | Edit conflict sheet | as above |
| Member has balance (409 on remove) | "Arun still owes ₹1,240.00" | "Settle up or write off the balance before removing them." · Settle up / Write off |
| Rate limited (429) | "Too many tries" | "Wait a few minutes and try again." (sign-in shows the wait time) |
| AI over budget | "AI allowance used up" | "Resets on 1 Nov. You can still add expenses by hand." |
| AI provider down | "AI isn't responding" | "Try again, or fill in the form yourself." · Try again / Open form |
| FX rate unavailable | "No exchange rate for 14 Oct" | Inline field: "Enter the rate yourself" (never silently 1:1) |
| Network down (web) | Banner: "You're offline. Changes can't be saved right now." | Disable submit buttons with that caption |
| Server error (5xx) | "Something went wrong on our side" | "Your change wasn't saved. Try again." + request ID in small mono text for bug reports |

## Empty states

Each uses an inline-SVG shape illustration (tokens only) and one action.

| Where | Illustration | Headline | Body | Action |
| --- | --- | --- | --- | --- |
| Home, no groups | Three overlapping blobs (violet, mango, mint) with a suitcase | "Start your first group" | "A trip, a flat, a dinner. Add people and split as you go." | New group |
| Group, no expenses | A paper receipt with a dashed outline | "Nothing here yet" | "Add the first expense, or paste a message and let Kanakku draft it." | Add |
| Balances, all zero | Two coins clinking, mint confetti dots | "All settled up" | "Nobody owes anybody. Nice." | (none) |
| Drafts, empty | A sparkle over an empty tray | "No drafts to review" | "Drafts from quick add, receipts and chats show up here." | (none) |
| Activity, empty | A small clock blob | "No activity yet" | "Changes in your groups will show up here." | (none) |
| Search, no results | Magnifier over a blob | "No matches" | "Try fewer words or clear a filter." | Clear filters |
| Trash, empty | Empty bin with a sparkle | "Trash is empty" | "Deleted expenses stay here for 30 days." | (none) |
| Share link, guest settled | Coins + check | "You're all square with Goa Trip" | "Nothing to pay." | (none) |

## Loading

- Skeletons for lists and cards after 300 ms; spinners only inside buttons.
- Lists page with cursor-based infinite scroll (50 rows), with a "Load more" button fallback
  for keyboard and screen-reader users.
- After a write, update the screen from the response (the balance comes back with the post);
  do not optimistically change balances on the client before the server confirms.

## Offline

- **Web** is online only. When offline: an info Banner, cached screens stay readable if the
  browser has them, and every write button disables with "You're offline".
- **Mobile** is offline-first for adding: see [screens/mobile.md](screens/mobile.md). Offline
  rows carry a SyncBadge; balances show "Updated 2 h ago" and do not include unsynced
  expenses ("Not counting 1 unsynced expense").

## Multi-currency display

- Group currency is the default everywhere. An expense in another currency shows its
  original amount as the primary Amount, and "≈ ₹3,812.65 at 90.78 on 14 Oct" beneath.
- Balances: one row per currency when a member has balances in several, plus the converted
  total in the group currency labelled "≈ total".
- The rate field in the expense form shows the locked rate and its source ("ECB, 14 Oct");
  editing it shows "You set this rate".

## Permissions in the UI

The server enforces everything; the UI only hides what cannot work.

- Owner-only controls (archive, delete group, roles, write off) are absent for members, not
  disabled.
- AI off for a group: no AI tiles, no Explain button, no "Draft it", no chat import, and
  statement matching for that group is not offered.
- Archived groups are read-only: a Banner "Archived on 2 Feb. Unarchive to make changes."
  (owner sees Unarchive), and Add is hidden inside them.
- Guests (no account) only ever see the share link page.

## Notifications

- In-app notifications are Activity items addressed to you, with an unread dot; the bell
  (web) and the Activity tab badge (mobile) count them.
- Web push and mobile push ask permission in context, never at first launch: after the user
  creates or joins their first group, a small card offers "Get a ping when someone adds you to
  an expense" with Turn on / Not now.
- Notification copy: "Priya added you to *Cab to airport* · you owe ₹300.00",
  "Arun paid you ₹1,240.00 · Goa Trip", "2 drafts from your receipt need review".

## Responsive rules

| Width | Layout |
| --- | --- |
| 360–767 | Bottom bar with centre Add FAB; single column; sheets from the bottom; group tabs scroll |
| 768–1023 | Sidebar (collapsible to 72 px icons); single content column; sheets become dialogs |
| ≥ 1024 | Group detail is two columns: expense list (left, flexible) and a 320 px Balances panel (right, sticky); Home shows groups as a 2-column card grid |
| ≥ 1280 | Content capped at 960 px, centred in the space right of the sidebar |
