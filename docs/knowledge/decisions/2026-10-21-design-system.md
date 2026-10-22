tags: [design, web, mobile]
Decision: one playful look on web, Android and iOS, specified in docs/design/ (tokens.json is the single source of colours and sizes).
Why: chosen with the human on 2026-10-21: violet brand, light + dark, Tailwind + Radix on web, one Compose theme on mobile, sidebar web / bottom tabs mobile.
Consequence: UI work reads docs/design/screens/<area>.md first; colour changes go through tokens.json and build-tokens.mjs, never raw hex.
