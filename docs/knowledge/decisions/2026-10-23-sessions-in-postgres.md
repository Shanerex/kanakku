tags: [identity, sessions, security]
Decision: web sessions use Spring Session JDBC (Postgres), cookie SESSION, HttpOnly, SameSite=Lax, 14-day idle expiry, revoked server-side on sign-out.
Why: no Redis; a stateless JWT cookie cannot be revoked (rejected). Passwords use Argon2id (OWASP minimum params) in their own table.
