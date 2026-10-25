# Kanakku — PRD

Open-source, AI-native expense splitting for friends, trips and flatmates

Oct 19, 2026 · @Shane Rex Sasikumar · Status: Draft v0.1

## Overview

Kanakku is an open-source, self-hostable app for splitting shared expenses among friends, trips and flatmates. In Kanakku, AI does the data entry: it turns chats, receipts and bank statements into expenses and settlements that you confirm. "Kanakku" is Tamil and Malayalam for "accounts"; check that it is free on GitHub, Maven Central and a domain before the first public release.

**Vision.** Nobody types an expense twice, nobody chases a payment by hand, and the group's ledger is always right to the paisa.

**Positioning.** Splitwise's free plan now caps entries at roughly 3–5 expenses a day and shows ads; receipt itemization, currency conversion, search and charts are Pro features. Open-source alternatives such as Spliit and SplitPro (both TypeScript web apps) cover the basics: groups, uneven splits, multi-currency and simplified settlements. Kanakku does not compete on breadth. Its wedge is Indian groups that settle over UPI, built around four things those tools do not do:

1. **Chat to ledger.** Paste a WhatsApp trip chat or type "paid 1200 for the cab, everyone except Arun", and AI drafts the expenses for you to confirm.
2. **Receipt split in plain English.** Snap a bill, say "Priya and I shared the biryani, Arun had the drinks", and the line items are assigned.
3. **Settle-up detection.** Import a UPI or bank statement (CSV or password-protected PDF) and Kanakku matches the payments to open debts.
4. **Agent access.** An MCP server lets Claude and other agents read balances and draft expenses and settlements for a member to confirm.

The ledger core must still be excellent: exact money, an audit trail, undo, multi-currency and fast settle-up are the price of entry.

**Why Java.** Unlike Margin, whose core libraries exist only in JavaScript, every hard part of Kanakku is server-side and Java-native: a double-entry ledger in `BigDecimal`, optimistic concurrency, PDF parsing with Apache PDFBox, statement reconciliation and scheduled jobs. The web and mobile clients stay thin.

## Problem and target users

Shared expenses break down at data entry, not at maths. On a trip, one person types every bill late at night, half the cab rides are forgotten, and the free tier runs out by the third expense. After the trip, people pay each other over UPI and nobody records it, so the balances drift from reality.

| Persona | Who they are | What hurts today | What Kanakku gives them |
| --- | --- | --- | --- |
| Trip organizer | Plans group trips and ends up paying for most things | Types every expense; hits the daily cap; chases people for weeks | Chat to ledger, receipt scan and one-tap UPI settle-up |
| Flatmate | Shares rent, groceries and utilities every month | The same bills entered by hand each month; arguments over who paid what | Recurring expenses, an audit trail and "explain my balance" |
| Casual member | A friend on one trip who will never install an app | Asked to sign up just to see what they owe | Guest members and a read-only share link with a UPI pay button |
| Agent-driven user | Uses Claude or another assistant all day | Has to switch apps to log a bill | An MCP server: "add ₹2,400 dinner, split with the Goa group" |
| Self-hoster | Runs their own services; cares about financial privacy | Statements and spending data go to a third party | Docker deploy, bring-your-own model, data stays on their server |

## Goals, non-goals and success metrics

The v1.0 goal is a self-hostable app that a group of 3–20 friends or flatmates can run a whole trip or a year of rent on, from the web or native Android and iOS apps, with at least two AI differentiators working end to end.

**Goals**

- A ledger that is never wrong: every split sums exactly to its expense, and every group's balances sum to zero.
- AI that removes data entry: plain-English quick add, receipt scans and chat import, all landing as drafts a person confirms.
- Settle-up that closes the loop: UPI pay links out, and statement matching back in.
- Native Android and iOS apps (Kotlin Multiplatform) alongside a responsive web app, all on one API.
- One-command self-hosting (`docker compose up`) with a bring-your-own model, including local models.
- A Java codebase strong enough to discuss in depth in backend interviews: money, concurrency, idempotency, parsing and jobs.

**Non-goals for v1.0**

- Moving money. Kanakku records payments and opens UPI apps; it never holds funds or acts as a payment provider.
- Personal budgeting beyond the group's spending (kept as an optional P3 module).
- A hosted SaaS offering or billing.
- A WhatsApp bot (the WhatsApp Business API is paid); chat import uses WhatsApp's own export file.

**Success metrics**

| Metric | Target | When |
| --- | --- | --- |
| Ledger invariant violations across 10,000 random operation sequences (property tests) | 0 | v0.1 |
| Splits whose shares do not sum exactly to the expense total | 0 | v0.1 |
| Balance query, p95, for a group with 5,000 expenses | < 150 ms | v0.1 |
| Time from `git clone` to a running instance | < 10 min | v0.1 |
| Quick-add drafts confirmed without edits, on a 100-phrase test set | ≥ 80% | v0.2 |
| Receipt line items extracted correctly, on 30 real Indian receipts | ≥ 90% | v0.2 |
| Expenses found by chat import that the group confirms | ≥ 75% | v0.3 |
| Settle-up matches confirmed as correct | ≥ 90% | v0.4 |
| Expenses lost when a real trip is run offline on the Android and iOS apps | 0 | v0.5 |
| Crash-free sessions on Android and iOS | ≥ 99% | v0.5 |

## Scope at a glance

The scope is 27 features in four tiers. P0 and P1 (core, AI foundation and native apps) are the table stakes; P2 holds the differentiators that make Kanakku worth switching to; P3 is optional and built only if time allows. Feature IDs (F-nn) are used throughout this PRD and in the rx backlog.

