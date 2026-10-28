import { cleanup, screen, within } from "@testing-library/react";
import type { RouteObject } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { aGroup, GROUP_ID } from "../test/groups";
import { aProfile, problem, renderApp } from "../test/harness";
import { AppShell, usePageTitle } from "./AppShell";
import { RequireSession } from "./routes";

afterEach(cleanup);

function Page({ title }: { title: string }) {
  usePageTitle(title);
  return <p>{`body of ${title}`}</p>;
}

const screens: RouteObject[] = [
  {
    element: <RequireSession />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: "/groups", element: <Page title="Groups" /> },
          { path: "/me", element: <Page title="Me" /> },
          { path: "/groups/new", element: <Page title="New group" /> },
          { path: "/groups/:id", element: <Page title="Goa Trip" /> },
        ],
      },
    ],
  },
];
const FLAT_ID = "55555555-5555-4555-8555-555555555555";
const signedIn = {
  "GET /api/v1/me": () => Response.json(aProfile),
  "GET /api/v1/groups": () =>
    Response.json([
      aGroup({ cover: "emoji:🏖️" }),
      aGroup({ id: FLAT_ID, name: "Flat 4B", cover: "color:mango" }),
      aGroup({
        id: "66666666-6666-4666-8666-666666666666",
        name: "Old Trip",
        archivedAt: "2026-10-01T00:00:00Z",
      }),
    ]),
};

