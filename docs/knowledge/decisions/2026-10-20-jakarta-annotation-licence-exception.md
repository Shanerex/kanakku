tags: [licence, dependencies]
Decision: accept jakarta.annotation-api 3.0.0 (EPL-2.0 OR GPL-2.0+CPE) as a transitive runtime dependency of Spring Boot.
Why: required by Spring Boot at runtime; excluding it risks startup failure. Human approved the exception on 2026-10-20.
Dev-only npm licences (MPL-2.0, ISC, BlueOak) are not in the bundle and are also tolerated.
