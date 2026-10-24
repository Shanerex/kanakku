# Kanakku design system

The rules every Kanakku screen follows, on web (React) and on the Android and iOS apps
(Compose Multiplatform). Values live in [tokens.json](tokens.json); this file says how to use
them. Components are in [components.md](components.md), cross-screen behaviour in
[patterns.md](patterns.md), and each screen in [screens/](screens/).

## Personality

Playful and colourful, for friends who split a Goa trip, not for an accounts department.

- **Friendly, never flippant about money.** Colour, emoji and bouncy motion live around the
  numbers; the numbers themselves are calm, exact and always legible.
- **Every group feels like its own place.** A group picks a colour and an emoji; that identity
  tints its header, its cards and its avatar badge everywhere it appears.
- **Owing is not shameful.** "You owe" uses coral, not alarm red, and always comes with words.
- **AI is a helpful friend, not an oracle.** Anything AI-made is visibly a draft (amber,
  dashed, sparkle) until a member confirms it.
- **One look everywhere.** Web, Android and iOS share tokens, components and layouts. Mobile
  follows platform conventions only for system behaviour: back gestures, safe areas, the
  keyboard, share sheets, haptics and system dialogs.

## Colour

### Semantic roles

Use semantic tokens (`--color-*` on web, `KanakkuTheme.colors.*` in Compose). Never use a raw
hex value or a palette colour for a semantic meaning.

| Role | Tokens | Use |
| --- | --- | --- |
| Canvas | `bg`, `surface`, `surface-sunken`, `surface-raised` | Page background; cards and sheets; inset wells (inputs, chips at rest); menus and popovers |
| Text | `text`, `text-muted`, `text-faint` | Primary text; secondary labels and metadata; placeholders and disabled (never for meaning) |
| Brand | `primary`, `primary-hover`, `primary-pressed`, `primary-soft`, `primary-text` | Primary buttons, the Add FAB, selected nav, links (`primary-text`), selected chips (`primary-soft`) |
| Owed to you | `positive`, `positive-soft`, `positive-text` | "owes you", "you are owed", "settled" success |
| You owe | `negative`, `negative-soft`, `negative-text` | "you owe" |
| Draft / AI | `draft`, `draft-soft`, `draft-text` | Draft chips, draft card borders, low-confidence field markers |
| Danger | `danger`, `danger-soft`, `danger-text` | Destructive actions (delete, revoke, remove member) and errors |
| Info | `info`, `info-soft`, `info-text` | Neutral notices: offline, sync, rate unavailable |
| Lines | `border`, `border-strong`, `focus-ring` | Dividers and card edges; input edges; keyboard focus |

`negative` (coral, you owe) and `danger` (red, destructive or error) are different meanings.
Do not use `danger` for a debt or `negative` for an error.

### Identity palette

Ten colours (`violet`, `grape`, `pink`, `coral`, `tangerine`, `mango`, `lime`, `mint`, `teal`,
`sky`) for groups, people and categories. Each has `solid`, `tint` and `ink` (tint and ink
switch with the theme).

- Text and icons on an identity colour are always `ink` on `tint`. `solid` is for chart marks,
  accent bars, icons on a plain surface and borders, never behind text.
- **Groups** choose a colour when created (default: next unused in palette order).
- **People** get a colour from a stable hash: `palette[fnv1a32(userId) % 10]` for registered
  users, so a person has the same colour in every group and on every device; guests use their
  member ID. A guest who claims an invite takes their user colour from then on.
- **Categories** have fixed colours (table under Iconography).

### Money colour rule

Colour is never the only signal. Every balance shows **sign, words and colour** together:

| State | Words | Sign | Colour |
| --- | --- | --- | --- |
| They owe you | "owes you ₹2,340" / "you are owed ₹2,340" | `+` in summaries | `positive-text` |
| You owe them | "you owe ₹1,200" | `−` (U+2212) in summaries | `negative-text` |
| Settled | "settled up" | none | `text-muted` with a check icon |
| Draft amount | "draft" chip beside it | as above | `draft-text` chip; amount itself stays `text` |

Expense amounts in lists (what an expense cost) are neutral `text`; only *your share of the
balance* is coloured. The line under an expense reads "you lent ₹800" (positive) or
"you borrowed ₹400" (negative) or "not involved" (faint).

## Typography

Two self-hosted SIL OFL families (see `docs/knowledge/decisions/2026-10-21-ofl-fonts.md`):

- **Baloo 2** (display): screen titles, group names in headers, the big balance number,
  empty-state headlines. Weights 600–800. Its siblings Baloo Thambi 2 (Tamil), Baloo Chettan 2
  (Malayalam) and Baloo 2 itself (Devanagari) cover future locales with the same shapes.
- **Nunito** (body): everything else, including every amount below the hero size. Rounded,
  friendly, readable at 12 px, and it has tabular figures.

