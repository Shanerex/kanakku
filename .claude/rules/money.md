---
paths:
  - "app/src/main/java/dev/kanakku/ledger/**"
  - "app/src/main/java/dev/kanakku/expenses/**"
  - "app/src/main/java/dev/kanakku/settlement/**"
  - "app/src/main/java/dev/kanakku/fx/**"
  - "app/src/test/java/dev/kanakku/ledger/**"
  - "app/src/test/java/dev/kanakku/expenses/**"
  - "app/src/test/java/dev/kanakku/settlement/**"
  - "app/src/test/java/dev/kanakku/fx/**"
---
# Money rules

- An amount is a `long` in minor units plus an ISO 4217 currency code. `double`, `float`, `Double` and `Float` never hold money, not even in a DTO, test or log line.
- Minor-unit digits come from the currency (JPY 0, INR 2, KWD 3); never assume 2.
- Conversion: `BigDecimal` all the way, rounded exactly once to the target currency's minor unit, using the rate locked on the expense. No silent 1:1 fallback when a rate is missing; fail instead.
- Splits sum exactly to the expense. Distribute remainders by largest remainder, ties broken by stable member order, so the same input always gives the same split.
- The journal is append-only. An edit or delete posts a reversal plus a new entry; never UPDATE or DELETE journal rows.
- Journal post and balance update happen in one transaction. Each group's balances sum to zero per currency.
- Only the `expenses` and `settlement` services post to the ledger; other modules go through their API.
- Every money rule has a jqwik property test (sum preserved, zero-sum per currency, reversal restores balances, rounding is deterministic).
