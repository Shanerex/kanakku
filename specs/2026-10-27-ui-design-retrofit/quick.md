---
status: final
---
# Quick: 2026-10-27-ui-design-retrofit

## Change
Add the signed-in app shell from docs/design/patterns.md (sidebar at 768 px and wider, bottom bar below, content capped at 960 px). Rebuild the auth, me and groups screens to match their docs/design/mocks/ HTML. Why: the shipped UI does not follow the design.

## Acceptance criteria
- AC-1: Each rebuilt screen has a Playwright screenshot at 360 px and 1280 px next to its mock, the human has reviewed them, and the Vitest, Playwright and lint checks pass.

## Tasks
- [x] T-1 (AC-1): App shell per patterns.md: Sidebar (>= 768 px), BottomBar (< 768 px), 64 px TopBar with the page title as h1, content capped at 960 px; every signed-in route renders inside it. Vitest: shell landmarks, nav links with the current page marked, title from the page.
- [x] T-2 (AC-1): Auth screens (sign in, sign up, code step, welcome) match web-sign-in.html and auth.md: shared Wordmark, 400 px card with the mock's padding and shadow, blob band, v1 omissions kept. Vitest on the structure and states.
- [x] T-3 (AC-1): Me screens per me.md: header with Avatar xl, settings rows grouped in Cards, Sign out, profile page as a sub-page inside the shell. Vitest.
- [x] T-4 (AC-1): Groups screens per groups.md: list with search and Active/Archived control, GroupRow list and 3-column IdentityCard grid at 1024 px, empty state, `/groups/new` with live preview, type chips, colour swatches and emoji; group page and claim banner restyled inside the shell. Vitest.
- [x] T-5 (AC-1): Playwright screenshot run (`e2e/playwright.screens.config.ts`, API stubbed) writes each rebuilt screen at 360 px and 1280 px, plus the mock where one exists, to specs/2026-10-27-ui-design-retrofit/screenshots/. Then Vitest, Playwright and lint pass; human reviews the screenshots.
- [x] T-6 (AC-1): Sidebar "Groups" section per components.md Sidebar and web-home.html (nav.side): a "Groups" label, one link per active group (GroupBadge sm + name, selected state while that group is open), then "New group". Data from the existing `GET /api/v1/groups` through one shared `useGroups` hook (also used by /groups); no new endpoint. Loading, empty and error never break the shell (empty shows just "New group"). Sidebar only (>= 768 px); bottom bar unchanged. Balance dots are not built: ledger-core (backlog row 5) does not exist yet, so there is no balance to show; add them when it lands. Vitest: list, selected state, empty, error; screenshots refreshed.
- [x] T-7 (AC-1): Visual polish pass after the human found the site dull and misaligned versus the mocks: one shared left and right edge for the top bar and page; primary pill "New group" in the top bar; sidebar label spacing and icon states; bottom-bar pill; groups cards as tinted header band over a white body; group page tinted header with blobs, two columns, compact buttons, role chips and coloured balances; new-group swatches with a ring and check; Me full width with a Sign out tile. Vitest updated; screenshots refreshed.
