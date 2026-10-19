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
