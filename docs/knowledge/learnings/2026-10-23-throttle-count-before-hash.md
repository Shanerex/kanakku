tags: [identity, throttle, concurrency]
Hash Argon2id outside any transaction: holding the pooled connection and advisory lock through it stalls the DB under parallel sign-ins.
Fix pattern: short tx takes the per-(email, IP) lock, checks the throttle and counts the try up front; a correct password deletes its own row.
Open follow-ups: unknown-email limiter permit leaks if the commit fails; its 503 reveals unknown emails.
