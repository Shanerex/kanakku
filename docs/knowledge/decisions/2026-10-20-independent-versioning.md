tags: [versioning, release]
Decision: backend and web are versioned independently.
Backend: ${revision} only in .mvn/maven.config as a -SNAPSHOT value; a release overrides it with -Drevision, then returns to the next -SNAPSHOT.
Web: version only in web/package.json (0.1.0-dev in development, private), lockfile must match; checked by scripts/check-version.
