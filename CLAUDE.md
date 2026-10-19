# Kanakku

Open-source, self-hostable, AI-native expense splitting for friends, trips and flatmates,
aimed at Indian groups that settle over UPI. AI drafts expenses and settlements from chats,
receipts and bank statements; a member confirms them. Product spec: `docs/Kanakku_PRD.md`.

Not: a payment provider (never moves money), a personal budgeting app, a hosted SaaS,
or a WhatsApp bot.

## Stack
- Java 25 LTS, Spring Boot 4, Spring Modulith, Maven multi-module with the wrapper.
- Postgres 17, Flyway, jOOQ; db-scheduler for jobs; MinIO for files; no Redis.
- Spring AI 2.0 (Anthropic, OpenAI-compatible, Ollama); Spring AI MCP server.
- Web: React + Vite + TypeScript SPA in `web/`, built into the app's static resources.
- Mobile: Kotlin Multiplatform + Compose in `mobile/` (Gradle, wrapped by a Maven module).
- Tests: JUnit 5, Testcontainers, jqwik, Vitest, Playwright, kotlin.test.

## Commands (available once F-01 maven-scaffold lands)
- Test: `./mvnw -B -q test` (JUnit, Vitest, and mobile tests via exec-maven-plugin)
- Lint: `./mvnw -B -q spotless:check` (Java, TypeScript, Kotlin)
- Run: `docker compose up` (app, Postgres, MinIO, Mailpit)

## Conventions
- Version is `${revision}` in every pom; the value lives only in `.mvn/maven.config`.
- REST under `/api/v1`, Bean Validation, RFC 9457 problem details.
- Structured JSON logs with request IDs. Flyway migrations are forward-only.
- Domain packages under `dev.kanakku` are Modulith modules; never touch another module's internals.
- Each feature ships its own web screen in the same change.
- License Apache-2.0; dependencies must be Apache, MIT, BSD or LGPL.

## Hard invariants
- Money is `long` minor units plus an ISO 4217 currency. Never `double` or `float`.
- Conversions use `BigDecimal` and round once to the currency's minor unit.
- Every split sums exactly to its expense; remainders by largest remainder, stable member order.
- Journal is append-only; edits post reversals. Group balances sum to zero per currency.
- Journal post and balance update happen in one transaction.
- Every create is idempotent by `Idempotency-Key`; edits use optimistic version checks.
- AI output is always a draft; only a member's confirm posts to the ledger. MCP writes are drafts.
- Every read is scoped to the caller's group membership inside the SQL query.
- Chat, receipt and statement text is untrusted model input; no permission logic in prompts.
- Statement passwords are never persisted; raw uploads are deleted after parsing.
- Model keys and secrets never reach the browser or a log line.
- Tests stub AI with a fake provider; no network in CI.

## rx workflow
- Workflow is `/rx-feature <name>` then `/rx-next`; the backlog order is in the PRD.
- rx is vendored in `.claude/` (agents, commands, skill, scripts); hooks are in `.claude/settings.json`.
- Specs live in `specs/<id>/`.
- File formats and caps are in the rx-spec skill.
