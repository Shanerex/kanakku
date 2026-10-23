import { describe, expect, it } from "vitest";
import { fnv1a32, identityColor, initials } from "./Avatar";

describe("avatar identity", () => {
  it("hashes with FNV-1a 32", () => {
    expect(fnv1a32("")).toBe(0x811c9dc5);
    expect(fnv1a32("a")).toBe(0xe40c292c);
    expect(fnv1a32("foobar")).toBe(0xbf9cf968);
  });

  it("picks the same colour for the same id every time", () => {
    const id = "11111111-1111-4111-8111-111111111111";
    expect(identityColor(id)).toBe(identityColor(id));
    expect([
      "violet",
      "grape",
      "pink",
      "coral",
      "tangerine",
      "mango",
      "lime",
      "mint",
      "teal",
      "sky",
    ]).toContain(identityColor(id));
  });

  it("uses the first letter of the first two words", () => {
    expect(initials("Priya Raman")).toBe("PR");
    expect(initials("Arun")).toBe("A");
    expect(initials("  mary jane watson ")).toBe("MJ");
  });
});