describe("AppShell", () => {
  it("shows the page title as the one h1 in a 64 px top bar", async () => {
    renderApp("/groups", screens, signedIn);
    const h1 = await screen.findByRole("heading", {
      level: 1,
      name: "Groups",
    });
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(h1.closest("header")?.className).toMatch(/\bh-16\b/);
  });

  it("puts the page inside main, capped at the content width token", async () => {
    renderApp("/groups", screens, signedIn);
    await screen.findByRole("navigation", { name: "Main" });
    const main = screen.getByRole("main");
    expect(within(main).getByText("body of Groups")).toBeTruthy();
    expect(main.className).toContain("max-w-[var(--size-content-max)]");
  });

  it("gives the top bar and the page one left and right edge", async () => {
    renderApp("/groups", screens, signedIn);
    const h1 = await screen.findByRole("heading", { level: 1, name: "Groups" });
    const bar = h1.closest("header") as HTMLElement;
    const main = screen.getByRole("main");
    // Both cap at the content token inside one padded column, so no inner padding shifts either.
    expect(bar.firstElementChild?.className).toContain(
      "max-w-[var(--size-content-max)]",
    );
    expect(main.className).toContain("max-w-[var(--size-content-max)]");
    expect(main.className).not.toMatch(/\bpx-/);
    expect(bar.parentElement).toBe(main.parentElement);
    expect(main.parentElement?.className).toContain("md:px-6");
  });

  it("marks the selected bottom tab with a primary-soft pill behind its icon", async () => {
    renderApp("/groups", screens, signedIn);
    const bottom = await screen.findByRole("navigation", {
      name: "Main tabs",
    });
    const groups = within(bottom).getByRole("link", { name: "Groups" });
    expect(groups.querySelector(".bg-primary-soft")).toBeTruthy();
    const me = within(bottom).getByRole("link", { name: /Me/ });
    expect(me.querySelector(".bg-primary-soft")).toBeNull();
  });

  it("has a sidebar for 768 px and up and a bottom bar below it, both marking the current page", async () => {
    renderApp("/groups", screens, signedIn);
    const side = await screen.findByRole("navigation", { name: "Main" });
    const bottom = screen.getByRole("navigation", { name: "Main tabs" });
    expect(side.parentElement?.className).toMatch(/\bhidden\b/);
    expect(side.parentElement?.className).toMatch(/\bmd:flex\b/);
    expect(bottom.className).toMatch(/md:hidden/);
    for (const nav of [side, bottom]) {
      expect(
        within(nav)
          .getByRole("link", { name: "Groups" })
          .getAttribute("aria-current"),
      ).toBe("page");
      expect(
        within(nav)
          .getByRole("link", { name: /^Me|Asha Rao/ })
          .getAttribute("aria-current"),
      ).toBeNull();
    }
  });

  it("links the sidebar to the built destinations and New group", async () => {
    renderApp("/me", screens, signedIn);
    const side = await screen.findByRole("navigation", { name: "Main" });
    expect(
      within(side).getByRole("link", { name: "Groups" }).getAttribute("href"),
    ).toBe("/groups");
    expect(
      within(side)
        .getByRole("link", { name: "New group" })
        .getAttribute("href"),
    ).toBe("/groups/new");
    const me = within(side).getByRole("link", { name: /Asha Rao/ });
    expect(me.getAttribute("href")).toBe("/me");
    expect(me.getAttribute("aria-current")).toBe("page");
  });

  it("lists my active groups under a Groups label, then New group", async () => {
    renderApp("/me", screens, signedIn);
    const side = await screen.findByRole("navigation", { name: "Main" });
    const list = await within(side).findByRole("list", {
      name: "Active groups",
    });
    const links = within(list).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual(["🏖️Goa Trip", "FFlat 4B"]);
    expect(links[0]?.getAttribute("href")).toBe(`/groups/${GROUP_ID}`);
    expect(links[1]?.getAttribute("href")).toBe(`/groups/${FLAT_ID}`);
    expect(within(side).queryByText("Old Trip")).toBeNull();
    const all = within(side).getAllByRole("link");
    const labelAt = all.findIndex((l) => l.getAttribute("href") === "/groups");
    const newAt = all.findIndex(
      (l) => l.getAttribute("href") === "/groups/new",
    );
    expect(labelAt).toBeLessThan(all.indexOf(links[0] as HTMLElement));
    expect(newAt).toBeGreaterThan(all.indexOf(links[1] as HTMLElement));
  });

  it("marks the open group's link as current, and no other", async () => {
    renderApp(`/groups/${FLAT_ID}`, screens, signedIn);
    const side = await screen.findByRole("navigation", { name: "Main" });
    const list = await within(side).findByRole("list", {
      name: "Active groups",
    });
    expect(
      within(list)
        .getByRole("link", { name: /Flat 4B/ })
        .getAttribute("aria-current"),
    ).toBe("page");
    expect(
      within(list)
        .getByRole("link", { name: /Goa Trip/ })
        .getAttribute("aria-current"),
    ).toBeNull();
    expect(
      within(side)
        .getByRole("link", { name: "Groups" })
        .getAttribute("aria-current"),
    ).toBeNull();
  });

  it("marks the Groups label on the list page, and New group on its form", async () => {
    const view = renderApp("/groups", screens, signedIn);
    const side = await screen.findByRole("navigation", { name: "Main" });
    await within(side).findByRole("list", { name: "Active groups" });
    expect(
      within(side)
        .getByRole("link", { name: "Groups" })
        .getAttribute("aria-current"),
    ).toBe("page");
    expect(
      within(side)
        .getByRole("link", { name: "New group" })
        .getAttribute("aria-current"),
    ).toBeNull();
    view.unmount();
    renderApp("/groups/new", screens, signedIn);
    const next = await screen.findByRole("navigation", { name: "Main" });
    expect(
      within(next)
        .getByRole("link", { name: "New group" })
        .getAttribute("aria-current"),
    ).toBe("page");
    expect(
      within(next)
        .getByRole("link", { name: "Groups" })
        .getAttribute("aria-current"),
    ).toBeNull();
    expect(within(next).queryByRole("link", { name: /Goa Trip/ })).toBeTruthy();
  });

  it("shows just New group while I have no active group", async () => {
    renderApp("/me", screens, {
      ...signedIn,
      "GET /api/v1/groups": () => Response.json([]),
    });
    const side = await screen.findByRole("navigation", { name: "Main" });
    await within(side).findByRole("link", { name: /Asha Rao/ });
    await within(side).findByRole("link", { name: "New group" });
    expect(
      within(side).queryByRole("list", { name: "Active groups" }),
    ).toBeNull();
  });

  it("keeps the shell working when the groups list fails to load", async () => {
    renderApp("/me", screens, {
      ...signedIn,
      "GET /api/v1/groups": () => problem(500, "internal"),
    });
    const side = await screen.findByRole("navigation", { name: "Main" });
    expect(
      within(side)
        .getByRole("link", { name: "New group" })
        .getAttribute("href"),
    ).toBe("/groups/new");
    expect(
      (
        await within(side).findByRole("link", { name: /Asha Rao/ })
      ).getAttribute("href"),
    ).toBe("/me");
    expect(
      within(side).queryByRole("list", { name: "Active groups" }),
    ).toBeNull();
    expect(screen.getByRole("main")).toBeTruthy();
  });

  it("draws the wordmark in the sidebar", async () => {
    renderApp("/me", screens, signedIn);
    const side = await screen.findByRole("navigation", { name: "Main" });
    expect(side.parentElement?.textContent).toContain("kanakku.");
  });

  it("does not render for a signed-out visitor", async () => {
    renderApp("/me", screens, {
      "GET /api/v1/me": () => problem(401, "unauthenticated"),
    });
    await screen.findByRole("heading", { name: "This isn't available" });
    expect(screen.queryByRole("navigation", { name: "Main" })).toBeNull();
  });
});
