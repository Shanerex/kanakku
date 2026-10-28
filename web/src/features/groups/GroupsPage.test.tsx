import { cleanup, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { groupRoutes } from "../../app/routes";
import { aGroup, GROUP_ID, Here } from "../../test/groups";
import { aProfile, problem, renderApp } from "../../test/harness";

afterEach(cleanup);

const screens = [
  ...groupRoutes,
  { path: "/sign-in", element: <Here /> },
  { path: "/me", element: <Here /> },
];
const signedIn = { "GET /api/v1/me": () => Response.json(aProfile) };

describe("GroupsPage list", () => {
  const two = {
    ...signedIn,
    "GET /api/v1/groups": () =>
      Response.json([
        aGroup(),
        aGroup({
          id: "55555555-5555-4555-8555-555555555555",
          name: "Flat 4B",
          myRole: "MEMBER",
          archivedAt: "2026-10-01T00:00:00Z",
        }),
      ]),
  };

  it("titles the page, lists active groups with my role and links to each", async () => {
    renderApp("/groups", screens, two);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Groups" }),
    ).toBeTruthy();
    const list = await screen.findByRole("list", { name: "Your groups" });
    const goa = within(list).getByRole("link", { name: /Goa Trip/ });
    expect(goa.getAttribute("href")).toBe(`/groups/${GROUP_ID}`);
    expect(goa.textContent).toMatch(/INR · You're the Owner/);
    expect(goa.className).toContain("min-h-16");
    expect(within(list).queryByRole("link", { name: /Flat 4B/ })).toBeNull();
  });

  it("switches between Active and Archived", async () => {
    const user = userEvent.setup();
    renderApp("/groups", screens, two);
    await screen.findByRole("list", { name: "Your groups" });
    await user.click(screen.getByRole("radio", { name: "Archived" }));
    const list = screen.getByRole("list", { name: "Your groups" });
    const flat = within(list).getByRole("link", { name: /Flat 4B/ });
    expect(flat.textContent).toMatch(/Archived/);
    expect(flat.textContent).not.toMatch(/Owner/);
    expect(within(list).queryByRole("link", { name: /Goa Trip/ })).toBeNull();
  });

  it("filters by name as you type and says when nothing matches", async () => {
    const user = userEvent.setup();
    renderApp("/groups", screens, two);
    await screen.findByRole("list", { name: "Your groups" });
    await user.type(
      screen.getByRole("searchbox", { name: "Search groups" }),
      "xyz",
    );
    expect(screen.queryByRole("list", { name: "Your groups" })).toBeNull();
    expect(screen.getByText("No groups match that search.")).toBeTruthy();
    await user.clear(screen.getByRole("searchbox", { name: "Search groups" }));
    await user.type(
      screen.getByRole("searchbox", { name: "Search groups" }),
      "goa",
    );
    expect(
      within(screen.getByRole("list", { name: "Your groups" })).getByRole(
        "link",
        {
          name: /Goa Trip/,
        },
      ),
    ).toBeTruthy();
  });

  it("puts New group in the top bar as a link to the form", async () => {
    renderApp("/groups", screens, two);
    await screen.findByRole("list", { name: "Your groups" });
    const header = screen.getByRole("heading", { level: 1 }).closest("header");
    const link = within(header as HTMLElement).getByRole("link", {
      name: "New group",
    });
    expect(link.getAttribute("href")).toBe("/groups/new");
    // The primary action: a filled pill, not an outline button.
    expect(link.className).toContain("bg-primary");
    expect(link.className).toContain("rounded-pill");
  });

  it("shows the empty state with one action when there are no groups", async () => {
    renderApp("/groups", screens, {
      ...signedIn,
      "GET /api/v1/groups": () => Response.json([]),
    });
    expect(
      await screen.findByRole("heading", { name: "Start your first group" }),
    ).toBeTruthy();
    expect(
      screen.getByText(
        "A trip, a flat, a dinner. Add people and split as you go.",
      ),
    ).toBeTruthy();
    const main = screen.getByRole("main");
    expect(
      within(main).getAllByRole("link", { name: "New group" }).length,
    ).toBeGreaterThan(0);
    expect(main.querySelector("svg[aria-hidden='true']")).toBeTruthy();
  });

  it("shows identity cards in three columns from 1024 px, ending with a New group card", async () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("1024"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia;
    try {
      renderApp("/groups", screens, two);
      const list = await screen.findByRole("list", { name: "Your groups" });
      expect(list.className).toContain("grid-cols-3");
      const cards = within(list).getAllByRole("listitem");
      expect(cards).toHaveLength(2);
      expect(
        within(cards[0]).getByRole("link", { name: /Goa Trip/ }),
      ).toBeTruthy();
      expect(
        within(cards[1])
          .getByRole("link", { name: "New group" })
          .getAttribute("href"),
      ).toBe("/groups/new");
      expect(cards[0].querySelector("[data-identity-bar]")).toBeTruthy();
      // A tinted header band over a white body with my role and the currency.
      const band = cards[0].querySelector(
        "[data-identity-band]",
      ) as HTMLElement;
      expect(band.style.background).toContain("-tint");
      const body = cards[0].querySelector(
        "[data-identity-body]",
      ) as HTMLElement;
      expect(body.className).not.toContain("tint");
      expect(body.textContent).toContain("Owner");
      expect(body.textContent).toContain("INR");
    } finally {
      window.matchMedia = original;
    }
  });

  it("sends a signed-out visitor to sign-in with a way back", async () => {
    renderApp("/groups", screens, {
      "GET /api/v1/me": () => problem(401, "unauthenticated"),
    });
    expect(
      await screen.findByText("now at /sign-in?returnTo=%2Fgroups"),
    ).toBeTruthy();
  });

  it("explains a failed load in words", async () => {
    renderApp("/groups", screens, {
      ...signedIn,
      "GET /api/v1/groups": () => problem(500, "internal"),
    });
    expect((await screen.findByRole("alert")).textContent).toMatch(/our side/);
  });
});
