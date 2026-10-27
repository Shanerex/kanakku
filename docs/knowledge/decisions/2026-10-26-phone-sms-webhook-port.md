tags: [identity, phone, sms, security]
Decision: SMS goes through a webhook port; the webhook may use plain http only for a loopback/private IP literal or a dotless name, and hosts are never resolved; redirects are off.
Why: no SMS vendor lock-in for self-hosters, and an admin-set URL must not become an SSRF or token leak path.
Consequence: no provider set means sign-up skips phone verification; per-number and per-IP caps bound SMS pumping.