| ID | Feature | Tier | Milestone |
| --- | --- | --- | --- |
| F-01 | Maven multi-module scaffold, CI and Docker self-host | P0 Core | v0.1 |
| F-02 | Accounts and sign-in (password, Google, Microsoft, Apple) | P0 Core | v0.1 |
| F-03 | Groups, members and invites | P0 Core | v0.1 |
| F-04 | Expenses and split types | P0 Core | v0.1 |
| F-05 | Double-entry ledger and balances | P0 Core | v0.1 |
| F-06 | Settle up, debt simplification and UPI pay links | P0 Core | v0.1 |
| F-07 | Multi-currency with locked rates | P0 Core | v0.1 |
| F-08 | Activity log, audit trail and undo | P0 Core | v0.1 |
| F-09 | Recurring expenses | P0 Core | v0.1 |
| F-10 | Search, filters and spending charts | P0 Core | v0.1 |
| F-11 | Notifications and reminders | P0 Core | v0.1 |
| F-12 | Splitwise import and CSV/JSON export | P0 Core | v0.1 |
| F-13 | Responsive web app and guest share links | P0 Core | v0.1 |
| F-27 | Phone numbers and contact matching | P0 Core | v0.1 |
| F-14 | AI gateway and bring-your-own model | P1 AI | v0.2 |
| F-15 | Draft expenses and review queue | P1 AI | v0.2 |
| F-16 | Quick add in plain English | P1 AI | v0.2 |
| F-17 | Receipt scan and itemization | P1 AI | v0.2 |
| F-18 | Plain-English item assignment | P1 AI | v0.2 |
| F-19 | Chat to ledger (WhatsApp import) | P2 Differentiator | v0.3 |
| F-20 | Scoped API tokens and MCP server | P2 Differentiator | v0.3 |
| F-21 | "Explain my balance" and trip summary | P2 Differentiator | v0.3 |
| F-22 | Statement import (CSV, Excel and PDF) | P2 Differentiator | v0.4 |
| F-23 | Settle-up detection | P2 Differentiator | v0.4 |
| F-26 | Native Android and iOS apps (Kotlin Multiplatform) | P1 Mobile | v0.5 |
| F-24 | Telegram bot for group entry | P3 Optional | Later |
| F-25 | Personal money manager module | P3 Optional | Later |

## Feature requirements

Each feature below lists what it must do and how we know it works. The acceptance criteria are written to drop straight into an rx `requirements.md`, where the pm agent gives them local R-n and AC-n IDs.

### P0 Core (v0.1)

**F-01 Maven multi-module scaffold, CI and Docker self-host.** This is the skeleton every later feature builds on.

- Requirements: a Maven multi-module build with the Maven wrapper: a parent pom that imports the Spring Boot BOM, an `app` Spring Boot module, and a `web` module whose React app is built by frontend-maven-plugin into the app's static resources. Domain packages are Spring Modulith modules. The version is a CI-friendly `${revision}` set in `.mvn/maven.config`. `./mvnw test` runs JUnit and the web app's Vitest suite; `./mvnw spotless:check` checks Java and TypeScript formatting. GitHub Actions CI. A `docker-compose.yml` with Postgres, MinIO and Mailpit. Readiness and liveness endpoints.
- Acceptance: `docker compose up` on a clean machine serves the app on one port within 10 minutes of `git clone`. CI fails a PR when tests or lint fail. A module that calls another module's internal package fails the Modulith verification test. Readiness returns 503 when Postgres is down.

**F-02 Accounts and sign-in.** People sign in with a password or an account they already have.

- Requirements: email and password sign-up with verification; passwords hashed with Argon2id. Sign-in with Google, Microsoft (personal and work accounts) and Apple, all through OpenID Connect with Spring Security's OAuth2 client and PKCE. An admin can add any other OIDC provider by its discovery URL. Apple's quirks are handled: its client secret is a signed JWT that must be renewed at least every six months, it sends the user's name only on the first sign-in, and the email may be a private relay address. Accounts link by verified email. HttpOnly, SameSite session cookies and CSRF protection on the web. A profile with display name, default currency and an optional UPI ID.
- Acceptance: a user who signs up and verifies can sign in; an unverified one cannot. Google sign-in works with a client ID from the admin's own Google Cloud project. Microsoft and Apple sign-in work by configuration alone. An ID token with the wrong issuer, audience or nonce is rejected. A user who signs in with an Apple private relay email can still be invited and linked. Five failed logins in 10 minutes trigger a rate limit.

**F-03 Groups, members and invites.** A group is a trip, a home, a couple or anything else that shares costs.

- Requirements: create a group with a type, a default currency and a cover image. Members are registered users or guests (a name, optionally an email). A guest can claim their place through an invite link. Invite links expire and can be revoked. Roles are Owner and Member. A member with a non-zero balance cannot be removed until they settle or the owner writes off the balance. Groups can be archived.
- Acceptance: a guest who claims an invite keeps every expense they were part of. An expired or revoked invite link fails. Removing a member with a balance is refused with the amount owed. A user never sees a group they do not belong to.

**F-04 Expenses and split types.** Record who paid and who shares.

- Requirements: an expense has an amount, currency, description, category, date, notes and an optional receipt image (stored in MinIO). One or more payers with amounts. Split equally, by exact amounts, by percentages or by shares, across any subset of members, with per-person adjustments. Amounts are stored in minor units (paise). Rounding remainders go to members by the largest-remainder method, ties broken by stable member order. Every create takes an `Idempotency-Key`; edits carry a version for optimistic locking.
- Acceptance: ₹100 split three ways gives ₹33.34, ₹33.33 and ₹33.33, the same way every time. Percentages that do not sum to 100 are rejected. Two people editing the same expense: the second gets 409 with the current version. The same `Idempotency-Key` sent twice creates one expense.

**F-05 Double-entry ledger and balances.** The ledger is the source of truth for who owes whom.

- Requirements: every expense and settlement posts a balanced journal entry to an append-only table. An edit posts a reversal and a new entry; nothing is updated in place. A balance table is updated in the same transaction. A nightly job rebuilds balances from the journal and raises an alert on any mismatch.
- Acceptance: property tests (jqwik) over 10,000 random sequences of add, edit, delete and settle keep every group's balances summing to zero per currency. Every journal entry's debits equal its credits. Balances rebuilt from the journal match the balance table exactly. An edited expense shows its reversal in history.

**F-06 Settle up, debt simplification and UPI pay links.** Close out what people owe.

- Requirements: record a payment between two members (UPI, cash or other). A per-group "simplify debts" setting suggests transfers by greedy matching of the largest debtor and creditor. Finding the true minimum number of transfers is NP-hard; the greedy approach guarantees at most n−1 transfers. When the payee has a UPI ID, show a `upi://pay` link with the exact amount and a note, plus a QR code. UPI gives no callback for person-to-person payments, so the payer confirms the payment on return.
- Acceptance: for n members with non-zero balances, suggestions number at most n−1 and bring every balance to zero. The pay link carries the exact amount and the group name. Recording a settlement updates both balances in one transaction. A member with no UPI ID gets no pay button.

