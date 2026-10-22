# Add sheet, quick add and drafts (F-15, F-16; rows 17, 18)

The Add sheet's structure is in [patterns.md](../patterns.md#add-sheet) and ships in v0.1 with
manual tiles only. Mocks: [web-add-sheet](../mocks/web-add-sheet.html),
[web-drafts](../mocks/web-drafts.html).

## Quick add (row 18)

1. **Type**: the text box ("Say it like you would…") with three rotating example
   placeholders: "1200 cab yesterday, everyone except Arun, I paid", "Priya paid 3400 for the
   stay, split 4 ways", "Dinner 2,350 — me and Meera". Code-mixed input is fine; no language
   picker.
2. **Draft it**: AIBusy in place ("Reading that…"), Cancel available. Typical < 3 s.
3. **Review** (the sheet becomes a DraftCard):

```
┌ ✨ Draft · From your message                  ✕ ┐
│ "1200 cab yesterday, everyone except Arun,      │
│  I paid"                                        │
│ 🚕 Cab                         ₹1,200.00        │
│ Yesterday, 20 Oct · You paid                    │
│ Split equally · 4 people           ₹300.00 each │
│  (P) You  (PR) Priya  (M) Meera  (K) Karthik    │
│ ┌ Who is "KK"? ───────────────────────────────┐ │
│ │ (K) Karthik   (KR) Kiran R.                 │ │  ConfidenceMarker → MemberPicker
│ └─────────────────────────────────────────────┘ │
│ [ Discard ]   [ Edit ]   [ ✓ Confirm and add ]  │
└─────────────────────────────────────────────────┘
```

- Fields the model was unsure of carry ConfidenceMarkers. A missing amount shows the empty
  "Add amount" slot and Confirm stays disabled ("Add the amount to continue"); the model never
  guesses one.
- **Ambiguous name**: an inline MemberPicker card "Who is 'KK'?" listing candidates; never an
  option to create a member from AI output. Unknown name: the picker opens with all members
  and the caption "No one called 'Ravi' in Goa Trip".
- Confirm posts through the same expense service (Undo toast). Edit opens the full form
  prefilled, still marked as from a draft. Discard → Undo toast.
- Keyboard: `Enter` confirms when valid, `E` edits, `Esc` closes (draft stays in the queue).
- Closing the sheet without acting keeps the draft in the queue (toast "Saved to drafts").

## Drafts queue — `/drafts` · mobile: Home → drafts banner

- Filter chips: group, source (Message, Receipt, Chat, Statement, Token), "Needs info" (has
  missing fields or ambiguity). Sort: newest / expiring soon.
- Each item is a compact DraftCard (source icon, description, amount or "Amount missing",
  group badge, age, ConfidenceMarker count "2 things to check"), with inline Confirm when
  nothing needs info, otherwise "Review".
- **Bulk confirm** for chat and statement imports: select drafts with checkboxes, "Confirm 6"
  (disabled for any that need info; caption counts them).
- Desktop: list left, selected draft's full DraftCard right (master-detail); `j` / `k` move,
  `Enter` confirms, `e` edits, `x` discards.
- Expired filter shows drafts expired in the last 7 days, greyed, with "Restore draft".
- Empty: "No drafts to review".

## Draft settlements

Same DraftCard with the settlement layout ("Arun → You ₹1,200.00 · UPI · UTR 4321…"),
created by statement matching (F-23), MCP (F-20) or a guest's "Yes, I paid". Confirming
records the settlement.

## API

`POST /api/v1/groups/:id/drafts/quick-add` (Idempotency-Key) → draft;
`GET /api/v1/drafts?group&source&state`; `POST /api/v1/drafts/:did/confirm` (Idempotency-Key,
version); `PATCH /api/v1/drafts/:did`; `POST /api/v1/drafts/:did/discard`.
