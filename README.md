# Kanakku

Open-source, self-hostable, AI-native expense splitting for friends, trips and flatmates.

"Kanakku" is Tamil and Malayalam for "accounts". Kanakku is built for Indian groups that settle
over UPI: AI does the data entry, turning chats, receipts and bank statements into draft expenses
and settlements, and a member confirms them before anything touches the ledger.

> **Status: pre-alpha.** This repository holds the product spec and the development workflow.
> No application code exists yet; the first feature (the Maven scaffold) is next. See the
> [roadmap](#roadmap).

## What it does (planned)

- **Chat to ledger.** Paste a WhatsApp trip chat or type "paid 1200 for the cab, everyone except
  Arun", and AI drafts the expenses for you to confirm.
- **Receipt split in plain English.** Snap a bill, say who had what, and the line items are
  assigned.
- **Settle-up detection.** Import a UPI or bank statement (CSV, Excel or password-protected PDF)
  and Kanakku matches the payments to open debts.
- **Agent access.** An MCP server lets Claude and other assistants read balances and draft
  expenses for a member to confirm.
- **An exact ledger.** Double-entry journal, money to the paisa, multi-currency, audit trail,
  undo, recurring expenses, guests and share links with a UPI pay button.

Kanakku is not a payment provider (it never moves money), a personal budgeting app, a hosted
SaaS or a WhatsApp bot.

## Principles

- **AI proposes, a person posts.** Every AI result is a draft that goes through the same
  validation, permissions and history as a manual entry.
- **Money is exact.** Amounts are `long` minor units plus an ISO 4217 currency, never floating
  point. Every split sums exactly to its expense and every group's balances sum to zero.
- **Your data stays yours.** One `docker compose up` on your own server, bring your own model
  (Anthropic, any OpenAI-compatible API, or Ollama for a fully offline setup), no telemetry.
  Statement passwords are never stored and raw uploads are deleted after parsing.

## Stack

| Layer | Choice |
| --- | --- |
| Backend | Java 25, Spring Boot 4, Spring Modulith (modular monolith), Maven |
| Data | Postgres 17, Flyway, jOOQ; db-scheduler for jobs; MinIO for files; no Redis |
| AI | Spring AI 2.0 (Anthropic, OpenAI-compatible, Ollama); Spring AI MCP server |
| Web | React, Vite and TypeScript single-page app, served by the backend |
| Mobile | Kotlin Multiplatform and Compose Multiplatform for Android and iOS |
| Tests | JUnit 5, Testcontainers, jqwik, Vitest, Playwright, kotlin.test |

Architecture overview: [docs/knowledge/architecture.md](docs/knowledge/architecture.md).

## Getting started

Not runnable yet. Once the scaffold feature lands, these will work:

```sh
docker compose up          # app, Postgres, MinIO and Mailpit
./mvnw -B -q test          # JUnit, Vitest and mobile tests
./mvnw -B -q spotless:check
```

## Roadmap

| Milestone | Scope |
| --- | --- |
| v0.1 Core | Accounts and sign-in, groups, expenses and splits, ledger, settle-up with UPI links, multi-currency, activity and undo, recurring expenses, search, notifications, Splitwise import, share links |
| v0.2 AI foundation | Model gateway, draft review queue, quick add, receipt scan, item assignment |
| v0.3 Chat and agents | WhatsApp chat import, API tokens and MCP server, "explain my balance" |
| v0.4 Statements | CSV, Excel and PDF statement import, settle-up detection |
| v0.5 Mobile apps | Android and iOS apps with offline support, camera and push |
| v1.0 Hardening | Docs, demo, security pass, store listings |

The full product spec, with requirements, acceptance criteria and the build-order backlog, is in
[docs/Kanakku_PRD.md](docs/Kanakku_PRD.md).

## Development workflow

Features are built one at a time with [rx](https://github.com/Shanerex/ai-sdlc-rx), a
spec-driven workflow for Claude Code that is vendored in `.claude/` and `.rx/`:

1. `/kanakku-feature <row>` starts the next row of the PRD backlog on its own branch.
2. `/rx-next` moves it through requirements, plan, build, verify and close, stopping for a
   human approval at each gate.
3. Specs live in `specs/<feature-id>/`; decisions and learnings accumulate in `docs/knowledge/`.

Hooks enforce plan-before-code, green tests before a build step ends, and forward-only
migrations. Project conventions and hard invariants are in [CLAUDE.md](CLAUDE.md).

## License

Apache License 2.0. Every dependency must be Apache, MIT, BSD or LGPL licensed.
