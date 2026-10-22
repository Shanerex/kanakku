# Settle up (F-06, row 8)

Mock: [web-settle-up](../mocks/web-settle-up.html).

## Settle-up plan — `/groups/:id/settle` · mobile: Settle up

```
┌ Settle up · 🏖️ Goa Trip                         ✕ ┐
│ Suggested payments (simplified)          ⓘ        │
│ ┌──────────────────────────────────────────────┐  │
│ │ (A) Arun  →  (P) You            ₹1,200.00    │  │
│ │ [ Record payment ]                           │  │
│ ├──────────────────────────────────────────────┤  │
│ │ (P) You   →  (PR) Priya         ₹1,240.00    │  │
│ │ [ Pay with UPI ]  [ Record payment ]         │  │
│ ├──────────────────────────────────────────────┤  │
│ │ (M) Meera →  (PR) Priya           ₹100.00    │  │
│ └──────────────────────────────────────────────┘  │
│ 3 payments settle everyone.                       │
│ Paying someone else? [ Record a different payment ]│
└───────────────────────────────────────────────────┘
```

- List of suggested transfers from the balances endpoint: payer → payee, Amount lg neutral.
  With "Simplify debts" on, greedy netting (≤ n−1 transfers); off, pairwise debts. The ⓘ tip
  explains: "Kanakku matches the biggest debts first so you need fewer payments. Nobody pays
  more in total."
- Rows involving you come first and carry actions: **you pay** → "Pay with UPI" (only when the
  payee has a UPI ID) + "Record payment"; **you receive** → "Record payment" + "Remind"
  (sends a gentle nudge notification, rate-limited to once a day per person).
- Rows between others are display-only, with "Record payment" available to any member (e.g.
  the organiser records cash).
- Multi-currency: suggestions per currency, with a currency heading per block.
- All zero: EmptyState "All settled up" + celebration.

## Record payment — sheet

Fields: From (MemberPicker, prefilled), To (prefilled), Amount (MoneyInput big, prefilled
with the suggestion, editable for partial payments), Method chips (UPI, Cash, Bank transfer,
Other), Date (today), Note (optional), UPI reference (optional, shown for UPI: "UTR (12 digits)
helps match your bank statement later"). Primary positive "Record ₹1,240.00". Success:
Undo toast, balances refresh, and celebration if the group or your balance hits zero.

Over-payment (amount > what's owed) is allowed with a caption: "This is ₹200.00 more than
Arun owes. Priya will owe Arun ₹200.00."

## Pay with UPI — sheet

UPI gives no callback, so this is a two-step "pay, then confirm" flow.

```
┌ Pay Priya ₹1,240.00                         ✕ ┐
│ (PR) Priya Raman · priya@okhdfc               │
│ [ Open UPI app ]          ← primary            │
│ ┌──────────┐  Scan with any UPI app            │
│ │ ▓▓ QR ▓▓ │  ₹1,240.00 · "Kanakku Goa Trip"    │
│ └──────────┘                                   │
│ priya@okhdfc  [Copy]                           │
│ ───────────────────────────────────────────── │
│ Paid?  [ Yes, record ₹1,240.00 ]  Not yet      │
└───────────────────────────────────────────────┘
```

- `upi://pay?pa={vpa}&pn={name}&am={rupees.paise}&cu=INR&tn=Kanakku {group}` — amount
  formatted from minor units with exactly two decimals. Only INR balances get UPI.
- Web on phones: "Open UPI app" is the `upi://` link. Web on desktop: hide that button and lead
  with the QR ("Scan with your phone"). Mobile apps: see [mobile.md](mobile.md#upi-pay).
- Always show QR and Copy UPI ID as fallbacks (some apps refuse links to personal VPAs).
- When the user returns to the tab/app after opening the UPI app, the "Paid?" row pulses once
  and takes focus. "Yes, record" posts a UPI settlement (method UPI); "Not yet" closes.
- No UPI ID on the payee: no Pay button anywhere; the row says "Priya hasn't added a UPI ID"
  and offers only Record payment. When the payee is you, the row adds a ghost link "Add your
  UPI ID" to the profile.

## API

`GET /api/v1/groups/:id/balances?simplify=true`, `POST /api/v1/groups/:id/settlements`
(Idempotency-Key), `POST /api/v1/groups/:id/reminders`.
