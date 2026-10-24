import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { InlineProblem } from "./InlineProblem";

afterEach(cleanup);

describe("InlineProblem", () => {
  it("announces the words as an alert", () => {
    render(
      <InlineProblem
        problem={{ title: "Too many tries", body: "Try again in 8 minutes." }}
      />,
    );
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("Too many tries");
    expect(alert.textContent).toContain("Try again in 8 minutes.");
  });

  it("works without a title", () => {
    render(
      <InlineProblem
        problem={{ body: "That email and password don't match." }}
      />,
    );
    expect(screen.getByRole("alert").textContent).toBe(
      "That email and password don't match.",
    );
  });
});