**F-07 Multi-currency with locked rates.** Trips abroad split correctly.

- Requirements: each expense keeps its original currency. Its rate to the group currency is locked on the expense date, from a rates provider (ECB reference rates through Frankfurter, cached daily) or entered by hand. Balances show per currency and converted to the group currency. Conversion uses `BigDecimal` and rounds once, to the currency's minor unit.
- Acceptance: editing an old expense keeps its locked rate unless the user changes it. If the provider is down, the user is asked for a rate, never given 1:1 in silence. JPY (0 decimals), INR (2) and KWD (3) round to the right minor unit.

**F-08 Activity log, audit trail and undo.** Every change can be seen and reversed.

- Requirements: every change is stored as an event with its actor (a person, an AI draft and who confirmed it, or an API token), time, and before and after values. A group activity feed and a per-expense history. Deleted expenses stay in a group trash for 30 days, with restore.
- Acceptance: restoring a deleted expense brings back its exact splits and balances. Each history entry names who made the change and how. An AI-created expense shows both the draft source and the confirming member.

**F-09 Recurring expenses.** Rent and bills post themselves.

- Requirements: rules for monthly on day N, weekly, or every N days, with an optional end date, in the group's time zone (Asia/Kolkata by default). A scheduler posts each occurrence exactly once, even with two app instances. Skip or edit a single occurrence.
- Acceptance: a rule for the 31st posts on the last day of shorter months. Two instances running at once post an occurrence once (a unique key per rule and period). A skipped occurrence is not posted and the next one is. Members are notified when an occurrence posts.

**F-10 Search, filters and spending charts.** The Splitwise Pro features, free.

- Requirements: Postgres full-text search on descriptions and notes. Filters by member, category, date range and amount. Spending charts by category and by month, per group and per user. Every query is scoped to the user's groups inside the SQL.
- Acceptance: a search never returns an expense from a group the user is not in. Results come back in under 300 ms for 10,000 expenses. A new expense is searchable as soon as its save returns.

**F-11 Notifications and reminders.** People find out without opening the app.

- Requirements: in-app, email and web-push notifications when you are added to an expense, a settlement is recorded or a draft awaits review. A weekly reminder of open balances. Per-user preferences. Notifications leave through a transactional outbox (Spring Modulith event publication). Guests have no account or preferences; reaching them is part of F-13.
- Acceptance: a rolled-back transaction sends nothing. A crash after commit still sends the notification once on restart. A user who turns off email gets none.

**F-12 Splitwise import and CSV/JSON export.** Move in without retyping and leave without lock-in.

- Requirements: import a Splitwise group's CSV export, map its names to members and create the expenses with their original dates. Export a group as CSV, as full-fidelity JSON, and as a PDF trip report.
- Acceptance: an imported group's balances match the CSV's balance columns to the paisa. A JSON export re-imported into a new group gives identical expenses and balances. The PDF report lists every expense and the final settlements.

**F-13 Responsive web app and guest share links.** The web is for desktops, admins and friends who will never install an app.

- Requirements: a React + Vite + TypeScript single-page app, served as static files by Spring Boot from the same image. Its API client is generated from the backend's OpenAPI spec, like the mobile apps'. Usable at 360 px wide and installable to the home screen through a web manifest (online only). Every v0.1 feature has its web screen. A read-only share link per guest shows what they owe and a UPI pay button, with no account needed; links last until revoked or the group is archived. Charts with Recharts. Offline entry lives in the native apps (F-26), not the web.
- Reaching guests: Kanakku cannot message a guest on WhatsApp (no paid Business API), so it gives members the tools to tell them.
  - A guest with an email gets an email, through the F-11 outbox, when added to an expense, plus the weekly open-balance reminder, each with their share link. Every email has a one-click unsubscribe that needs no account; the opt-out is stored on the guest's member row.
  - A "Send on WhatsApp" button on each guest opens a `https://wa.me/` link with a prefilled message: the amount owed, the group name and the share link. It targets the guest's number when known (F-27) and otherwise lets the member pick the chat; on phones it uses the Web Share API. The server sends nothing to WhatsApp.
  - A user's weekly reminder lists guests who owe them, each linking to that guest's send button, so guests with no email are still chased.
- Acceptance: adding, editing and settling an expense work at 360 px and with the keyboard alone. A share link shows only the expenses that guest is part of. A revoked share link returns 404. The first screen loads in under 2 seconds on a mid-range phone over 4G. A guest with an email receives the expense email with a working share link; after unsubscribing they receive none. A guest without an email gets nothing, and appears in the weekly reminder of each member they owe. The WhatsApp link carries the exact amount and the share link, and the guest's number when one is stored.

**F-27 Phone numbers and contact matching.** In India people know each other by phone number, not email, and WhatsApp runs on it.

- Requirements: a user can add one mobile number to their profile, and a guest can carry an optional one. Numbers are stored in E.164 form, parsed with libphonenumber, with +91 as the default country. A number is verified by an SMS one-time code through an SMS provider the admin configures; Kanakku bundles none, since Indian SMS needs a paid, DLT-registered sender. Without a provider, numbers stay unverified. A verified number belongs to one account. Owner search (F-03) also matches a full phone number exactly, never a prefix, and only verified numbers; it still honours the invite-only setting and shows the number masked. When a user verifies an email or phone that matches a guest's, Kanakku suggests claiming that guest; nothing merges until the user confirms, and the claim then works exactly like an invite claim. Phone numbers never appear in logs or share links.
- Acceptance: "98765 43210", "+91 98765 43210" and "09876543210" store as the same number. A partial number finds no one. An unverified number is never matched by search. A second account cannot verify a number already verified on another. Five wrong codes lock verification for 15 minutes. With no SMS provider, the profile shows the number as unverified and search by phone is off. A suggested claim changes nothing until confirmed; once confirmed, the guest's expenses and balance carry over.

### P1 AI foundation (v0.2)

**F-14 AI gateway and bring-your-own model.** One server-side layer that every AI feature calls.

- Requirements: built on Spring AI's `ChatClient`, with Anthropic, OpenAI-compatible and Ollama (local) providers, including a vision-capable model for receipts. The admin configures providers; model keys are encrypted at rest and never sent to the browser. Structured output validated against a JSON schema, with one automatic retry on a validation failure. AI can be turned off per group. A per-user monthly token budget and rate limit. A usage log with feature, model, tokens, latency and user.
- Acceptance: switching the provider in admin settings changes the model for every AI feature without a restart. A key never appears in a browser response or a log line. A user over budget gets a clear error, not a silent failure. With AI off for a group, no AI controls appear and its AI endpoints return 403.

