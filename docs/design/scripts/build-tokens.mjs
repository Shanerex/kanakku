#!/usr/bin/env node
// Builds docs/design/tokens.css from docs/design/tokens.json and checks colour contrast.
// Usage: node docs/design/scripts/build-tokens.mjs [--check]
//   (no flag)  write tokens.css, then run the contrast checks
//   --check    fail if tokens.css is stale or any contrast pair is below its minimum
// The web app copies tokens.css (or imports it) and the mobile app generates its Compose
// theme from tokens.json; neither edits colours by hand.

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const designDir = join(here, "..");
const tokens = JSON.parse(readFileSync(join(designDir, "tokens.json"), "utf8"));
const outPath = join(designDir, "tokens.css");
const check = process.argv.includes("--check");

const flat = (prefix, obj) =>
  Object.entries(obj)
    .filter(([k]) => !k.startsWith("$"))
    .map(([k, v]) => `  --${prefix}-${String(k).replace(".", "_")}: ${v};`);

const colorVars = (theme) => flat("color", tokens.color[theme]);
const shadowVars = (theme) => flat("shadow", tokens.shadow[theme]);
const paletteVars = (theme) =>
  Object.entries(tokens.palette)
    .filter(([k]) => !k.startsWith("$"))
    .flatMap(([name, p]) => [
      `  --id-${name}-solid: ${p.solid};`,
      `  --id-${name}-tint: ${theme === "dark" ? p["tint-dark"] : p.tint};`,
      `  --id-${name}-ink: ${theme === "dark" ? p["ink-dark"] : p.ink};`,
    ]);

const themeBlock = (theme) => [
  ...colorVars(theme),
  ...shadowVars(theme),
  ...paletteVars(theme),
];

const css = [
  "/* Generated from tokens.json by scripts/build-tokens.mjs. Do not edit by hand. */",
  ":root {",
  "  color-scheme: light dark;",
  ...flat("font", tokens.font.family),
  ...flat("weight", tokens.font.weight),
  ...flat("text", tokens.font.size),
  ...flat("leading", tokens.font["line-height"]),
  ...flat("space", tokens.space),
  ...flat("radius", tokens.radius),
  ...flat("duration", tokens.motion.duration),
  ...flat("ease", tokens.motion.easing),
  ...flat("size", tokens.size),
  ...flat("z", tokens.z),
  ...themeBlock("light"),
  "}",
  "@media (prefers-color-scheme: dark) {",
  '  :root:not([data-theme="light"]) {',
  ...themeBlock("dark").map((l) => `  ${l}`),
  "  }",
  "}",
  ':root[data-theme="dark"] {',
  ...themeBlock("dark"),
  "}",
  "@media (prefers-reduced-motion: reduce) {",
  "  :root {",
  "    --duration-fast: 0ms;",
  "    --duration-base: 0ms;",
  "    --duration-slow: 0ms;",
  "    --duration-celebrate: 0ms;",
  "  }",
  "}",
  "",
].join("\n");

// WCAG 2.1 relative luminance and contrast ratio, for opaque hex colours.
const lum = (hex) => {
  const n = hex.replace("#", "");
  const [r, g, b] = [0, 2, 4].map((i) => {
    const c = parseInt(n.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

// [foreground, background, minimum]. 4.5 for text, 3 for large text, icons and UI edges.
const pairs = [
  ["text", "bg", 4.5], ["text", "surface", 4.5], ["text-muted", "bg", 4.5],
  ["text-muted", "surface", 4.5], ["text-faint", "surface", 3],
  ["text-on-primary", "primary", 4.5], ["primary-text", "surface", 4.5],
  ["primary-text", "bg", 4.5], ["focus-ring", "surface", 3],
  ["positive-text", "surface", 4.5], ["positive-text", "positive-soft", 4.5],
  ["negative-text", "surface", 4.5], ["negative-text", "negative-soft", 4.5],
  ["draft-text", "draft-soft", 4.5], ["draft-text", "surface", 4.5],
  ["danger-text", "surface", 4.5], ["danger-text", "danger-soft", 4.5],
  ["info-text", "info-soft", 4.5], ["border-strong", "surface", 1.5],
];

const failures = [];
for (const theme of ["light", "dark"]) {
  const c = tokens.color[theme];
  for (const [fg, bg, min] of pairs) {
    const r = ratio(c[fg], c[bg]);
    if (r < min) failures.push(`${theme}: ${fg} on ${bg} is ${r.toFixed(2)}, needs ${min}`);
  }
  for (const [name, p] of Object.entries(tokens.palette)) {
    if (name.startsWith("$")) continue;
    const tint = theme === "dark" ? p["tint-dark"] : p.tint;
    const ink = theme === "dark" ? p["ink-dark"] : p.ink;
    const r = ratio(ink, tint);
    if (r < 4.5) failures.push(`${theme}: palette ${name} ink on tint is ${r.toFixed(2)}, needs 4.5`);
    const s = ratio(p.solid, c.surface);
    if (s < 3) failures.push(`${theme}: palette ${name} solid on surface is ${s.toFixed(2)}, needs 3`);
  }
}

if (check) {
  const current = existsSync(outPath) ? readFileSync(outPath, "utf8") : "";
  if (current !== css) failures.push("tokens.css is stale; run node docs/design/scripts/build-tokens.mjs");
} else {
  writeFileSync(outPath, css);
  console.log(`wrote ${outPath}`);
}

if (failures.length) {
  console.error(failures.join("\n"));
  process.exit(1);
}
console.log("contrast checks passed");
