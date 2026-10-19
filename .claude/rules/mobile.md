---
paths:
  - "mobile/**"
---
# Mobile app rules

- Kotlin Multiplatform + Compose Multiplatform, Ktor, SQLDelight. The API client is generated from the app's OpenAPI spec; do not hand-write endpoint calls.
- Sign-in uses the provider's native flow with PKCE, then exchanges the ID token for access and refresh tokens. Refresh tokens rotate; reusing an old one revokes the whole token family, so handle that by signing out cleanly.
- Tokens live only in the platform keystore (Android Keystore, iOS Keychain), never in SQLDelight, logs or crash reports.
- Offline writes go to the SQLDelight outbox with their `Idempotency-Key` fixed at creation, so a retry posts exactly once.
- Amounts are `Long` minor units plus a currency code; no `Double` or `Float` for money.
- Tests use kotlin.test in the common source set; iOS tests need Xcode and are skipped on Linux.
- The Maven `mobile` module runs `mobile/gradlew allTests`; keep Gradle the only build inside `mobile/`.
