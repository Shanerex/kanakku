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
});
