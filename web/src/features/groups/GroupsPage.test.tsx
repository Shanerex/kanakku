import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { groupRoutes } from "../../app/routes";
import { aGroup, GROUP_ID, Here, requestsTo } from "../../test/groups";
import { aProfile, problem, renderApp } from "../../test/harness";

afterEach(cleanup);

const screens = [
  ...groupRoutes,
  { path: "/sign-in", element: <Here /> },
  { path: "/me", element: <Here /> },
];
const signedIn = { "GET /api/v1/me": () => Response.json(aProfile) };

describe("GroupsPage list", () => {
  it("lists my groups with my role and a link to each", async () => {
    renderApp("/groups", screens, {
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
    });

    const list = await screen.findByRole("list", { name: "Your groups" });
    const goa = within(list).getByRole("link", { name: /Goa Trip/ });
    expect(goa.getAttribute("href")).toBe(`/groups/${GROUP_ID}`);
    expect(goa.textContent).toMatch(/INR · You're the Owner/);
    const flat = within(list).getByRole("link", { name: /Flat 4B/ });
    expect(flat.textContent).toMatch(/Archived/);
    expect(flat.textContent).not.toMatch(/Owner/);
  });

  it("says so when there are no groups", async () => {
    renderApp("/groups", screens, {
      ...signedIn,
      "GET /api/v1/groups": () => Response.json([]),
    });
    expect(await screen.findByText("No groups yet")).toBeTruthy();
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

describe("GroupsPage create", () => {
  const empty = { ...signedIn, "GET /api/v1/groups": () => Response.json([]) };

  async function openForm(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole("button", { name: "New group" }));
    return screen.findByRole("form", { name: "New group" });
  }

  it("checks the name before sending anything", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/groups", screens, empty);
    await openForm(user);
    await user.click(screen.getByRole("button", { name: "Create group" }));

    expect(screen.getByText("Enter a name for the group.")).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByLabelText("Name"));
    expect(requestsTo(requests, "POST", "/api/v1/groups")).toHaveLength(0);
  });

  it("creates the group with an Idempotency-Key and opens it", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/groups",
      [{ path: "/groups/:id", element: <Here /> }, ...screens],
      {
        ...empty,
        "POST /api/v1/groups": () => Response.json(aGroup(), { status: 201 }),
      },
    );
    await openForm(user);
    await user.click(screen.getByLabelText("Home"));
    await user.type(screen.getByLabelText("Name"), "  Goa Trip  ");
    await user.selectOptions(screen.getByLabelText("Currency"), "USD");
    await user.click(screen.getByLabelText("Colour mango"));
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

  it("leaves the cover out when none is picked and offers the user's default currency", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/groups",
      [{ path: "/groups/:id", element: <Here /> }, ...screens],
      {
        "GET /api/v1/me": () =>
          Response.json({ ...aProfile, defaultCurrency: "EUR" }),
        "GET /api/v1/groups": () => Response.json([]),
        "POST /api/v1/groups": () => Response.json(aGroup(), { status: 201 }),
      },
    );
    await openForm(user);
    expect((screen.getByLabelText("Currency") as HTMLSelectElement).value).toBe(
      "EUR",
    );
    await user.type(screen.getByLabelText("Name"), "Flat");
    await user.click(screen.getByRole("button", { name: "Create group" }));
    await screen.findByText(/now at \/groups\//);

    expect(requestsTo(requests, "POST", "/api/v1/groups")[0].body).toEqual({
      name: "Flat",
      type: "TRIP",
      currency: "EUR",
    });
  });

  it("reuses the key when the same form is retried, and uses a new one after an edit", async () => {
    const user = userEvent.setup();
    let calls = 0;
    const { requests } = renderApp("/groups", screens, {
      ...empty,
      "POST /api/v1/groups": () => {
        calls += 1;
        return calls <= 2
          ? problem(500, "internal")
          : Response.json(aGroup(), { status: 201 });
      },
    });
    await openForm(user);
    await user.type(screen.getByLabelText("Name"), "Goa");
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
    renderApp("/groups", screens, {
      ...empty,
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
    await openForm(user);
    await user.type(screen.getByLabelText("Name"), "Goa");
    await user.click(screen.getByRole("button", { name: "Create group" }));

    expect(
      await screen.findByText("Use a three-letter currency code such as INR."),
    ).toBeTruthy();
    expect((await screen.findByRole("alert")).textContent).toMatch(
      /highlighted fields/,
    );
  });
});
