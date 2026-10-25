tags: [groups, invites, security, idempotency]
Decision: invite token = HMAC-SHA256(KANAKKU_INVITE_TOKEN_SECRET, invite id); only its SHA-256 is stored.
Why: the create's idempotency record is stored verbatim, so a random token would sit in clear in the database; now the record holds the invite only and a replay recomputes the same link.
Consequence: the secret must be 32+ chars with no default in application.yml; changing it invalidates unclaimed links.
