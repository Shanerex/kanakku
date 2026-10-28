import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { SearchField } from "./SearchField";

afterEach(cleanup);

function Harness() {
  const [value, setValue] = useState("");
  return (
    <SearchField label="Search groups" value={value} onChange={setValue} />
  );
}

describe("SearchField", () => {
  it("is a labelled search box that clears with its button", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const box = screen.getByRole("searchbox", { name: "Search groups" });
    expect(screen.queryByRole("button", { name: "Clear search" })).toBeNull();
    await user.type(box, "goa");
    expect((box as HTMLInputElement).value).toBe("goa");
    await user.click(screen.getByRole("button", { name: "Clear search" }));
    expect((box as HTMLInputElement).value).toBe("");
    expect(document.activeElement).toBe(box);
  });
});
