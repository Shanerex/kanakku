# Activity, notifications and trash (F-08, F-11; rows 10, 13)

## Activity — `/activity` · mobile: Activity tab

One feed of changes across your groups, newest first, day-grouped. Segmented "For you |
Everything": **For you** (default) shows notifications: things that involve you (added to an
expense, paid or were paid, drafts awaiting you, reminders, recurring posts); **Everything**
shows all group changes. Group filter chip.

ActivityRow per event: actor Avatar with action glyph, sentence, caption "Goa Trip · 2 h",
trailing Amount when it moves money for you ("you owe ₹300.00", coloured). Unread items have a
6 px `primary` dot and `primary-soft` 40% background until seen; "Mark all as read" ghost
button in the TopBar.

Actors are always named, with how:
- "Priya added *Dinner at Thalassa*"
- "Priya confirmed a draft from a receipt: *Thalassa bill*" (Sparkle glyph)
- "*Laptop* token drafted *Cab to airport* for Priya" (Key glyph)
- "Recurring: *Rent, Nov* was added" (Repeat glyph, actor is the group badge)
- "Arun deleted *Snacks* · Restore" (inline Restore while in trash, if permitted)
- "Arun undid an edit to *Cab*"

Tapping opens the object (expense detail, settlement, draft). Deleted objects open their
Trash entry.

Web: the sidebar bell opens a 380 px popover with the latest 10 "For you" items and "See all";
mobile: the Activity tab badge counts unread.

## Notification preferences

In Me → Notifications ([me.md](me.md#notifications)).

## Trash — `/groups/:id/settings/trash`

List of deleted expenses: ExpenseRow (muted) with "Deleted by Arun · 3 days ago · 27 days
left". Actions: Restore (secondary; restores exact splits and balances; toast "Restored ·
View"). No permanent delete control: items expire after 30 days. EmptyState "Trash is empty".

## API

`GET /api/v1/activity?scope=mine|all&group&cursor`, `POST /api/v1/activity/read`,
`GET /api/v1/groups/:id/trash`.
