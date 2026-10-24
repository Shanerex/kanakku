tags: [identity, security, availability]
Decision: unauthenticated Argon2 work (unknown-email sign-in, sign-up) is bounded by global hash slots (300 ms wait) plus a per-IP cap (IPv6 by /64, default 3 in flight). Over the cap is an immediate 429 `busy`, never a 503, and the same for known and unknown emails.
Why: a 503 only for unknown emails was an existence oracle; a long wait parked all Tomcat threads. Cookies are Secure by default; only docker-compose.dev.yml opts out.
Trade-off: users behind one shared IP can briefly 429 each other; behind a proxy without KANAKKU_TRUSTED_PROXIES the cap covers the whole server.
