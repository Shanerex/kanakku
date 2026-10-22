# Home — `/` · mobile: Home tab (rows 4–8)

Answers "what do I owe, and what's owed to me?" first, then gets you into a group.
Mock: [web-home](../mocks/web-home.html), mobile frame in [mobile-core](../mocks/mobile-core.html).

## Layout

Phone (360–767):
```
┌ TopBar: "Hi, Priya 👋"            🔍  🔔 ┐
│ ┌ BalanceSummary ────────────────────┐   │
│ │ You are owed                       │   │
│ │ +₹2,340                            │   │
│ │ you owe ₹1,200 · owed ₹3,540       │   │
│ │ [ Settle up ]        Explain        │   │
│ └────────────────────────────────────┘   │
│ ▸ 3 drafts to review               ›     │  (draft Banner, only if any)
│ People                       See all     │
│  (PR) Priya R.     owes you  +₹1,240     │
│  (A)  Arun         you owe   −₹1,200     │
│  (M)  Meera        owes you  +₹1,100     │
│ Groups                       ＋ New      │
│  🏖️ Goa Trip   ●●●● 5   you're owed ₹2,340│
│  🏠 Flat 4B    ●●● 3     you owe ₹600     │
│  ...                                     │
└ BottomBar: Home Groups (+) Activity Me ──┘
```

Desktop (≥ 1024): sidebar left; content column with BalanceSummary and the "People" card side
by side in a 2-column row, the drafts Banner across, then Groups as a 2-column grid of
IdentityCards (group tint header with badge, name, AvatarStack, your balance, last activity
"Priya added Dinner · 2 h").

## Content

- **Greeting**: "Hi, {first name}" with a wave emoji, h1. On mobile it lives in the TopBar.
- **BalanceSummary**: net across all groups in the user's default currency. Other currencies:
  "+2 currencies" chip expands rows per currency. Words above the amount: "You are owed",
  "You owe", or "All settled up" (with the coins illustration, no amount). "Settle up" opens
  a chooser of groups with non-zero balances (or goes straight to the only one).
- **People**: up to 5 people with the largest absolute net balance with you across groups
  (BalanceRow). Tap → a person sheet listing per-group balances with Settle buttons. "See all"
  → full list. Hidden when everything is settled.
- **Drafts banner**: `draft` Banner "3 drafts to review" → `/drafts`. Only after F-15.
- **Groups**: active groups ordered by last activity; GroupRow (phone) or IdentityCard
  (desktop). Archived groups behind a "Show archived (2)" link at the end.

## States

- Loading: skeleton of the summary card, 3 people rows, 3 group rows.
- No groups: EmptyState "Start your first group" with New group, plus "Got an invite link?
  Open it to join." caption.
- All settled: summary shows the illustration and "All settled up"; People hidden.
- Offline (mobile): summary caption "Updated 2 h ago".

## Actions and API

- `GET /api/v1/me/balances` (net per currency + per-person + per-group) and
  `GET /api/v1/groups?status=active`. One request each; no N+1 per group.
- Add (FAB / top-bar button) opens the Add sheet with the last used group.

## A11y

The summary region is `<section aria-labelledby>` with heading "Your balance". People and
Groups lists are lists with per-row labels like "Goa Trip, 5 people, you are owed 2,340
rupees".