**F-15 Draft expenses and review queue.** AI proposes; a person posts. This is Kanakku's version of Margin's suggestion mode.

- Requirements: every AI result becomes a draft expense or draft settlement, never a ledger entry. Each draft shows its source (the phrase, receipt, chat lines or statement row) and flags low-confidence fields. Any member in the expense can confirm, edit then confirm, or discard it. Confirming calls the same service and validation as a manual expense. Drafts expire after 14 days.
- Acceptance: a draft never changes a balance until it is confirmed. Confirming the same draft twice posts once. A discarded draft leaves no ledger entry. History records the draft's source and who confirmed it.

**F-16 Quick add in plain English.** Type it the way you would say it.

- Requirements: a single text box turns "1200 cab yesterday, everyone except Arun, I paid" into a draft with amount, payer, participants, split, category and date. Names are matched to group members, including nicknames and partial names. An ambiguous name asks the user to pick; it never creates a member. The test set includes code-mixed phrases such as Tanglish.
- Acceptance: on a 100-phrase test set, at least 80% of drafts are confirmed without edits. A phrase with no amount gives a draft with the amount flagged as missing, never a guessed one. An unknown name opens a member picker.

**F-17 Receipt scan and itemization.** A photo of the bill becomes line items.

- Requirements: from a photo or PDF bill, the vision model returns line items, quantities, prices, taxes (CGST and SGST), service charge, discounts and the total. Kanakku checks that items and charges add up to the total, and flags a mismatch instead of fixing it. Taxes and service charge are spread across people in proportion to their items. Location data (EXIF) is stripped from images before storage.
- Acceptance: on 30 real Indian receipts, at least 90% of line items are extracted correctly. A receipt whose items do not reach its total is flagged with the difference. The spread of tax sums exactly to the tax line. A stored image carries no GPS data.

**F-18 Plain-English item assignment.** Say who had what.

- Requirements: after a scan, "Priya and I shared the biryani, Arun had the drinks, rest equally" assigns the items. The result shows as an editable table, with unassigned items listed. Confirming creates one expense with an itemized split.
- Acceptance: every item is assigned or listed as unassigned before confirm is allowed. "Rest equally" splits the remaining items among everyone present. The shares sum exactly to the receipt total.

### P2 Differentiators (v0.3 to v1.0)

**F-19 Chat to ledger (WhatsApp import).** The trip's group chat becomes the trip's expenses.

- Requirements: upload a WhatsApp "Export chat (without media)" file or paste text, then pick a date range. A parser handles Android and iOS formats, 12- and 24-hour times, and day-first or month-first dates. The model extracts candidate expenses with the sender, time and quoted message; the payer defaults to the sender. Candidates are checked against existing expenses and each other for duplicates. The raw chat is deleted when the job ends; drafts keep only their quoted lines.
- Acceptance: each draft cites the message lines it came from. An expense already in the group is flagged as a possible duplicate, not drafted again. Android and iOS exports both parse. A message with no amount, such as "I'll pay later", produces nothing. The uploaded file is gone after the job.

**F-20 Scoped API tokens and MCP server.** Assistants and agents work with your groups.

- Requirements: personal API tokens scoped to one group or all of a user's groups, read or write, with expiry and revoke. An MCP server (streamable HTTP, Spring AI's MCP server starter) with tools `list_groups`, `get_balances`, `list_expenses`, `add_expense`, `record_settlement` and `explain_balance`. `add_expense` and `record_settlement` always create drafts (F-15); there is no direct-post scope in v1.0. Every call goes through the same services and permission checks as the app, labelled with the token's name.
- Acceptance: Claude configured with a token adds an expense that shows up as a draft labelled with the token name and changes no balance until a member confirms. A read-only token's write call is rejected. A token scoped to one group cannot read another. A revoked token fails on its next call.

**F-21 "Explain my balance" and trip summary.** Answers people trust because the numbers come from the ledger.

- Requirements: "Why do I owe Priya ₹2,340?" returns a trace computed from the ledger: the expenses and settlements behind the balance, netted. The model only writes the explanation over that trace. A validator rejects any explanation that states an amount not in the trace. A trip summary at the end of a trip: total and per-person spend, spend by category, the biggest expense and the settle-up plan, as a shareable link.
- Acceptance: every amount in an explanation matches the trace. The trace sums exactly to the balance. With AI off, the trace still shows as a table.

**F-22 Statement import (CSV and PDF).** Bring in what actually left your account.

- Requirements: upload a bank or UPI app statement as CSV, Excel (XLS or XLSX, read with Apache POI) or PDF. Password-protected PDFs open with Apache PDFBox using a password typed for that upload only, never stored. Tables are extracted with Tabula-java. v0.4 ships parsers for SBI, HDFC Bank, ICICI Bank, Axis Bank and Kotak Mahindra Bank; any other layout goes to a model fallback that maps it to columns for the user to confirm. Each transaction keeps its date, amount, direction, counterparty name or UPI ID, and UPI reference (UTR). The raw file is deleted after parsing; parsed rows are purged after 30 days.
- Acceptance: a statement from each of the five banks parses with every amount and date correct, checked by golden-file tests on redacted real statements. The PDF password never reaches disk or a log. The raw file is deleted within 1 minute of the job ending. Importing the same statement twice creates no duplicate transactions (unique by UTR). An unknown layout either maps through the fallback with user confirmation or fails with a clear message.

**F-23 Settle-up detection.** Payments made over UPI close debts without anyone recording them.

