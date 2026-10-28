import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AuthLayout } from "./AuthLayout";

afterEach(cleanup);

describe("AuthLayout", () => {
  it("shows the wordmark, tagline and its children", () => {
    const { container } = render(
      <AuthLayout>
        <h1>Welcome back</h1>
      </AuthLayout>,
    );
    expect(screen.getByRole("heading", { name: "Welcome back" })).toBeTruthy();
    expect(screen.getByText(/Split trips, flats and dinners/)).toBeTruthy();
    expect(container.textContent).toContain("kanakku");
  });

  it("hides the decorative blobs from assistive tech", () => {
    const { container } = render(<AuthLayout>x</AuthLayout>);
    expect(container.querySelector("svg")?.getAttribute("aria-hidden")).toBe(
      "true",
    );
  });

  it("draws a 400 px bordered card with the mock's padding, large shadow and mango wordmark dot", () => {
    const { container } = render(<AuthLayout>x</AuthLayout>);
    const card = container.querySelector("main > div");
    for (const cls of [
      "max-w-[400px]",
      "w-full",
      "px-7",
      "py-8",
      "border",
      "border-border",
      "shadow-lg",
      "bg-surface",
      "rounded-lg",
    ])
      expect(card?.className, cls).toContain(cls);
    const dot = container.querySelector("header span span");
    expect(dot?.textContent).toBe(".");
    expect(dot?.className).toContain("--id-mango-solid");
  });

  it("is a single-column card with 16 px gutters on a phone", () => {
    const { container } = render(<AuthLayout>x</AuthLayout>);
    expect(container.querySelector("main")?.className).toContain("px-4");
  });
});
