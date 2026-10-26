# Kanakku

Open-source, self-hostable, AI-native expense splitting for friends, trips and flatmates.

"Kanakku" is Tamil and Malayalam for "accounts". Kanakku is built for Indian groups that settle
over UPI: AI does the data entry, turning chats, receipts and bank statements into draft expenses
and settlements, and a member confirms them before anything touches the ledger.

> **Status: pre-alpha.** The Maven scaffold has landed: an empty API app, a web shell and a
> compose stack. No product features exist yet. See the [roadmap](#roadmap).

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
| Web | React, Vite and TypeScript single-page app in `web/`, served on its own port and calling the API cross-origin |
| Mobile | Kotlin Multiplatform and Compose Multiplatform for Android and iOS |
| Tests | JUnit 5, Testcontainers, jqwik, Vitest, Playwright, kotlin.test |

Architecture overview: [docs/knowledge/architecture.md](docs/knowledge/architecture.md).

## Run locally

Requires Docker with Compose (Docker Desktop or Docker Engine). Nothing else is needed on a clean
machine: Maven, Node and Java run inside the image builds.

```sh
git clone <this repository> && cd kanakku
cp .env.example .env
# put a secret after KANAKKU_INVITE_TOKEN_SECRET= in .env, for example: openssl rand -hex 32
docker compose up
```

`KANAKKU_INVITE_TOKEN_SECRET` has no default, so compose refuses to start without it. (`make up-dev`
supplies a throwaway one for local development unless you set your own in the shell.)

The first build downloads dependencies; a cold build took about 2 minutes on a laptop with a
fast connection (timed 2026-10-20: `docker compose up -d --wait --build` with an empty build
cache, 129 s; images for Postgres, MinIO and Mailpit already pulled). The stack is ready when
every service reports healthy.

| Service | URL | Notes |
| --- | --- | --- |
| web | http://localhost:3000 | React shell; shows the API readiness status |
| app | http://localhost:8080 | Spring Boot API |
| Mailpit UI | http://localhost:8025 | Captures outgoing mail |

Probe URLs on the app: `/actuator/health/liveness` (process is up),
`/actuator/health/readiness` (process and Postgres; 503 while Postgres is down) and
`/actuator/health`.

The web image has no API URL baked in. It serves `/config.json` from the
`KANAKKU_API_BASE_URL` environment variable (default `http://localhost:8080`), so change it on the
`web` service, and set `KANAKKU_CORS_ALLOWED_ORIGINS` on the `app` service to the web origin
(no wildcard). Defaults for credentials are in `.env.example`; copy it to `.env` to override.

Session and CSRF cookies are Secure by default (`KANAKKU_AUTH_SECURE_COOKIES=true`), so a
deployment should sit behind TLS (set `KANAKKU_TRUSTED_PROXIES` for the proxy). Chrome and Firefox
still accept them from `http://localhost`. For plain HTTP on another host, in Safari, or for the
Playwright e2e run (`cd e2e && npm test`), start the stack with the development override:
`make up-dev` (`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --wait`).

MinIO images are no longer published by MinIO; compose uses the `pgsty/minio` fork.

### Develop and test

```sh
./mvnw -B -q test             # backend JUnit (needs Docker for Testcontainers)
./mvnw -B -q spotless:check   # Java formatting
cd web && npm ci && npm test  # Vitest
cd web && npm run lint        # Biome
cd web && npm run dev         # Vite dev server on :5173 (API on :8080)
```

The backend version lives only in `.mvn/maven.config`; the web version only in
`web/package.json`.

### Required branch checks

CI (`.github/workflows/ci.yml`) has `backend`, `web` and `compose-smoke` jobs. In the repository
settings, protect `main` and require the `backend` and `web` checks on every pull request.

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
