import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { groupRoutes } from "../../app/routes";
import { aGroup, GROUP_ID, Here, requestsTo } from "../../test/groups";
import { aProfile, problem, renderApp } from "../../test/harness";

afterEach(cleanup);

const screens = [
  { path: "/groups/:id", element: <Here /> },
  ...groupRoutes,
  { path: "/sign-in", element: <Here /> },
];
const signedIn = { "GET /api/v1/me": () => Response.json(aProfile) };
const create = {
  ...signedIn,
  "POST /api/v1/groups": () => Response.json(aGroup(), { status: 201 }),
};

describe("NewGroupPage", () => {
  it("titles the page and shows a live preview that follows the name, type and colour", async () => {
    const user = userEvent.setup();
    renderApp("/groups/new", screens, signedIn);
    expect(
      await screen.findByRole("heading", { level: 1, name: "New group" }),
    ).toBeTruthy();
    const preview = screen.getByRole("region", { name: "Preview" });
    expect(within(preview).getByText("Your group")).toBeTruthy();
    expect(within(preview).getByText(/🏖/)).toBeTruthy();

    await user.type(screen.getByLabelText("Name"), "Goa, Dec 2026");
    expect(within(preview).getByText("Goa, Dec 2026")).toBeTruthy();

    await user.click(screen.getByRole("radio", { name: "Home" }));
    expect(within(preview).getByText(/🏠/)).toBeTruthy();

    await user.click(screen.getByLabelText("Mango"));
    const band = preview.querySelector("[data-identity-band]") as HTMLElement;
    expect(band.style.background).toContain("--id-mango-tint");
  });

  it("offers the types as chips and ten named colour swatches in a radio group", async () => {
    renderApp("/groups/new", screens, signedIn);
    await screen.findByRole("heading", { level: 1, name: "New group" });
    for (const t of ["Trip", "Home", "Couple", "Other"])
      expect(screen.getByRole("radio", { name: t })).toBeTruthy();
    const colours = screen.getByRole("radiogroup", { name: "Colour" });
    expect(within(colours).getAllByRole("radio")).toHaveLength(10);
    expect(within(colours).getByRole("radio", { name: "Mango" })).toBeTruthy();
  });

  it("shows the chosen colour with a ring and a check, and the others without", async () => {
    const user = userEvent.setup();
    renderApp("/groups/new", screens, signedIn);
    await screen.findByRole("heading", { level: 1, name: "New group" });
    const colours = screen.getByRole("radiogroup", { name: "Colour" });
    expect(colours.querySelector("svg")).toBeNull();
    await user.click(within(colours).getByLabelText("Mango"));
    const mango = within(colours).getByLabelText("Mango").closest("label");
    expect(mango?.querySelector("svg")).toBeTruthy();
    expect(mango?.className).toContain("shadow-[0_0_0_2px");
    expect(mango?.getAttribute("style")).toContain("--id-mango-solid");
    expect(colours.querySelectorAll("svg")).toHaveLength(1);
  });

  it("checks the name before sending anything", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/groups/new", screens, signedIn);
    await user.click(
      await screen.findByRole("button", { name: "Create group" }),
    );
    expect(screen.getByText("Enter a name for the group.")).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByLabelText("Name"));
    expect(requestsTo(requests, "POST", "/api/v1/groups")).toHaveLength(0);
  });

  it("creates the group with an Idempotency-Key and opens it", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/groups/new", screens, create);
    await user.click(await screen.findByRole("radio", { name: "Home" }));
    await user.type(screen.getByLabelText("Name"), "  Goa Trip  ");
    await user.selectOptions(screen.getByLabelText("Currency"), "USD");
    await user.click(screen.getByLabelText("Mango"));
    await user.click(screen.getByRole("button", { name: "Create group" }));

    expect(await screen.findByText(`now at /groups/${GROUP_ID}`)).toBeTruthy();
    const [post] = requestsTo(requests, "POST", "/api/v1/groups");
    expect(post.body).toEqual({
      name: "Goa Trip",
      type: "HOME",
      currency: "USD",
      cover: "color:mango",
    });
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(post.headers.get("X-XSRF-TOKEN")).toBe("tok-1");
  });

  it("uses the type's emoji as the cover until one is picked, and the user's default currency", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/groups/new", screens, {
      ...create,
      "GET /api/v1/me": () =>
        Response.json({ ...aProfile, defaultCurrency: "EUR" }),
    });
    await screen.findByRole("heading", { level: 1, name: "New group" });
    await waitFor(() =>
      expect(
        (screen.getByLabelText("Currency") as HTMLSelectElement).value,
      ).toBe("EUR"),
    );
    await user.click(screen.getByRole("radio", { name: "Couple" }));
    await user.type(screen.getByLabelText("Name"), "Us");
    await user.click(screen.getByRole("button", { name: "Create group" }));
    await screen.findByText(/now at \/groups\//);
    expect(requestsTo(requests, "POST", "/api/v1/groups")[0].body).toEqual({
      name: "Us",
      type: "COUPLE",
      currency: "EUR",
      cover: "emoji:💞",
    });
  });

  it("reuses the key when the same form is retried, and uses a new one after an edit", async () => {
    const user = userEvent.setup();
    let calls = 0;
    const { requests } = renderApp("/groups/new", screens, {
      ...signedIn,
      "POST /api/v1/groups": () => {
        calls += 1;
        return calls <= 2
          ? problem(500, "internal")
          : Response.json(aGroup(), { status: 201 });
      },
    });
    await user.type(await screen.findByLabelText("Name"), "Goa");
    await user.click(screen.getByRole("button", { name: "Create group" }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Create group" }));
    await waitFor(() => expect(calls).toBe(2));
    await user.type(screen.getByLabelText("Name"), " 2026");
    await user.click(screen.getByRole("button", { name: "Create group" }));
    await waitFor(() => expect(calls).toBe(3));
    const keys = requestsTo(requests, "POST", "/api/v1/groups").map((r) =>
      r.headers.get("Idempotency-Key"),
    );
    expect(keys[0]).toBe(keys[1]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it("shows a server field error next to the field", async () => {
    const user = userEvent.setup();
    renderApp("/groups/new", screens, {
      ...signedIn,
      "POST /api/v1/groups": () =>
        problem(400, "validation", {
          errors: [
            {
              field: "currency",
              message: "Use a three-letter currency code such as INR.",
            },
          ],
        }),
    });
    await user.type(await screen.findByLabelText("Name"), "Goa");
    await user.click(screen.getByRole("button", { name: "Create group" }));
    expect(
      await screen.findByText("Use a three-letter currency code such as INR."),
    ).toBeTruthy();
    expect((await screen.findByRole("alert")).textContent).toMatch(
      /highlighted fields/,
    );
  });

  it("goes back to the list with Cancel", async () => {
    const user = userEvent.setup();
    renderApp("/groups/new", screens, signedIn);
    const cancel = await screen.findByRole("link", { name: "Cancel" });
    expect(cancel.getAttribute("href")).toBe("/groups");
    await user.click(cancel);
  });
});
