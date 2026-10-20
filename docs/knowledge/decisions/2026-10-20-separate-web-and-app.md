tags: [build, web, packaging]
Decision: web/ is a plain npm project on its own port; app is an API-only Spring Boot jar with configurable CORS.
Why: independent run and deploy; overrides PRD F-01 and CLAUDE.md wording (one jar, frontend-maven-plugin).
Web image is nginx-unprivileged; the API URL is read at runtime from /config.json (KANAKKU_API_BASE_URL).
Consequence: ./mvnw test and spotless cover the backend only; Vitest and Biome run via npm in a separate CI job.
