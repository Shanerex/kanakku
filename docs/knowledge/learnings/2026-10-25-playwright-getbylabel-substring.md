tags: [e2e, playwright]
Playwright `getByLabel("Invite link")` matches substrings, so it also hit the "Invite links" section; use `{ exact: true }` when one label prefixes another.
