# Group detail — `/groups/:id` · mobile: Group (rows 5–12)

The group's home. Tabs: **Expenses** (default) · **Balances** · **Activity** · **Insights**
(Insights from row 12). Mock: [web-group](../mocks/web-group.html).

## Header

A group-tinted band (cover image with scrim if set): back arrow (phone), GroupBadge 48–72,
group name (h1 Baloo 2), AvatarStack of members (tap → Members sheet), and the user's
balance in the group as a worded chip ("You're owed ₹2,340" positive / "You owe ₹600"
negative / "Settled" muted). Actions: "Settle up" primary pill, Search icon (scoped to the
group), ⋯ menu (Settings, Invite people, Trip summary, Export). The band collapses into the
TopBar on scroll (phone) leaving badge + name.

Archived: info Banner under the header; Add and Settle up hidden.

## Expenses tab

```
Phone                                     Desktop ≥ 1024
┌ header ────────────────────────────┐   ┌ header ──────────────────────────────────────┐
│ ▸ 2 drafts to review            ›  │   │ ┌ Expenses ─────────────────┐ ┌ Balances ──┐ │
│ [All] [Mine] [Category ▾] [Date ▾] │   │ │ filters                   │ │ You +2,340 │ │
│ Today                              │   │ │ Today                     │ │ Priya +1240│ │
│ 🍴 Dinner at Thalassa    you lent  │   │ │ rows…                     │ │ Arun −1,200│ │
│    Priya paid ₹2,400.00  ₹1,800.00 │   │ │                           │ │ Meera +1100│ │
│ 🚕 Cab to Baga        you borrowed │   │ │                           │ │ [Settle up]│ │
│    Arun paid ₹1,200.00     ₹300.00 │   │ │                           │ │ Simplify ✓ │ │
│ Yesterday                          │   │ └───────────────────────────┘ └────────────┘ │
│ 🤝 Arun paid Priya  UPI  ₹1,000.00 │   └───────────────────────────────────────────────┘
└────────────────────────────────────┘
```

- Drafts Banner for this group at top (F-15+).
- Filter chips: All / Mine (involves me) / Category / Date / Member; active filters show as
  removable chips. Filtering is server-side.
- Day-grouped list of ExpenseRow and SettlementRow, newest first. Recurring occurrences show
  the Repeat icon. Multi-currency rows show the original amount; your effect is in the group
  currency.
- Tap a row → expense detail ([expense.md](expense.md#expense-detail)); on desktop it opens
  in a right-side panel replacing the Balances panel, with a close button.
- Row overflow (desktop hover / mobile long press): Edit, Duplicate, Delete (→ Trash + Undo).
- Totals footer (sticky at bottom of the list on desktop, top of tab on phone): "Total spent
  ₹48,920.00 · your share ₹12,230.00".

Empty: EmptyState "Nothing here yet".

## Balances tab (phone; on desktop also the right panel)

- Your line first, then every member: BalanceRow with net in group currency; per-currency
  sub-rows when mixed. Sum check caption at bottom (muted): "Balances add up to ₹0.00" — a
  small trust signal reflecting the ledger invariant.
- "Settle up" primary → `/groups/:id/settle`.
- "Simplify debts" status line with a link to the setting.
- "Explain" ghost button per row involving you (F-21) → Explain sheet.
- All zero: EmptyState "All settled up" with confetti on first view after the last settlement.

## Activity tab

The group's feed ([activity.md](activity.md)), filtered to this group.

## Insights tab (row 12)

Group-scoped charts from [search-insights.md](search-insights.md#insights): category donut,
month bars, per-person spend. Period SegmentedControl: This trip (all time) / This month /
Custom.

## API

`GET /api/v1/groups/:id`, `GET /api/v1/groups/:id/expenses?cursor&filters`,
`GET /api/v1/groups/:id/balances`. The balances call returns per-member, per-currency nets and
the simplified suggestions when the setting is on.
