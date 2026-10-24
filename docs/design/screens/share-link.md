# Guest share link and invite claim (F-03, F-13; rows 4, 15)

## Guest share link — `/s/:token` (no account)

For the friend who will never install an app. Phone-first; works from a WhatsApp link.
Mock: [web-share-link](../mocks/web-share-link.html).

```
┌───────────────────────────────────┐
│ kanakku                           │
│ 🏖️ Goa Trip  (group tint band)    │
│ Hi Karthik 👋                      │
│ ┌ You owe ───────────────────────┐│
│ │ ₹1,860                         ││
│ │ Priya ₹1,240.00 · Arun ₹620.00 ││
│ └────────────────────────────────┘│
│ Pay Priya ₹1,240.00               │
│ [ Pay with UPI ]   (QR) (Copy ID) │
│ Pay Arun ₹620.00                  │
│ [ Pay with UPI ]                  │
│ Your expenses (7)                 │
│ 🍴 Dinner at Thalassa  your share │
│    14 Oct              ₹600.00    │
│ ...                               │
│ Want to add expenses too?         │
│ [ Join Goa Trip ] (if invite on)  │
└───────────────────────────────────┘
```

- Shows only what this guest is part of: their balance per person, pay buttons, and the
  expenses and settlements that include them (with their share). No other members' balances,
  no other expenses, no member emails.
- Pay buttons follow [settle-up.md](settle-up.md#pay-with-upi), including the "Paid?" step;
  a guest's "Yes, I paid" creates a **draft settlement** for the payee to confirm (a guest is
  not signed in, so it cannot post to the ledger), shown as "Waiting for Priya to confirm".
- Settled: "You're all square with Goa Trip" EmptyState.
- Revoked, expired or archived group: a plain 404 page "This link isn't active" with no group
  name.
- `noindex`, no third-party requests, no analytics.

Owner side (Group settings → Invites and links): per guest, "Share link" row with Copy,
Share, Revoke, and "Opened 2 days ago" caption.

## Invite claim — `/invite/:token`

- Signed out: card with group badge and name, "Priya invited you to Goa Trip", members
  AvatarStack, then sign in / sign up buttons; after auth it continues here.
- Signed in: "Who are you in this group?" — list of unclaimed guest members ("Karthik ·
  part of 7 expenses") plus "I'm not listed, join as new". Choosing a guest keeps every
  expense they were in (stated: "You'll take over Karthik's 7 expenses and balance"). Primary
  "Join Goa Trip" → the group, with a welcome toast.
- Expired or revoked: "This invite has expired. Ask Priya for a new link."
