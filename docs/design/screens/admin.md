# Instance admin (F-02 part 2, F-14; rows 3, 16) — outline

`/admin`, visible in the sidebar (and Me on mobile) only to instance admins. Desktop-first;
works at 360 px but mobile apps link out to the web for admin.

Sections (left sub-nav on desktop, list on phone):

- **Sign-in providers**: Google, Microsoft, Apple, custom OIDC. Each row: status (On / Off /
  Needs attention), Configure. Configure sheet: client ID, client secret (write-only field:
  shows "Saved · ends in …4f2a" and a Replace button, never the value), tenant (Microsoft),
  team ID / key ID / private key upload (Apple) with "Client secret renews automatically;
  next on 3 Mar" and a `draft` warning 30 days before expiry. Custom OIDC: name, discovery URL
  (validated by fetching), scopes. "Test sign-in" button.
- **AI providers**: list of providers (Anthropic, OpenAI-compatible, Ollama) with model per
  feature (Quick add, Receipts (vision), Chat import, Explain) as Selects. Keys are write-only
  fields like above. "Test" runs a tiny call and shows latency. Switching applies immediately
  ("Saved. New requests use Claude Sonnet."). A master Switch "AI features on this instance".
- **Budgets**: default monthly token budget per user, rate limit per minute; per-user override
  table.
- **Usage**: table and MonthBars of AI calls by feature, model, tokens, latency, user; filter
  by month. CSV export. No prompt or response content is shown.
- **Instance**: base URL, email (SMTP) status with "Send test email", version, health of
  Postgres / MinIO / jobs, backup reminder text.
