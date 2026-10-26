tags: [idempotency, security, groups]
The idempotency table stores create responses verbatim, so any secret in a response (invite token) lands in the database; derive it or leave it out of the stored body.
Open: two crossing write-offs can deadlock (target locked before sorted absorbers); the compose file has a public dev default for the invite secret.
