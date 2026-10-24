import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const SRC = join(__dirname, "..");

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : [path];
  });
}

describe("design tokens", () => {
  it("web/src/app/tokens.css is the copy of docs/design/tokens.css", () => {
    const docs = readFileSync(
      join(SRC, "../../docs/design/tokens.css"),
      "utf8",
    );
    const copy = readFileSync(join(SRC, "app/tokens.css"), "utf8");
    expect(copy).toBe(docs);
  });

  it("screens and components use no raw colours or ad-hoc fonts", () => {
    const sources = files(SRC).filter(
      (f) =>
        /\.(tsx|ts)$/.test(f) &&
        !f.endsWith(".test.ts") &&
        !f.endsWith(".test.tsx") &&
        !f.endsWith("schema.d.ts"),
    );
    for (const file of sources) {
      const text = readFileSync(file, "utf8");
      expect(text, file).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
      expect(text, file).not.toMatch(/\b(rgb|rgba|hsl|hsla)\(/);
      expect(text, file).not.toMatch(/font-family/i);
    }
  });
});
