---
paths:
  - "app/src/main/java/dev/kanakku/ai/**"
  - "app/src/main/java/dev/kanakku/drafts/**"
  - "app/src/main/java/dev/kanakku/imports/**"
  - "app/src/main/java/dev/kanakku/mcp/**"
  - "app/src/main/resources/prompts/**"
  - "app/src/test/java/dev/kanakku/ai/**"
  - "app/src/test/java/dev/kanakku/drafts/**"
  - "app/src/test/java/dev/kanakku/imports/**"
  - "app/src/test/java/dev/kanakku/mcp/**"
---
# AI and untrusted input rules

- AI output is always a draft. Only a member's confirm, through the `expenses` or `settlement` services, posts to the ledger. MCP writes are drafts too; there is no direct-post scope.
- Chat exports, receipt images and bank statement text are untrusted model input. Never put permission or authorization logic in a prompt; check permissions in code, before and after the model call.
- Model output is parsed into a schema and validated like a manual entry (amounts, currency, members in the group). Reject or flag what fails; never trust a model-produced ID.
- Amounts in an AI explanation must match the ledger trace; flag any amount that does not.
- Statement passwords are used in memory only and never persisted or logged. Raw statements and chat exports are deleted after parsing; settlements from statements are unique by UTR.
- Log every AI call with feature, model, tokens and requester, never with the prompt's secrets or the model key.
- Model keys never reach the browser, an API response or a log line.
- Tests use the fake provider. No network calls in tests or CI.
