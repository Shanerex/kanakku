import { describe, expect, it } from "vitest";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  fieldMessages,
  formatWait,
} from "./problems";

const p = (slug: string, extra: Record<string, unknown> = {}) => ({
  type: `urn:kanakku:problem:${slug}`,
  title: "x",
  status: 400,
  ...extra,
});

describe("formatWait", () => {
  it("rounds minutes up and uses singulars", () => {
    expect(formatWait(600)).toBe("10 minutes");
    expect(formatWait(481)).toBe("9 minutes");
    expect(formatWait(60)).toBe("1 minute");
    expect(formatWait(59)).toBe("59 seconds");
    expect(formatWait(1)).toBe("1 second");
  });
});

describe("describeProblem", () => {
  it("says sign-in failed without saying which part", () => {
    expect(describeProblem(p("invalid-credentials")).body).toBe(
      "That email and password don't match.",
    );
  });

  it("gives the wait for a throttled sign-in", () => {
    const words = describeProblem(
      p("too-many-attempts", { retryAfterSeconds: 480 }),
    );
    expect(words.title).toBe("Too many tries");
    expect(words.body).toBe("Try again in 8 minutes.");
  });

  it("explains a taken email", () => {
    expect(describeProblem(p("email-taken")).title).toMatch(
      /already has an account/,
    );
  });

  it("explains a version conflict in plain words", () => {
    expect(describeProblem(p("version-conflict")).body).toMatch(
      /changed somewhere else/,
    );
  });

  it("falls back to server-error words for unknown 5xx and generic words otherwise", () => {
    expect(describeProblem({ status: 503 }).title).toMatch(/our side/);
    expect(
      describeProblem({ type: "urn:other", status: 400 }).body,
    ).toBeTruthy();
    expect(describeProblem(undefined).body).toBeTruthy();
  });

  it("never shows a type URI, a status code or the server detail", () => {
    for (const slug of [
      "invalid-credentials",
      "too-many-attempts",
      "email-taken",
      "version-conflict",
      "validation",
      "idempotency-key-reuse",
      "csrf-token-invalid",
      "unauthenticated",
      "forbidden",
      "internal",
      "nonsense",
    ]) {
      const words = describeProblem(
        p(slug, { detail: "SECRET-DETAIL", retryAfterSeconds: 90 }),
      );
      const text = `${words.title ?? ""} ${words.body}`;
      expect(text).not.toMatch(/urn:|SECRET-DETAIL|\b[45]\d\d\b/);
    }
    expect(`${describeNetworkError().body}`).not.toMatch(/fetch|TypeError/);
  });
});

describe("fieldMessages", () => {
  it("keeps the first message per field", () => {
    expect(
      fieldMessages({
        errors: [
          { field: "email", message: "Bad email" },
          { field: "email", message: "Other" },
          { field: "password", message: "Too short" },
        ],
      }),
    ).toEqual({ email: "Bad email", password: "Too short" });
  });

  it("is empty without errors", () => {
    expect(fieldMessages(undefined)).toEqual({});
  });
});

describe("asProblem", () => {
  it("accepts objects with a type and rejects the rest", () => {
    expect(asProblem(p("x"))).toBeTruthy();
    expect(asProblem("text")).toBeUndefined();
    expect(asProblem(undefined)).toBeUndefined();
  });
});
