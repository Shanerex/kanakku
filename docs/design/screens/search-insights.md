# Search and insights (F-10, row 12)

## Search — `/search?q=&group=&member=&category=&from=&to=&min=&max=` · mobile: Search

- Web ≥ 1024: the TopBar SearchField; Enter navigates to `/search`. Below 1024 and on mobile:
  a search icon in the TopBar opens the Search screen with the field focused.
- Filters as chips under the field: Group, Member, Category, Date range, Amount range. Each opens
  a popover (web) or sheet (mobile). Filter state lives in the URL so results are shareable
  among members and survive reload.
- Results: ExpenseRow with the group badge added before the date and matched words highlighted
  (`primary-soft` background, `text` colour, `<mark>`). Grouped by group when "All groups".
- Count line: "24 results · ₹18,340.00 total" (sum in each currency).
- Empty: "No matches" EmptyState with Clear filters.
- Recent searches (device-local, last 5) when the field is empty.

## Insights — group tab and `/insights` (all groups, per user)

Period SegmentedControl: This month / Last 3 months / This year / Custom (and "Whole trip"
for Trip groups).

Cards:
1. **Total spent** (Amount lg) and **Your share** with a month-over-month caption ("↑ 12% vs
   Sep" in `text-muted`; spending up is not "bad", so no red).
2. **By category**: CategoryDonut + legend rows (CategoryBadge, name, Amount, %). Tapping a
   slice or row filters a list of matching expenses below.
3. **By month**: MonthBars, current month highlighted; tap a bar to filter.
4. **By person** (groups only): horizontal bars of who paid how much vs their share, two marks
   per person (paid in `primary`, share in `primary-soft`).

Every chart has "Show as table". Charts use category or primary colours only; never
positive/negative colours.

## API

`GET /api/v1/search`, `GET /api/v1/insights?group&from&to`.