| Style | Size / line | Font, weight | Use |
| --- | --- | --- | --- |
| display | 40 / 44 | Baloo 2, 800 | Hero balance on Home and group header |
| h1 | 28 / 34 | Baloo 2, 700 | Screen title |
| h2 | 22 / 28 | Baloo 2, 700 | Section title, sheet title |
| h3 | 18 / 24 | Nunito, 800 | Card title, list group header |
| body | 16 / 24 | Nunito, 400 / 600 | Default text, list primary line (600) |
| body-sm | 14 / 20 | Nunito, 400 / 600 | List secondary line, metadata, helper text |
| caption | 12 / 16 | Nunito, 700, +0.02em | Chips, badges, tab labels, timestamps |
| amount-xl | 40 | Baloo 2, 800, tabular | Hero balance |
| amount-lg | 24 | Nunito, 800, tabular | Card totals, settle-up amount, form amount |
| amount-md | 16 | Nunito, 700, tabular | List amounts |
| amount-sm | 14 | Nunito, 700, tabular | Secondary amounts ("you lent ₹800") |

- All amounts use `font-variant-numeric: tabular-nums` (Compose: `fontFeatureSettings = "tnum"`)
  and right-align in lists so digits line up.
- Minimum text size 12 px; body text never below 14 px. Support OS text scaling to 200%:
  layouts wrap, never truncate an amount.
- Sentence case everywhere ("Settle up", not "Settle Up"). No all-caps except 12 px captions
  in tab bars, and even there prefer sentence case.

## Money formatting

One formatter, shared by spec across web (`Intl.NumberFormat`) and mobile (a common Kotlin
formatter). Input is always `long` minor units plus an ISO 4217 code; never a float.

- Digits after the separator equal the currency's minor-unit digits: INR 2, JPY 0, KWD 3.
- Indian grouping for INR and for any user whose locale is `en-IN`: `₹1,23,456.50`.
  Other currencies use the user's locale grouping: `$123,456.50`, `¥12,346`.
- Hide `.00` only in the hero balance and charts (`₹2,340`); lists, forms, splits and
  settlements always show full minor units (`₹2,340.00`) so splits visibly sum.
- Symbol before the number with no space for INR, USD, EUR, GBP; code after for others
  without a common symbol (`1,250.000 KWD`). A trip abroad shows the original amount first
  and the group-currency equivalent second, muted: `€42.00 · ≈ ₹3,812.65`.
- Negative balances use U+2212 minus, never a hyphen, and never parentheses.
- Screen readers get words: `aria-label="you owe Priya 1,200 rupees"` (Compose:
  `contentDescription`). Never let a screen reader read "minus rupee one two zero zero".

## Spacing, layout and shape

- 4 px grid; use `space` tokens only. Common rhythm: 16 px page gutter on phones, 24 px on
  tablet and desktop; 12 px between list rows' content; 24 px between sections.
- Breakpoints: `sm` 360 (smallest supported), `md` 768 (sidebar appears), `lg` 1024
  (group detail shows the balances panel beside the list), `xl` 1280 (content stays at
  `content-max` 960 px, centred in the space right of the sidebar).
- Radius: inputs and buttons `md` 12; cards `lg` 16; sheets and dialogs `xl` 24 (top corners
  only for bottom sheets); chips, FAB, avatars and pill buttons `pill`.
- Elevation is soft and violet-tinted: cards `sm`, menus and popovers `md`, sheets and dialogs
  `lg`, the FAB `fab`. In dark mode, raise with `surface-raised` first and shadow second.
- Touch targets ≥ 44 × 44 px on every platform, including icon buttons and chips; pad the hit
  area rather than enlarging the visual.

## Iconography

- **Phosphor Icons** (MIT): `@phosphor-icons/react` on web; the same SVGs converted to
  Compose `ImageVector`s in `mobile/`. Regular weight for UI, Fill weight for selected nav
  tabs and category badges, Duotone for empty-state accents.
- Sizes 16 / 20 / 24 (`size.icon-*`). Icons beside text match the text's line height.
- Every icon-only button has a text label for assistive tech and a tooltip on web.

Categories (fixed; the backend owns the list, the client owns icon and colour):

| Category | Phosphor icon | Colour |
| --- | --- | --- |
| Food and dining | `ForkKnife` | tangerine |
| Groceries | `Basket` | lime |
| Drinks | `BeerBottle` | mango |
| Cab and auto | `Taxi` | mango |
| Travel (flights, trains, buses) | `AirplaneTilt` | sky |
| Fuel | `GasPump` | coral |
| Stay | `Bed` | grape |
| Rent | `HouseLine` | violet |
| Utilities (power, water, internet) | `Lightning` | teal |
| Entertainment | `FilmSlate` | pink |
| Shopping | `ShoppingBag` | pink |
| Health | `FirstAidKit` | mint |
| Gifts | `Gift` | grape |
| Settlement (not a category; for payments) | `HandCoins` | mint |
| Other | `Receipt` | violet |

A category badge is a 36 px rounded-square (`radius.md`) in the category `tint` with the Fill
icon in `ink`.

## Identity: groups and people