- Requirements: imported transactions are matched to open debts by amount, counterparty (the member's UPI ID, or a fuzzy name match) and a date after the debt. Each match gets a confidence score and becomes a draft settlement (F-15). Payer and payee may both import the same payment; the UTR makes it one settlement.
- Acceptance: an exact amount to a member's UPI ID gives a high-confidence draft. A payment to someone outside the group gives no suggestion. A payment imported by both sides is recorded once. At least 90% of confirmed matches are correct.

### P1 Mobile apps (v0.5)

**F-26 Native Android and iOS apps (Kotlin Multiplatform).** The phone is where bills get paid and photographed, so v1.0 ships real apps.

- Requirements: a Kotlin Multiplatform project in `mobile/`, built with Gradle (which Kotlin Multiplatform requires), with a shared module and Compose Multiplatform UI for both platforms. The shared module holds the API client (Ktor, generated from the backend's OpenAPI spec), domain models (kotlinx.serialization) and an offline outbox in SQLite through SQLDelight, with client-generated IDs and idempotency keys. Platform pieces (camera, secure storage, UPI) sit behind `expect`/`actual`. Screens for groups, expenses, balances, the draft review queue, quick add, the receipt camera and settle-up. Native sign-in with Google, Microsoft and Apple through the system browser with PKCE. The app sends the provider's ID token to the API, which verifies it and issues short-lived Kanakku access tokens and rotating refresh tokens, stored in the iOS Keychain or Android Keystore. Push notifications through FCM and APNs. UPI pay opens installed UPI apps (an intent on Android, UPI app URL schemes on iOS), with the QR code as a fallback. Android ships through Play internal testing; iOS is built on your Mac with Xcode and ships through TestFlight.
- Acceptance: the same Google account sees the same groups on web, Android and iOS. An expense added offline on a phone syncs exactly once, and a conflict with a newer server version is shown, never dropped. A push notification arrives within 10 seconds of being added to an expense. The UPI button opens an installed UPI app with the amount filled in, and the QR code shows when none is installed. Reusing an old refresh token is rejected and revokes that session's whole token family. Both store test builds install and run.

### P3 Optional (after v1.0)

- **F-24 Telegram bot for group entry.** Add the bot to a group chat; messages with amounts become drafts (F-15) for the linked Kanakku group.
- **F-25 Personal money manager module.** Reuses F-22's statement import for personal spending: categories, forgotten subscriptions and a monthly plain-English summary.

## Non-functional requirements

One rule covers AI: the model proposes and a person posts. Every AI result is a draft that passes the same validation, permissions and history as a manual entry.

| Area | Requirement |
| --- | --- |
| Money correctness | Amounts are `long` minor units plus an ISO 4217 currency; `double` and `float` never touch money. Conversions use `BigDecimal` and round once. Every split sums exactly to its expense; every group's balances sum to zero per currency. |
| Performance | Balance query p95 < 150 ms and expense list p95 < 200 ms for a group with 5,000 expenses. App start < 10 s. Runs in 1 GB of RAM including Postgres, for a small self-host. |
| Reliability | No confirmed write is ever lost. Every create is idempotent by key. Background jobs retry with backoff and park after 5 failures. Nightly `pg_dump` with a documented restore. |
| Concurrency | Optimistic locking on edits; balance updates and journal posts in one transaction. Scheduled jobs run once across instances. Background work uses Java virtual threads. |
| Security | Permissions checked on the server for every HTTP and MCP call, scoped to group membership in the query. Model keys, OIDC secrets and token hashes encrypted or hashed at rest. CSRF protection. Dependency and secret scanning in CI. |
| Privacy | Statement PDF passwords never stored. Raw statements and chat exports deleted after parsing; parsed statement rows purged after 30 days. Image location data stripped. No telemetry by default. Data leaves the server only in calls to the model provider the admin chose; Ollama allows a fully offline setup. |
| AI safety | Chat text, receipts and statements are untrusted model input. Permissions are never enforced through prompts. Model output is schema-validated, and amounts in explanations are checked against the ledger. Every AI call is logged with feature, model, tokens and requester. |
| Accessibility | WCAG 2.1 AA for core screens. Full keyboard use. Screen-reader labels on amounts and split controls. |
| Operability | Spring Boot Actuator health checks, Micrometer metrics for Prometheus, OpenTelemetry traces, structured JSON logs with request IDs. |
| Licensing | Apache-2.0 for the whole project, including the split and settle-up engine. Every dependency is Apache, MIT, BSD or LGPL, all compatible. |

## Architecture and tech stack

Kanakku is one Spring Boot application split into Spring Modulith modules: one JVM and one Postgres run everything, and a test still enforces the module boundaries.

&#91;embedded content: Kanakku architecture · two paths to the ledger\]

A member's own entry goes straight to the expenses or settlement service. Anything from AI, a chat, a statement or an assistant waits in drafts until a member confirms it. Only those two services post to the ledger, in the same transaction as the balance update.

| Layer | Choice | Why |
| --- | --- | --- |
| Build | Maven with the wrapper, multi-module; CI-friendly `${revision}`; Gradle only inside `mobile/` | Familiar; BOM-managed versions; the version lives apart from dependencies, which rx needs; Kotlin Multiplatform requires Gradle |
| Runtime | Java 25 LTS, virtual threads | Current LTS; cheap concurrency for jobs, uploads and model calls |
| Framework | Spring Boot 4, Spring Modulith | Module boundaries verified by a test; its event publication registry is the transactional outbox |
| Auth | Spring Security OAuth2 client and resource server; Argon2id | One code path for Google, Microsoft, Apple and any OIDC provider; JWT access tokens for the mobile apps |
| API contract | springdoc-openapi | The OpenAPI spec the mobile apps' Kotlin client is generated from |
| Data access | Postgres 17, jOOQ, Flyway | Type-safe SQL with explicit transactions and row locks, which a ledger needs |
| Jobs | db-scheduler (Postgres-backed) | Cluster-safe scheduled and one-off jobs without Redis |
| AI | Spring AI 2.0: Anthropic, OpenAI-compatible, Ollama | Bring-your-own model; structured output with validation and retry |
| MCP | Spring AI MCP server starter, streamable HTTP | The protocol Claude already speaks, over the same services as REST |
| Parsing | Apache PDFBox, Tabula-java, Apache POI, Jackson CSV | Opens encrypted PDFs, extracts statement tables and reads Excel statements |
| Exchange rates | Frankfurter (ECB reference rates) | Free, no key, daily; cached and locked per expense |
| PDF export | OpenHTMLtoPDF | Trip reports rendered from HTML, built on PDFBox |
| Files | MinIO (S3 API) | Self-hostable; swaps for S3 or R2 |
| Web client | React + Vite + TypeScript single-page app, served by Spring Boot | The framework you know; static files keep one deployable image; API client generated from the same OpenAPI spec as the apps |
| Mobile apps | Kotlin Multiplatform, Compose Multiplatform, Ktor, SQLDelight | One Kotlin codebase for Android and iOS; Compose for iOS is stable; offline outbox in SQLite |
| Testing | JUnit 5, Testcontainers, jqwik, Vitest, Playwright, kotlin.test | Real Postgres in tests; property tests for every money rule; web and app tests |
| Ops | Docker Compose, GitHub Actions, Actuator, Micrometer, OpenTelemetry | One-command deploy, metrics and traces |

## Release roadmap

v1.0 is about nine months out, assuming 8 to 10 hours a week of weekend time from late October 2026, alongside Margin. The dates are estimates; the order and the exit gates are what matter.

&#91;embedded content: Kanakku release roadmap · 6 milestones\]

Each milestone ends only when its exit gate passes. Every milestone after v0.1 is cut by tag, so a release can ship with a feature missing rather than slip.

| Milestone | Features | Exit gate |
| --- | --- | --- |
| v0.1 Core | F-01 to F-13, F-27 | Property tests find zero ledger violations; you run one real trip or a month of flat expenses on it |
| v0.2 AI foundation | F-14 to F-18 | Every AI result lands as a draft; quick add reaches 80% and receipts 90% on their test sets; switching between Claude, OpenAI and Ollama needs no restart |
| v0.3 Chat and agents | F-19 to F-21 | A real trip chat imports with at least 75% of drafts confirmed; Claude adds a draft expense over MCP |
| v0.4 Statements | F-22 to F-23 | Your own bank's PDF statement imports without the password being stored; at least 90% of settle-up matches are correct |
| v0.5 Mobile apps | F-26 | A real trip runs on the Android and iOS apps, offline in places, with no lost expenses; Play internal testing and TestFlight builds pass |
| v1.0 Hardening | Docs, demo video, security pass | Someone else gets from `git clone` to a running instance in under 10 minutes; Play Store and App Store listings approved; docs site and demo video published |

## Building it with the rx toolkit

rx works one feature at a time with tight file caps, so this PRD is not handed to rx whole. Instead, each F-nn becomes one `/rx-feature` run, and its intake answers come from the table below. Large features are split so each fits rx's caps: 60 lines for `requirements.md` and 40 for `tasks.md`.

**One-time setup in the new `kanakku` repo**

1. Create the repo with a `main` branch and an initial commit.
2. `/rx-init` is still not in `commands/` (only `feature`, `next` and `status` exist). Until it lands, create these by hand: `.rx/config.yml` (below), a `CLAUDE.md` of 60 lines or fewer, `docs/knowledge/architecture.md` (from the next section), and empty `docs/knowledge/decisions/` and `docs/knowledge/learnings/` folders.
3. Commit the setup on `main`, then start F-01 with `/rx-feature maven-scaffold` and run `/rx-next` until it is done.

```yaml
# .rx/config.yml
test_cmd: ./mvnw -B -q test
lint_cmd: ./mvnw -B -q spotless:check
test_timeout: 900
version_file: .mvn/maven.config
source_globs: app/src/main/**, web/src/**, mobile/*/src/*Main/**
test_globs: app/src/test/**, web/src/**/*.test.ts, web/src/**/*.test.tsx, mobile/*/src/*Test/**, e2e/**
enforce_plan_first: true
branch_pattern: feature/{id}
base_branch: main
version_bump: release
```

Four build notes for rx:

- `rx-check shared` blocks any change to `version_file` on a feature branch, and a `pom.xml` holds both the version and the dependencies. So use Maven's CI-friendly versions: every pom says `<version>${revision}</version>`, the value lives alone in `.mvn/maven.config` (`-Drevision=0.1.0`), and flatten-maven-plugin writes the resolved version into installed poms. Pointing `version_file` at `pom.xml` would block every feature that adds a dependency.
- `./mvnw test` and `./mvnw spotless:check` only work after F-01, so F-01's tasks must create the Maven wrapper, the Spotless config (Java, TypeScript and Kotlin) and the frontend-maven-plugin executions that run the web app's Vitest suite in the \`test\` phase. Otherwise F-01's `rx-check done` gate will fail.
- Kotlin Multiplatform builds only with Gradle. Row 27 adds a `mobile` Maven module whose pom runs `mobile/gradlew allTests` through exec-maven-plugin in the `test` phase, and Spotless's ktlint step covers the Kotlin files, so the same two rx commands cover the apps. iOS tests need Xcode: they run on your Mac and on a macOS CI runner, and the module skips them on Linux.
- Every feature row includes its own web screen. The web client is thin, so the screen is a task in the same feature, not a separate one. Mobile screens arrive in rows 27 and 28, on the same API.

**Feature backlog, in build order**

| # | `/rx-feature` name | PRD | Depends on | Intake: why / who / done-when |
| --- | --- | --- | --- | --- |
| 1 | maven-scaffold | F-01 | — | Nothing runs yet / every contributor / `docker compose up` serves the app; CI gates PRs; Modulith verification runs |
| 2 | accounts-password | F-02 (part 1) | 1 | No identity / every user / sign-up, verification, password sign-in and rate limits pass F-02 acceptance |
| 3 | oidc-sign-in | F-02 (part 2) | 2 | People want to sign in with Google, Microsoft or Apple / every user and admins / all three work by config alone; bad tokens are refused; Apple relay emails link |
| 4 | groups-members | F-03 | 2 | No way to share costs / trip organizers and flatmates / groups, guests, invites and the remove-with-balance rule pass F-03 acceptance |
| 29 | phone-numbers | F-27 | 4 | Indians find friends by phone, not email / every user and guests / numbers normalise to E.164; search matches only verified numbers exactly; matching guests are suggested for claim, never merged silently |
| 5 | ledger-core | F-05 | 4 | Balances need a source of truth / every member / journal and balances keep the zero-sum invariant under property tests |
| 6 | expenses-splits | F-04 (part 1) | 5 | Nothing to split yet / every member / equal, exact, percentage and share splits post to the ledger with exact rounding and idempotent creates |
| 7 | expenses-payers-receipts | F-04 (part 2) | 6 | Real bills have several payers and a photo / trip organizers / multiple payers, adjustments and receipt images work; edits lock optimistically |
| 8 | settle-up-upi | F-06 | 6 | Debts never close / every member / settlements record; simplified transfers are at most n−1; UPI links carry the exact amount |
| 9 | multi-currency | F-07 | 6 | Trips abroad break the maths / travellers / locked rates; correct minor units; no silent 1:1 fallback |
| 10 | activity-undo | F-08 | 6 | Nobody knows who changed what / every member / activity feed, per-expense history and 30-day restore |
| 11 | recurring-expenses | F-09 | 6 | Rent is typed every month / flatmates / each occurrence posts exactly once across two instances |
| 12 | search-charts | F-10 | 6 | Pro features are paywalled elsewhere / every member / scoped search under 300 ms; charts by category and month |
| 13 | notifications-outbox | F-11 | 8 | People miss updates / every member / outbox delivery: nothing on rollback, exactly once after a crash |
| 14 | splitwise-import-export | F-12 | 7, 9 | Switching costs and lock-in fear / people leaving Splitwise / imported balances match to the paisa; JSON round trip is lossless |
| 15 | web-share-links | F-13 | 8, 13 | Guests will not install an app / casual members / share links show a guest's balance and a UPI button; guests with an email get updates and can unsubscribe; WhatsApp send links carry the amount; revoked links return 404; core pages work at 360 px |
| 16 | ai-gateway | F-14 | 2 | No AI layer / admins and every AI feature / provider switch without restart; keys never reach the browser |
| 17 | draft-review-queue | F-15 | 6, 16 | AI must not post to the ledger / every member / drafts change no balance until confirmed; confirm is idempotent |
| 18 | quick-add | F-16 | 17 | Typing forms is slow / every member / ≥ 80% of the 100-phrase test set confirmed without edits |
| 19 | receipt-scan | F-17 | 7, 17 | Itemizing bills by hand is painful / trip organizers / items, taxes and totals extracted; mismatches flagged |
| 20 | item-assignment | F-18 | 19 | Assigning items is fiddly / groups at restaurants / plain-English assignment; shares sum to the total |
| 21 | whatsapp-chat-import | F-19 | 17 | Expenses live in the group chat / trip organizers / drafts cite their messages; duplicates flagged; raw chat deleted |
| 22 | api-tokens-mcp | F-20 | 17 | Assistants cannot reach Kanakku / agent-driven users / Claude adds a draft expense over MCP with a scoped token |
| 23 | explain-balance-summary | F-21 | 5, 16 | People distrust balances / every member / traces sum to the balance; explanations state no amount outside the trace |
| 24 | statement-import-csv | F-22 (part 1) | 2 | Payments made outside the app are invisible / every member / CSV and Excel statements import with UTR de-duplication |
| 25 | statement-import-pdf | F-22 (part 2) | 16, 24 | Indian banks send password-protected PDFs / every member / the five banks' PDFs parse; password never stored; raw file deleted |
| 26 | settle-up-detection | F-23 | 8, 17, 24 | UPI payments never get recorded / every member / matches become draft settlements; one settlement when both sides import |
| 27 | mobile-app-core | F-26 (part 1) | 3, 8 | Groups live on phones, not browsers / every member / Kotlin Multiplatform apps sign in natively with rotating tokens, show groups and balances, and add expenses offline exactly once |
| 28 | mobile-camera-push-upi | F-26 (part 2) | 13, 19, 27 | Bills get photographed and paid on the phone / trip organizers and payers / receipt camera, push notifications and UPI intents work on both platforms; store test builds pass |

Row 29 was added after the table was first numbered; it is built right after row 4, so numbers stay stable for existing branches and specs.

Use rx's `--quick` lane for small follow-ups such as a new bank layout parser or a new category, not for any row above. The security agent's review deserves the closest read on rows 2, 3, 4, 16, 22, 24, 25, 27 and 29, where auth, group scoping, secrets, tokens, mobile sessions, financial statements and phone numbers live.

## Seed architecture.md

Paste this as `docs/knowledge/architecture.md` (59 lines, under rx's 80-line cap). After this, only a human edits it, in its own PR, when a feature's `plan.md` proposes an architecture change.

```markdown
# Architecture: Kanakku

## System
- Self-hosted, open-source expense splitting with AI that drafts and humans who post.
- Java 25 LTS, Spring Boot 4, Maven (wrapper, multi-module). One app image, one `docker compose` deploy.
- Modular monolith: Spring Modulith modules, verified by test; no module reads another's internals.

## Modules (app/src/main/java/dev/kanakku/...)
- identity: users, password + OIDC sign-in (Google, Microsoft, Apple), sessions, mobile and API tokens.
- groups: groups, members, guests, invites, share links, roles.
- ledger: journal entries, balances, invariants, nightly rebuild check. Owns all money maths.
- expenses: expenses, splits, payers, receipts, recurring rules. Posts to ledger via its API.
- settlement: payments, debt simplification, UPI links, statement matching.
- fx: currency metadata, rate provider (Frankfurter/ECB), locked rates.
- drafts: AI drafts and the review queue; confirm calls expenses/settlement services.
- ai: Spring AI gateway (Anthropic, OpenAI-compatible, Ollama), prompts, schemas, budgets.
- imports: Splitwise CSV, WhatsApp chat, bank statements (PDFBox, Tabula-java, POI).
- mcp: MCP server (streamable HTTP) over the same services.
- notify: in-app, email, web push, FCM and APNs push; weekly reminders.
- reporting: search, charts, exports, trip summary, balance traces.
- web (Maven module): React + Vite + TypeScript SPA, built into the app's static resources.
- mobile (Gradle, Kotlin Multiplatform): Compose apps for Android and iOS; client from OpenAPI.

## Data
- Postgres 17: system of record. Flyway migrations, jOOQ for type-safe SQL.
- MinIO (S3 API): receipt images and temporary uploads (statements, chats).
- No Redis: jobs, outbox and locks live in Postgres.

## Invariants
- Money is long minor units + ISO 4217 currency. No double or float for money, ever.
- Every split sums exactly to its expense; remainders by largest remainder, stable order.
- The journal is append-only; edits post reversals. Group balances sum to zero per currency.
- Journal post and balance update share one transaction.
- Every create is idempotent by Idempotency-Key; edits use optimistic version checks.
- AI output is a draft; only a member's confirm posts to the ledger.
- MCP writes are always drafts; there is no direct-post scope.
- Every read is scoped to the caller's group membership inside the SQL query.
- Chat, receipt and statement text is untrusted model input; no permission logic in prompts.
- Statement passwords are never persisted; raw uploads are deleted after parsing.
- Settlements from statements are unique by UPI reference (UTR).
- Mobile refresh tokens rotate; reusing an old one revokes the whole token family.

## Flows
- Add: web or app -> REST -> expenses -> ledger (journal + balances) -> outbox -> notify.
- Quick add: client -> drafts -> ai -> draft -> member confirms -> expenses -> ledger.
- Receipt: upload -> MinIO -> job -> ai (vision) -> itemized draft -> confirm.
- Statement: upload -> job -> imports parse -> settlement match -> draft settlements.
- MCP: token -> mcp -> identity -> same services -> same history as a person.
- Recurring: scheduler (one instance via Postgres lock) -> expenses, unique per period.
- Mobile sign-in: app -> provider (PKCE) -> ID token -> identity -> access + refresh tokens.

## Testing
- JUnit 5 + Testcontainers (Postgres, MinIO). jqwik property tests for ledger and splits.
- Modulith verification test for module boundaries. Playwright for web, kotlin.test for apps.
- AI calls stubbed by a fake provider in tests; no network in CI.

## Conventions
- REST under /api/v1, Bean Validation, RFC 9457 problem details, JSON logs with request IDs.
- Migrations forward-only via Flyway. License: Apache-2.0.
```

## Risks and open questions

The biggest risk is scope: 29 rx features on weekends, alongside Margin. v0.1 must ship and be used on one real trip before any AI work starts.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Scope outruns weekend time, with Margin running in parallel | Nothing ships | Hard milestone cuts; v0.1 ships with no AI; P3 stays optional; use Kanakku on your own trips from v0.1 |
| Rounding or ledger bugs | Balances people stop trusting | Minor units only; property tests on every split and ledger change; nightly rebuild check |
| Kotlin Multiplatform and Compose are new to you, on two platforms | v0.5 slips | A small spike during v0.1 (sign in and list groups on Android and iOS); shared UI and logic; Android first; the web app covers iPhone users until the iOS app is ready |
| The apps need Gradle and a Mac for iOS | Two build tools; slower iOS CI | Maven wraps the Gradle build so rx runs one command; iOS builds on your Mac, with a macOS CI runner only for release tags |
| App store review and fees (Apple charges yearly, Google once) | iOS listing slips or costs money | TestFlight and Play internal testing early in v0.5; Sign in with Apple is already in F-02, which App Store review expects alongside Google sign-in |
| UPI apps reject or warn on pay links to personal UPI IDs | Settle-up button feels broken | Always show the QR code and a copy-UPI-ID fallback; test with GPay, PhonePe and Paytm before v0.1 |
| WhatsApp export formats differ by OS, locale and app version | Chat import misses expenses | Golden-file tests per format; a parser with clear errors; pasted text as a fallback |
| Bank statement layouts vary and change | Statement import fails for some banks | Parsers for the five largest banks; model-assisted column mapping with user confirmation; CSV and Excel first |
| Statements and chats are sensitive data | A leak harms real people | Delete raw uploads after parsing; never store PDF passwords; purge rows after 30 days; self-host first |
| Receipt OCR is weak on faded thermal paper | Wrong items, wrong splits | Totals check flags mismatches; every result is a draft; easy manual edit |
| Prompt injection through chats, receipts or statements | Wrong drafts | Schema-validated output; drafts only, including over MCP; no tool access beyond the requester's groups |
| Model costs on self-hosted installs | Admins turn AI off | Per-user budgets; Ollama support; small models for quick add |
| Friends keep using Splitwise | Your own groups never switch | Splitwise import, guest members and share links, so friends need nothing installed to start |
| Guests never hear about what they owe | Balances stay open for months | Email guests who have one; a WhatsApp send button for every guest; creditors' weekly reminders list guests who owe them |
| Phone numbers are personal data and easy to enumerate | Strangers learn who uses the instance | Exact full-number match only, verified numbers only, the invite-only setting honoured, masked in results, kept out of logs |

**Open questions**

- [x] Backend language: Java, Spring Boot (decided 2026-10-19). Every hard part is server-side and Java-native, unlike Margin.
- [x] Build: Maven, with CI-friendly versions (decided 2026-10-19). The mobile apps alone use Gradle, which Kotlin Multiplatform requires.
- [x] Sign-in: password plus Google, Microsoft and Apple; no GitHub (decided 2026-10-19).
- [x] Native Android and iOS apps are a v1.0 goal (decided 2026-10-19).
- [x] Product name: "Kanakku" is free on GitHub, Maven Central, both app stores and a domain (checked 2026-10-19).
- [x] Mobile stack: Kotlin Multiplatform with Compose Multiplatform for Android and iOS (decided 2026-10-19). It builds on Kotlin you already know; Compose for iOS has been stable since version 1.8.0.
- [x] Web client: React + Vite + TypeScript, built as a single-page app and served by Spring Boot (decided 2026-10-19). Not Next.js: Kanakku needs no SEO or server rendering, and Next would add a Node server to every self-hosted install. Offline entry lives in the native apps, so the web needs no offline PWA work.
- [x] License: Apache-2.0 (decided 2026-10-19). The goal is a portfolio and your own groups, not protection from hosted clones, so a permissive license that matches Spring and Kotlin is the simplest choice.
- [x] MCP writes: always drafts; no `post` scope in v1.0 (decided 2026-10-19). Chats and prompts can carry injected instructions, and one tap to confirm costs little.
- [ ] Phone verification (F-27): SMS one-time codes need a paid, DLT-registered sender in India. Planned: an optional, admin-configured SMS provider, with phone search off without one. Revisit if a free verification path appears.
- [x] Statement parsers in v0.4: SBI, HDFC Bank, ICICI Bank, Axis Bank and Kotak Mahindra Bank (decided 2026-10-19), plus the model fallback for any other layout. Each further bank is a quick-lane rx change.

## Sources

- [Splitwise pricing 2026: what Pro costs and what stays free (getfinny.app)](https://getfinny.app/blog/splitwise-pricing-2026)
- [Open-source Splitwise alternatives: Spliit, SplitPro (openalternative.co)](https://openalternative.co/alternatives/splitwise)
- [Spring AI 2.0 GA with Spring Boot 4 and MCP SDK 2.0 (besthub.dev)](https://www.besthub.dev/articles/spring-ai-2-0-ga-native-java-ai-development-with-spring-boot-4-integration-7c0c0bd64e6d)
- [Spring AI streamable-HTTP MCP servers (docs.spring.io)](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html)

* [Compose Multiplatform 1.8.0: Compose for iOS is stable (blog.jetbrains.com)](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
