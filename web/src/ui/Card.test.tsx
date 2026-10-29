import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Card } from "./Card";

afterEach(cleanup);

describe("Card", () => {
  it("is a surface with the large radius, small shadow, an edge and 20 px padding", () => {
    const { container } = render(<Card>x</Card>);
    const cls = container.firstElementChild?.className ?? "";
    for (const c of [
      "bg-surface",
      "rounded-lg",
      "shadow-sm",
      "border",
      "border-border",
      "p-5",
    ])
      expect(cls).toContain(c);
  });

  it("can be flush so list rows reach the edge, and renders as another element", () => {
    const { container } = render(
      <Card as="section" flush aria-label="Settings">
        x
      </Card>,
    );
    const el = container.firstElementChild;
    expect(el?.tagName).toBe("SECTION");
    expect(el?.className).toContain("overflow-hidden");
    expect(el?.className).not.toContain("p-5");
  });
});
