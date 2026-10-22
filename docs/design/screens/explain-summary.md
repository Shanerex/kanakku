# Explain my balance and trip summary (F-21, row 23) — outline

## Explain — sheet from a BalanceRow or BalanceSummary "Explain"

```
┌ Why you owe Priya ₹2,340.00                   ✕ ┐
│ ✨ You owe Priya mostly for the stay (₹3,400.00) │
│ and two dinners, minus the ₹1,000.00 you paid    │
│ her on 16 Oct.                                   │
│ ─────────────── The numbers ──────────────────── │
│ 🛏️ Stay, 12 Oct   Priya paid · your share +₹3,400.00│
│ 🍴 Dinner, 13 Oct  Priya paid · your share +₹600.00 │
│ 🍴 Lunch, 14 Oct   You paid · Priya's share −₹660.00│
│ 🤝 You paid Priya, 16 Oct              −₹1,000.00   │
│                                   = ₹2,340.00 ✓  │
└──────────────────────────────────────────────────┘
```

- The **trace table** is the source of truth and always shows (with AI off, it's the only
  thing shown, titled "How this adds up"). Its rows sum exactly to the balance with a check.
- The AI paragraph sits above it in a `draft-soft` box with the Sparkle and the caption "Written
  by AI from the numbers below". If the validator rejected the explanation, only the table
  shows (no error to the user).
- Each row links to its expense or settlement.

## Trip summary — `/groups/:id/summary`, public `/t/:token`

A celebratory, shareable page for the end of a trip (also offered when the owner archives a
Trip group):
- Hero in the group's tint: badge, name, dates, "₹48,920 across 63 expenses".
- Cards: per-person spend (bars), category donut, "Biggest expense" (with its emoji category),
  "Most generous payer" (fun but factual: who paid most up front), and the settle-up plan.
- Optional AI one-liner recap (draft-styled caption, F-14 on).
- Share: "Create share link" (owner; revocable; no amounts per person unless the owner ticks
  "Include each person's spend"), Download PDF.
