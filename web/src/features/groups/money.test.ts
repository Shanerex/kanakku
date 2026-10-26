import { describe, expect, it } from "vitest";
import { balanceWords, formatMoney, minorUnitDigits } from "./money";

describe("minorUnitDigits", () => {
  it("follows ISO 4217, not Intl", () => {
    expect(minorUnitDigits("INR")).toBe(2);
    expect(minorUnitDigits("JPY")).toBe(0);
    expect(minorUnitDigits("KWD")).toBe(3);
    // CLDR says 0 for these, ISO 4217 (and the server) say 3 and 2.
    expect(minorUnitDigits("IQD")).toBe(3);
    expect(minorUnitDigits("LBP")).toBe(2);
  });
});

describe("formatMoney", () => {
  it("shows INR with two digits", () => {
    expect(formatMoney(4500, "INR")).toContain("45.00");
  });

  it("shows a three-digit currency the way the server counts it", () => {
    // 1500 minor units of IQD is 1.500, not 1,500.
    expect(formatMoney(1500, "IQD")).toContain("1.500");
    expect(formatMoney(1500, "IQD")).not.toContain("1,500");
  });

  it("shows a two-digit currency that Intl would show whole", () => {
    expect(formatMoney(12345, "LBP")).toContain("123.45");
  });

  it("shows a zero-digit currency without a fraction", () => {
    expect(formatMoney(1500, "JPY")).toContain("1,500");
  });

  it("keeps the sign and exact digits beyond float range", () => {
    expect(formatMoney(-4500, "INR")).toMatch(/^-/);
    expect(formatMoney(Number.MAX_SAFE_INTEGER, "INR")).toContain(
      "9,00,71,99,25,47,409.91",
    );
  });
});

describe("balanceWords", () => {
  it("says who owes and who is owed", () => {
    expect(balanceWords(-4500, "INR")).toMatch(/^owes /);
    expect(balanceWords(4500, "INR")).toMatch(/^is owed /);
  });
});
