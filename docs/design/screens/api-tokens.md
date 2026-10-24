# API tokens and MCP (F-20, row 22) — outline

`/me/tokens`.

- Intro card: "Let Claude or another assistant read your balances and draft expenses. Drafts
  still need a member to confirm." Link "How to connect" (disclosure with the MCP server URL
  in a mono CopyField and a sample client config block).
- Token list: name, scope ("Goa Trip · read and draft" / "All groups · read only"), created,
  last used, expires; Revoke (ConfirmDialog: "Revoke *Laptop*? Anything using it stops
  working immediately.").
- **New token** sheet: Name (required, "Laptop Claude"), Groups (All my groups / pick one),
  Access radio (Read only / Read and draft — with the caption "Draft only: tokens can never
  post to the ledger directly"), Expiry (30 / 90 / 365 days, no "never").
- After creation: the token shown once in a mono field with Copy and the warning "Copy it now.
  You won't see it again." Closing requires an explicit "I've copied it".

Everywhere a token acted, it is named: draft source "From Claude via token *Laptop*", activity
"*Laptop* token drafted…".