- **Group badge**: rounded square (`radius.md`), group `tint`, the group's emoji centred.
  Sizes 28 (lists, sidebar), 48 (cards), 72 (group header). If the group has a cover image,
  the header shows it with a 40% `overlay` scrim and the badge on top; lists still use the
  emoji badge. Group types suggest a default emoji: Trip 🏖️, Home 🏠, Couple 💞, Event 🎉,
  Other 👥. Emoji render with the platform's emoji font; never ship an emoji font.
- **Avatar**: circle, person's identity `tint`, up to two initials in `ink`, Nunito 800.
  Initials are the first letter of the first two words of the display name (`Priya Raman`
  → `PR`; a one-word name gives one letter: `Arun` → `A`). Guests have a 1.5 px dashed
  `border-strong` ring and a tiny "guest" caption where space allows. "You" gets a 2 px
  `primary` ring in member pickers so the user finds themselves fast.
- **Avatar stack**: overlap by 30%, 2 px `surface` outline, max 4 then `+n`.

## Illustration and delight

Mascot-free, made of simple shapes: blobs, circles, coins, a paper receipt, a rupee note, a
suitcase, all drawn as inline SVG that reference identity and semantic tokens (so they theme
with light and dark mode and need no licence). Specs and the full list are in
[patterns.md](patterns.md#empty-states). Rules:

- Illustrations appear only in empty states, onboarding, the all-settled moment, and errors
  that block a whole screen. Never inside forms or lists.
- **Celebration**: when a group reaches all-settled or a balance hits zero after a settlement,
  play a 900 ms confetti burst of identity-coloured dots and a bounce on the check icon, with a
  light haptic on mobile. Plays once per event, never on page load, and not at all under
  reduced motion.
- **Micro-motion**: buttons scale to 0.97 on press (`fast`), sheets slide up with `enter`
  over `slow`, list rows fade in over `base`, the Add FAB rotates its plus to an × when open.
  Amounts never animate their digits (no count-up): numbers must be readable instantly.
- Respect `prefers-reduced-motion` (web) and the OS reduce-motion setting (mobile): no
  confetti, no bounce, cross-fades only.

## Themes

Light and dark from day one. Default follows the OS; Me → Appearance offers System, Light,
Dark (stored per device: `localStorage` on web, settings store on mobile; it is a display
preference, not account data). On web the root gets `data-theme="light|dark"` when the user
overrides. Every token has a dark value; components never branch on theme themselves.

## Accessibility (WCAG 2.1 AA)

- Contrast: text ≥ 4.5:1, large text and UI edges ≥ 3:1. `node docs/design/scripts/build-tokens.mjs --check`
  verifies every semantic pair and every identity ink-on-tint pair in both themes.
- Focus: 2 px `focus-ring` outline with 2 px offset on every interactive element, visible on
  keyboard focus only (`:focus-visible`). Never remove it.
- Keyboard (web): every flow works with keyboard alone. `/` focuses search, `n` opens Add,
  `Esc` closes sheets and dialogs, arrow keys move within tabs, radio groups and menus.
  Sheets and dialogs trap focus and return it to the trigger on close.
- Screen readers: amounts and balances carry worded labels (see Money formatting). Split
  controls announce member, share and running total ("Priya, 1 share, ₹400.00; total ₹1,200.00
  of ₹1,200.00"). Live regions announce toasts, sync status and validation summaries.
- Never rely on colour, emoji or icon alone. Emoji in group badges have the group name as
  their label; decorative illustrations are `aria-hidden`.
- Hit areas ≥ 44 px; spacing between adjacent targets ≥ 8 px.

## Writing

- Plain, warm, short. Second person: "You owe Priya ₹1,200.00", "Priya owes you".
- Use real names, never "User 2". Say "you" for the current user, everywhere, including lists
  ("You paid ₹1,200.00").
- Errors say what happened and what to do, in words; never show a status code or a problem
  type URI ([patterns.md](patterns.md#errors)).
- AI copy is modest: "Here's a draft. Check it before you add it." Never "Done!" for a draft.
- No guilt: "Settle up" not "Pay your debts"; reminders say "a gentle nudge".
- All strings go through the message catalog (web: one JSON per locale under `web/src/i18n/`;
  mobile: Compose Multiplatform resources). English ships first; layouts allow 40% longer
  strings without breaking.

## Platform notes

| Concern | Web | Android | iOS |
| --- | --- | --- | --- |
| Primary nav | Sidebar ≥ 768 px; bottom bar below | Bottom bar | Bottom bar (tab bar placement, Kanakku styling) |
| Add | Top-right Add button ≥ 768 px; centre FAB below | Centre FAB in the bottom bar | Same |
| Sheets | Centred dialog ≥ 768 px; bottom sheet below | Modal bottom sheet, drag handle | Same, with swipe-to-dismiss |
| Back | Browser back and in-app back arrow | System back gesture closes sheets first | Edge swipe back; sheets swipe down |
| Haptics | none | Light tick on confirm, settle, celebrate | Same (UIImpactFeedback light) |
| Share | Copy link + Web Share API where present | System share sheet | System share sheet |
| Date picker | Native `<input type="date">` styled | Compose date picker in Kanakku colours | Same |
