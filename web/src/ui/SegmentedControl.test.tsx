import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { SegmentedControl } from "./SegmentedControl";

afterEach(cleanup);

function Harness() {
  const [value, setValue] = useState("active");
  return (
    <>
      <SegmentedControl
        label="Show"
        value={value}
        onChange={setValue}
        options={[
          { value: "active", label: "Active" },
          { value: "archived", label: "Archived" },
        ]}
      />
      <p>{`picked ${value}`}</p>
    </>
  );
}

describe("SegmentedControl", () => {
  it("is a labelled radio group with the current option checked", () => {
    render(<Harness />);
    expect(screen.getByRole("radiogroup", { name: "Show" })).toBeTruthy();
    expect(
      (screen.getByRole("radio", { name: "Active" }) as HTMLInputElement)
        .checked,
    ).toBe(true);
  });

  it("changes with a click and with the arrow keys", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByText("Archived"));
    expect(screen.getByText("picked archived")).toBeTruthy();
    screen.getByRole("radio", { name: "Archived" }).focus();
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByText("picked active")).toBeTruthy();
  });

  it("marks the selected segment on a raised surface", () => {
    render(<Harness />);
    const on = screen.getByText("Active").closest("label");
    expect(on?.className).toContain("bg-surface");
    expect(on?.className).toContain("shadow-sm");
    expect(
      screen.getByText("Archived").closest("label")?.className,
    ).not.toContain("shadow-sm");
  });
});
