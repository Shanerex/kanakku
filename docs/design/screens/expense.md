# Expenses (F-04, F-07, F-08; rows 6, 7, 9, 10)

Mock: [web-expense-form](../mocks/web-expense-form.html).

## Expense form — `/groups/:id/expenses/new`, `/groups/:id/expenses/:eid/edit`

Opens from the Add sheet's "Expense form" tile or from Edit. Phone: full-screen sheet with a
sticky footer. Desktop: 640 px dialog, or a page at the route when opened by URL.

```
┌ New expense in 🏖️ Goa Trip                         ✕ ┐
│              [₹ ▾]  2,400.00                         │  MoneyInput (big)
│  Description  [ Dinner at Thalassa               ]   │
│  Category     [🍴 Food and dining ▾]  Date [Today ▾] │
│  Paid by      [(P) You ▾]           Multiple payers  │
│  Split        [Equal | Exact | % | Shares]           │
│   ☑ (P) You        ₹600.00                           │
│   ☑ (PR) Priya R.  ₹600.00                           │
│   ☑ (A) Arun       ₹600.00                           │
│   ☑ (M) Meera      ₹600.00                           │
│   ☐ (K) Karthik    —                                 │
│   ✓ ₹2,400.00 of ₹2,400.00                           │
│  ▸ More: notes, receipt, exchange rate               │
├──────────────────────────────────────────────────────┤
│  [ Cancel ]                         [ Add expense ]  │
└──────────────────────────────────────────────────────┘
```

Fields:
- **Amount** (MoneyInput big; required; > 0). Currency chip defaults to the group currency.
- **Description** (required, 1–120). Typing suggests a category from local keyword rules
  (cab, uber, ola, auto → Cab; dinner, biryani, swiggy, zomato → Food; rent → Rent); the
  suggestion only fills Category if the user hasn't chosen one. No AI here.
- **Category** (Select with icons) and **Date** (DateField; defaults today in the group's time
  zone; future dates allowed up to 1 year with a caption).
- **Paid by**: single MemberPicker defaulting to you. "Multiple payers" link opens the payer
  editor (row 7).
- **Split** (see split editor).
- **More** (collapsed disclosure): Notes (TextArea, 500), Receipt (FileDrop / camera; image
  preview with remove; row 7), Exchange rate (only when currency ≠ group currency; row 9).

Footer: Cancel, primary "Add expense" / "Save changes". On success: close, Undo toast, the new
row highlights briefly in the list.

### Split editor

SegmentedControl: **Equal**, **Exact**, **%**, **Shares**. Below it, one row per member:
checkbox (included), Avatar, name, and the per-type control; the computed share in minor units
at the right (always shown, so people see the rounding). A live **total line** under the rows.

| Type | Row control | Total line | Valid when |
| --- | --- | --- | --- |
| Equal | checkbox only | "✓ ₹2,400.00 of ₹2,400.00" | ≥ 1 included |
| Exact | compact MoneyInput | "₹2,200.00 of ₹2,400.00 · ₹200.00 left" | assigned = amount |
| % | number input with % suffix, 0–100, up to 2 decimals | "90% of 100% · 10% left" | sum = 100 |
| Shares | Stepper (0–99) | "6 shares · ₹400.00 per share" | ≥ 1 share |

- Rounding remainders go by largest remainder with stable member order; the preview uses the
  same algorithm as the server (shared spec + fixtures). The rows show exact paise, e.g.
  ₹33.34 / ₹33.33 / ₹33.33. A small "Why ₹33.34?" info tip explains "₹100.00 doesn't split
  evenly; the extra paisa goes to the first person in the list."
- **Adjustments** (per-person "+ ₹ / − ₹", row 7): a "＋ Adjust" link on each row reveals a
  signed compact MoneyInput; the rest is split by the chosen type after adjustments.
  Caption: "Arun +₹200.00 for the extra drink".
- Total line colour: `positive-text` with a check when valid, `draft-text` with what's left
  when not (never red while the user is still typing). The primary button disables with that
  caption while invalid.
- Shortcuts above the rows: "Everyone", "Just me and…" (opens picker).
- Screen readers: each row announces name, control value and computed share; the total line
  is a polite live region.

### Payer editor (row 7)

Replaces "Paid by" with rows: MemberPicker + MoneyInput per payer, "＋ Add payer", total line
"₹2,400.00 of ₹2,400.00 paid". Valid when payers sum to the amount.

### Exchange rate (row 9)

Shows "1 EUR = ₹90.78 · ECB rate for 14 Oct" with an Edit link. If the provider fails:
inline `info` notice "No rate available for 14 Oct. Enter it yourself." with a required rate
field (BigDecimal string, up to 6 decimals). Never defaults to 1. Editing an old expense keeps
its locked rate; changing the date asks "Update the rate to 15 Oct's?" (Keep / Update).
Converted preview: "≈ ₹3,812.65".

### Validation copy

"Add an amount", "Add a description", "Pick who paid", "Percentages add up to 90%. They need to
make 100%.", "Exact amounts are ₹200.00 short", "Payers paid ₹2,200.00 of ₹2,400.00".

## Expense detail — `/groups/:id/expenses/:eid`

Phone: full screen. Desktop: right panel in the group view, or a page by URL.

- Header: CategoryBadge (lg), description (h2), Amount lg, date, "Added by Priya · 14 Oct".
  Multi-currency line. Recurring line "Repeats monthly on the 1st · Manage".
- **Paid by** list and **Split** list (Avatar, name, share; your row highlighted with
  `primary-soft`). Adjustments noted.
- Receipt thumbnail → full-screen viewer with pinch-zoom.
- Notes.
- If it came from a draft: a SourceQuote ("From a receipt · confirmed by Priya").
- **History** (F-08): ActivityRows newest first, each expandable to a before → after diff.
  Reversals are described in words ("Edited amount ₹2,400.00 → ₹2,600.00"), never as
  journal jargon.
- Actions: Edit (primary on desktop, TopBar icon on phone), Duplicate, Delete (→ Trash with
  Undo).

Edit conflicts use the sheet in [patterns.md](../patterns.md#edit-conflicts-409).

## API

`POST /api/v1/groups/:id/expenses` (Idempotency-Key), `PUT …/expenses/:eid` (If-Match
version), `DELETE …/expenses/:eid`, `POST …/expenses/:eid/restore`,
`GET …/expenses/:eid/history`, `POST /api/v1/uploads/receipts` (multipart → upload id),
`GET /api/v1/fx/rate?from&to&date`.
