---
paths:
  - "web/**"
  - "e2e/**"
---
# Web client rules

- React + Vite + TypeScript SPA, built into the app's static resources. Call the API only through the client generated from the app's OpenAPI spec; never hand-write fetch calls to `/api/v1`.
- No model keys, OIDC secrets or tokens in the bundle, `localStorage` or console output. Sessions are HttpOnly SameSite cookies; send the CSRF token on writes.
- Send an `Idempotency-Key` on every create and the entity version on every edit. Show RFC 9457 problem details to the user in plain words.
- Format amounts from minor units using the currency's minor-unit digits; never do money maths in floating point on the client.
- AI results show as drafts with a clear confirm step; nothing AI-made looks posted before a member confirms.
- WCAG 2.1 AA: full keyboard use, visible focus, screen-reader labels on amounts and split controls. Core pages work at 360 px wide.
- Each screen gets Vitest tests; user flows get a Playwright test under `e2e/`.
