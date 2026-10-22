# Kanakku design

The design for the whole Kanakku app: the React web app and the Compose Multiplatform apps for
Android and iOS. Read this before building any screen.

## Files

| File | What it holds |
| --- | --- |
| [tokens.json](tokens.json) | Every colour (light and dark), the identity palette, type, spacing, radius, shadow, motion and sizes. The single source. |
| [tokens.css](tokens.css) | CSS variables generated from `tokens.json`. Do not edit by hand. |
| [scripts/build-tokens.mjs](scripts/build-tokens.mjs) | Regenerates `tokens.css` and checks WCAG contrast for every colour pair in both themes. |
| [design-system.md](design-system.md) | Personality, colour roles, money colour rule, type, money formatting, layout, icons, identity, illustration, themes, accessibility, writing. |
| [components.md](components.md) | Every component, its variants and states, on web and mobile. |
| [patterns.md](patterns.md) | Navigation map, the Add sheet, drafts, forms, conflicts, undo, errors, empty states, loading, offline, permissions, responsive rules. |
| [screens/](screens/README.md) | One spec per area, mapped to PRD features and backlog rows. |
| [mocks/](mocks/index.html) | Static HTML mocks of key web screens and mobile frames, built on `tokens.css`. |

## How to use it when building a feature

1. Find the screen spec for the backlog row in [screens/README.md](screens/README.md).
2. Build from the spec, using components from [components.md](components.md) and behaviour from
   [patterns.md](patterns.md). The mock is a visual reference; the spec wins on any conflict.
3. Use tokens only. Web: copy or import `tokens.css` into `web/src/` and map it in the Tailwind
   theme. Mobile: generate `KanakkuTheme` from `tokens.json` (a Gradle task in `mobile/`).
4. If a feature needs something the design doesn't cover, add it to the spec in the same change
   and say so in the feature's `plan.md`.

## Changing tokens

Edit `tokens.json`, then:

```bash
node docs/design/scripts/build-tokens.mjs
```

`--check` fails when `tokens.css` is stale or a contrast pair drops below WCAG AA; run it in CI
once the web job picks it up.

## Viewing the mocks

```bash
python3 -m http.server 4400 --bind 127.0.0.1 --directory docs/design
```

Then open http://127.0.0.1:4400/mocks/. Each mock has a theme switch. The mocks load fonts and
icons from Google Fonts and unpkg for convenience; the app self-hosts both (see
`docs/knowledge/decisions/2026-10-21-ofl-fonts.md`).

## Decisions behind this design (2026-10-21)

- Playful and colourful; violet `#6D4AFF` brand; Baloo 2 for display and Nunito for text.
- Light and dark from day one, following the system, with a manual override.
- One Kanakku look on web, Android and iOS; platform conventions only for system behaviour.
- Home shows balances first, then groups. Web uses a left sidebar; phones use bottom tabs with a
  centre Add button.
- One Add button opens a text-first sheet: describe it, scan, import a chat, or use the form.
- Balances always use sign, words and colour together; owing is coral, not red.
- Groups have a colour and an emoji; people have hashed identity colours; categories have fixed
  icons and colours.
- Shape-based SVG illustrations and a small celebration on "all settled"; no mascot.
- Web styling: Tailwind CSS + Radix primitives. Icons: Phosphor (MIT).
- English first, every string in a message catalog, Indian digit grouping.

## Open questions

- The share link lets a guest tap "I've paid", which creates a draft settlement for the payee to
  confirm. The PRD calls the link read-only; confirm this small write is wanted (row 15).
- Remind ("gentle nudge") on the settle-up screen is not in the PRD; it reuses F-11
  notifications. Keep or drop (row 8 or 13).
