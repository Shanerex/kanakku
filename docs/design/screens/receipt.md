# Receipt scan and item assignment (F-17, F-18; rows 19, 20) — outline

Mock: receipt frames in [mobile-core](../mocks/mobile-core.html).

## Capture

- Add sheet → "Scan receipt". Mobile: CameraView (receipt guide frame, auto-crop preview,
  Retake / Use photo; gallery and PDF options). Web: FileDrop accepting JPG, PNG, HEIC, PDF
  (≤ 10 MB) plus the device camera via `capture` on phones.
- Caption under the shutter: "Location data is removed before we store the photo."
- Upload progress, then AIBusy "Reading your receipt…" (5–15 s). The user can leave; the
  draft lands in the queue and a notification says "Your receipt is ready to review".

## Itemized review (full-screen on phones, two-pane on desktop: image left, items right)

- Receipt image with pinch-zoom; tapping an item highlights its region when the model returns
  coordinates.
- Header: merchant, date, total (all editable, ConfidenceMarkers as needed).
- **Items table**: name, qty, price, line total; each editable; add/remove rows.
- **Charges**: CGST, SGST, service charge, discount, round-off — each a row.
- **Totals check** strip: "Items and charges = ₹3,482.00 · Receipt total ₹3,482.00 ✓"; on
  mismatch a `draft` strip "₹40.00 short of the receipt total. Check the items." It flags; it
  never adjusts a number itself.

## Assign items (row 20)

```
┌ Who had what? ─────────────────────────────────┐
│ [ Priya and I shared the biryani, Arun had the │
│   drinks, rest equally                     ✨ ] │
│ Chicken biryani ×2   ₹640.00  (P)(PR)          │
│ Kingfisher ×3        ₹540.00  (A)              │
│ Paneer tikka         ₹320.00  Everyone         │
│ Gulab jamun          ₹120.00  ⚠ Unassigned     │
│ Tax + service (spread by items)    ₹362.00     │
│ Per person: You ₹812.40 · Priya ₹812.40 · …     │
│ [ Confirm and add ₹3,482.00 ]  (disabled: 1    │
│   item unassigned)                              │
└────────────────────────────────────────────────┘
```

- Plain-English box at top runs assignment; result fills each item's avatar chips, which stay
  tappable to change (opens MemberPicker per item; "Everyone present" option).
- Unassigned items are listed first with a `draft` warning; Confirm stays disabled until none
  remain.
- Taxes and service charge are spread in proportion to each person's items (shown as one
  line with an info tip). The per-person summary sums exactly to the receipt total.
- Confirm creates one itemized expense; its detail shows the items and per-person shares.
