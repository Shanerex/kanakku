---
status: final
---
# Review: 2026-10-27-ui-design-retrofit

## QA
- AC-1 screenshots: 24 PNGs in specs/2026-10-27-ui-design-retrofit/screenshots/ cover auth (sign-in, sign-up, welcome), me, me-profile, groups (list, empty, new, detail), each at 360 and 1280 px; mock shots at both widths sit beside sign-in, groups-list and groups-detail (the only screens with a mock). Pass.
- AC-1 freshness: newest code change is web/src/features/groups/GroupPage.tsx at 00:34:43; all screenshots are 00:35-00:36, so they are newer. Human re-approval is logged in state.md (2026-10-28). Pass.
- AC-1 screens run: `playwright test -c playwright.screens.config.ts` run from a scratchpad copy (committed PNGs untouched): 24 passed. Pass.
- AC-1 Vitest: 224/224 on 8 of 9 runs; the first run showed 1 failed test (file not captured) and could not be reproduced in 8 reruns. Flake noted, not blocking.
- AC-1 lint: `tsc --noEmit` clean, `biome ci .` clean (87 files), `./mvnw -B -q spotless:check` clean. Pass.
- T-1..T-7: ticked; AppShell, Card, Chip, SearchField, SegmentedControl, NewGroupPage, useGroups and their tests exist, and T-7 adjustments are covered by the updated GroupPage, GroupsPage and AppShell tests. Pass.
- Tokens only: grep of web/src for hex, rgb(a) and hsl finds hits only in app/tokens.css (the token definitions). Arbitrary px values (max-w-[400px], min-h-[52px], text-[34px], max-w-[560px]) are layout sizes from the mocks, not colours. Pass.
- No app/src/main change: `git status` and `git diff develop...HEAD -- app/src/main` are empty, so no new endpoint, no money code and no double/float to check. Pass.
- Kanakku checks: no new REST endpoint (T-6 reuses GET /api/v1/groups), no money or split AC (no jqwik need), no AI AC; Modulith test is unaffected because no backend code changed. N/A.
- Note: the e2e stack suite (`e2e` playwright.config) was not re-run here; the screens config stubs the API and covers AC-1 screenshots, and the build log records 3 green after T-7.

## Security
Not run, quick lane.

## Verdict
pass. No blocking AC-n or T-n.
