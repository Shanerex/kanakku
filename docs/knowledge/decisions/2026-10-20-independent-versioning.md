tags: [versioning, release]
Decision: backend and web are versioned independently.
Backend: ${revision} only in .mvn/maven.config, 0.1.0-SNAPSHOT; release overrides with -Drevision=0.1.0, then back to next -SNAPSHOT.
Web: version only in web/package.json (0.1.0-dev in development, private), lockfile must match; checked by scripts/check-version.
