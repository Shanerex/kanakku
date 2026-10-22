# Screens

One file per area. Each screen lists: PRD feature and backlog row, route (web) and screen
(mobile), layout at phone and desktop width, content, states, actions with the API call they
make, copy, and accessibility notes. Build a screen from its spec plus
[../components.md](../components.md) and [../patterns.md](../patterns.md); when the spec and a
mock disagree, the spec wins and the mock gets fixed.

API paths in the specs show which call a screen needs; the backend's OpenAPI spec is the
authority for their exact shape, and a feature's `plan.md` may rename them.

Detail level: **full** for v0.1 screens (what you build next) and mobile core; **outline**
for later milestones (enough to keep the design coherent; refine when the feature starts).

| File | Screens | PRD | Row | Detail | Mock |
| --- | --- | --- | --- | --- | --- |
| [auth.md](auth.md) | Sign in, sign up, verify email, forgot/reset password, first-run profile | F-02 | 2, 3 | full | [web-sign-in](../mocks/web-sign-in.html) |
| [home.md](home.md) | Home (balances overview + groups) | F-05, F-06 | 4–8 | full | [web-home](../mocks/web-home.html) |
| [groups.md](groups.md) | Groups list, new group, group settings, members, invites | F-03 | 4 | full | — |
| [group-detail.md](group-detail.md) | Group: Expenses, Balances, Activity, Insights tabs | F-04, F-05, F-08, F-10 | 5–12 | full | [web-group](../mocks/web-group.html) |
| [expense.md](expense.md) | Expense form, split editor, payers, expense detail and history | F-04, F-07, F-08 | 6, 7, 9, 10 | full | [web-expense-form](../mocks/web-expense-form.html) |
| [settle-up.md](settle-up.md) | Settle-up plan, record payment, UPI pay | F-06 | 8 | full | [web-settle-up](../mocks/web-settle-up.html) |
| [activity.md](activity.md) | Activity feed, notifications, trash | F-08, F-11 | 10, 13 | full | — |
| [search-insights.md](search-insights.md) | Search and filters, charts | F-10 | 12 | full | — |
| [recurring.md](recurring.md) | Recurring rules and occurrences | F-09 | 11 | full | — |
| [me.md](me.md) | Profile, appearance, notification preferences, security, sessions | F-02, F-11 | 2, 3, 13 | full | — |
| [import-export.md](import-export.md) | Splitwise import, exports | F-12 | 14 | full | — |
| [share-link.md](share-link.md) | Guest share link, invite claim | F-03, F-13 | 4, 15 | full | [web-share-link](../mocks/web-share-link.html) |
| [add-and-drafts.md](add-and-drafts.md) | Add sheet, quick add, drafts queue, draft review | F-15, F-16 | 17, 18 | full | [web-add-sheet](../mocks/web-add-sheet.html), [web-drafts](../mocks/web-drafts.html) |
| [receipt.md](receipt.md) | Receipt scan, itemized review, plain-English assignment | F-17, F-18 | 19, 20 | outline | [mobile-core](../mocks/mobile-core.html) |
| [chat-import.md](chat-import.md) | WhatsApp import wizard and results | F-19 | 21 | outline | — |
| [explain-summary.md](explain-summary.md) | Explain my balance, trip summary | F-21 | 23 | outline | — |
| [statements.md](statements.md) | Statement import, transactions, settle-up matches | F-22, F-23 | 24–26 | outline | — |
| [api-tokens.md](api-tokens.md) | API tokens and MCP setup | F-20 | 22 | outline | — |
| [admin.md](admin.md) | Instance admin: AI providers, OIDC providers, budgets, usage | F-02, F-14 | 3, 16 | outline | — |
| [mobile.md](mobile.md) | Mobile-only: native sign-in, offline outbox, sync conflicts, camera, push, UPI intents | F-26 | 27, 28 | full | [mobile-core](../mocks/mobile-core.html) |
