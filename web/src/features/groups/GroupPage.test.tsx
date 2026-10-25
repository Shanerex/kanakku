import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { groupRoutes } from "../../app/routes";
import {
  aGroup,
  aMember,
  bala,
  GROUP_ID,
  Here,
  KARTHIK,
  karthik,
  requestsTo,
} from "../../test/groups";
import { aProfile, problem, renderApp } from "../../test/harness";

afterEach(cleanup);

const screens = [
  ...groupRoutes,
  { path: "/sign-in", element: <Here /> },
  { path: "/me", element: <Here /> },
];
const base = `/api/v1/groups/${GROUP_ID}`;

function handlers(
  over: {
    group?: Record<string, unknown>;
    members?: Record<string, unknown>[];
  } = {},
  extra: Record<string, () => Response | Promise<Response>> = {},
) {
  return {
    "GET /api/v1/me": () => Response.json(aProfile),
    [`GET ${base}`]: () => Response.json(aGroup(over.group)),
    [`GET ${base}/members`]: () =>
      Response.json(over.members ?? [aMember(), karthik()]),
    [`GET ${base}/invites`]: () => Response.json([]),
    ...extra,
  };
}

describe("GroupPage members", () => {
  it("lists members with balances in words and a Settled marker", async () => {
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({
        members: [
          aMember({ balances: [{ currency: "INR", amountMinor: 4500 }] }),
          karthik({ balances: [{ currency: "INR", amountMinor: -4500 }] }),
          bala(),
        ],
      }),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    expect(screen.getByText("INR · 3 people")).toBeTruthy();
    expect(screen.getByText(/Asha Rao \(you\)/)).toBeTruthy();
    expect(screen.getByText("is owed ₹45.00")).toBeTruthy();
    expect(screen.getByText("owes ₹45.00")).toBeTruthy();
    expect(screen.getByText("Guest")).toBeTruthy();
    expect(screen.getAllByText("Settled")).toHaveLength(1);
  });

  it("adds a guest with an Idempotency-Key and reloads the members", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        {},
        {
          [`POST ${base}/guests`]: () =>
            Response.json(karthik(), { status: 201 }),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Add guest" }));
    expect(screen.getByText("Enter the guest's name.")).toBeTruthy();
    expect(requestsTo(requests, "POST", `${base}/guests`)).toHaveLength(0);

    await user.type(screen.getByLabelText("Guest name"), "  Karthik ");
    await user.type(screen.getByLabelText("Email (optional)"), "k@example.com");
    await user.click(screen.getByRole("button", { name: "Add guest" }));

    await waitFor(() =>
      expect(requestsTo(requests, "POST", `${base}/guests`)).toHaveLength(1),
    );
    const [post] = requestsTo(requests, "POST", `${base}/guests`);
    expect(post.body).toEqual({ name: "Karthik", email: "k@example.com" });
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    await waitFor(() =>
      expect(requestsTo(requests, "GET", `${base}/members`)).toHaveLength(2),
    );
  });

  it("shows a non-member a plain not-found message", async () => {
    renderApp(`/groups/${GROUP_ID}`, screens, {
      "GET /api/v1/me": () => Response.json(aProfile),
      [`GET ${base}`]: () => problem(404, "group-not-found"),
      [`GET ${base}/members`]: () => problem(404, "group-not-found"),
    });
    expect((await screen.findByRole("alert")).textContent).toMatch(
      /isn't available|not found|can't find|removed/i,
    );
  });

  it("hides the Owner tools from a Member and offers Leave", async () => {
    const user = userEvent.setup();
    const { requests, router } = renderApp(
      `/groups/${GROUP_ID}`,
      [{ path: "/groups", element: <Here /> }, ...screens],
      handlers(
        {
          group: { myRole: "MEMBER" },
          members: [aMember({ userId: "x" }), karthik()],
        },
        { [`POST ${base}/leave`]: () => new Response(null, { status: 204 }) },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    expect(screen.queryByRole("button", { name: "Archive group" })).toBeNull();
    expect(screen.queryByText("Invite links")).toBeNull();
    expect(screen.queryByLabelText("Find people")).toBeNull();
    expect(screen.queryByRole("button", { name: /^Remove/ })).toBeNull();
    expect(requestsTo(requests, "GET", `${base}/invites`)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Leave group" }));
    await user.click(screen.getByRole("button", { name: "Leave" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/groups"));
  });

  it("explains a leave refused for a balance", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { group: { myRole: "MEMBER" } },
        {
          [`POST ${base}/leave`]: () =>
            problem(409, "member-has-balance", {
              balances: [{ currency: "INR", amountMinor: -1000 }],
            }),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Leave group" }));
    await user.click(screen.getByRole("button", { name: "Leave" }));
    expect((await screen.findByRole("alert")).textContent).toBeTruthy();
  });
});

describe("GroupPage archive", () => {
  it("archives with the version it loaded and shows the read-only note", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { group: { version: 2 } },
        {
          [`POST ${base}/archive`]: () =>
            Response.json(
              aGroup({ version: 3, archivedAt: "2026-10-25T00:00:00Z" }),
            ),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Archive group" }));
    await user.click(screen.getByRole("button", { name: "Archive" }));

    expect((await screen.findByRole("status")).textContent).toMatch(
      /archived.*read-only/,
    );
    expect(requestsTo(requests, "POST", `${base}/archive`)[0].body).toEqual({
      version: 2,
    });
    expect(screen.queryByText("Add a guest")).toBeNull();
    expect(
      screen.getByRole("button", { name: "Unarchive group" }),
    ).toBeTruthy();
  });

  it("unarchives and brings the add forms back", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { group: { archivedAt: "2026-10-25T00:00:00Z", version: 3 } },
        {
          [`POST ${base}/unarchive`]: () =>
            Response.json(aGroup({ version: 4 })),
        },
      ),
    );
    await user.click(
      await screen.findByRole("button", { name: "Unarchive group" }),
    );
    await user.click(screen.getByRole("button", { name: "Unarchive" }));
    expect(await screen.findByText("Add a guest")).toBeTruthy();
  });

  it("says so when the version is stale", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        {},
        { [`POST ${base}/archive`]: () => problem(409, "stale-version") },
      ),
    );
    await user.click(
      await screen.findByRole("button", { name: "Archive group" }),
    );
    await user.click(screen.getByRole("button", { name: "Archive" }));
    expect((await screen.findByRole("alert")).textContent).toBeTruthy();
  });
});

describe("GroupPage edit", () => {
  it("lets the Owner rename the group, sending the version and only what changed", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { group: { version: 5 } },
        {
          [`PATCH ${base}`]: () =>
            Response.json(aGroup({ name: "Goa 2027", version: 6 })),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Edit group" }));
    const dialog = await screen.findByRole("dialog", { name: "Edit group" });
    const name = within(dialog).getByLabelText("Name") as HTMLInputElement;
    expect(name.value).toBe("Goa Trip");
    await user.clear(name);
    await user.type(name, "  Goa 2027 ");
    await user.click(within(dialog).getByLabelText("Home"));
    await user.click(within(dialog).getByLabelText("Emoji 🏖"));
    await user.click(
      within(dialog).getByRole("button", { name: "Save changes" }),
    );

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getByRole("heading", { name: "Goa 2027" })).toBeTruthy();
    const [patch] = requestsTo(requests, "PATCH", base);
    expect(patch.body).toEqual({
      version: 5,
      name: "Goa 2027",
      type: "HOME",
      cover: "emoji:🏖",
    });
  });

  it("sends only the version and the changed field", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        {},
        { [`PATCH ${base}`]: () => Response.json(aGroup({ type: "HOME" })) },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Edit group" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText("Home"));
    await user.click(
      within(dialog).getByRole("button", { name: "Save changes" }),
    );
    await waitFor(() =>
      expect(requestsTo(requests, "PATCH", base)).toHaveLength(1),
    );
    expect(requestsTo(requests, "PATCH", base)[0].body).toEqual({
      version: 0,
      type: "HOME",
    });
  });

  it("asks for a name before sending anything", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(`/groups/${GROUP_ID}`, screens, handlers());
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Edit group" }));
    const dialog = await screen.findByRole("dialog");
    await user.clear(within(dialog).getByLabelText("Name"));
    await user.click(
      within(dialog).getByRole("button", { name: "Save changes" }),
    );
    expect(
      within(dialog).getByText("Enter a name for the group."),
    ).toBeTruthy();
    expect(requestsTo(requests, "PATCH", base)).toHaveLength(0);
  });

  it("says in plain words when the group changed somewhere else", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({}, { [`PATCH ${base}`]: () => problem(409, "stale-version") }),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Edit group" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Name"), "!");
    await user.click(
      within(dialog).getByRole("button", { name: "Save changes" }),
    );
    const alert = await within(dialog).findByRole("alert");
    expect(alert.textContent).toMatch(/changed somewhere else/i);
    expect(screen.getByRole("dialog", { name: "Edit group" })).toBeTruthy();
  });

  it("hides the edit control from a Member", async () => {
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({ group: { myRole: "MEMBER" } }),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    expect(screen.queryByRole("button", { name: "Edit group" })).toBeNull();
  });

  it("hides the edit control on an archived group", async () => {
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({ group: { archivedAt: "2026-10-25T00:00:00Z" } }),
    );
    await screen.findByRole("button", { name: "Unarchive group" });
    expect(screen.queryByRole("button", { name: "Edit group" })).toBeNull();
  });
});

describe("GroupPage remove and write-off", () => {
  const owing = [
    aMember(),
    bala(),
    karthik({ balances: [{ currency: "INR", amountMinor: -1000 }] }),
  ];

  it("offers the write-off when a removal is refused for a balance", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { members: owing },
        {
          [`DELETE ${base}/members/${KARTHIK}`]: () =>
            problem(409, "member-has-balance", {
              balances: [{ currency: "INR", amountMinor: -1000 }],
            }),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Remove Karthik" }));
    await user.click(screen.getByRole("button", { name: "Remove" }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/Karthik still has a balance/);
    expect(alert.textContent).toMatch(/Karthik owes ₹10\.00/);
    expect(
      within(alert).getByRole("button", {
        name: /Write off Karthik's balance/,
      }),
    ).toBeTruthy();
  });

  it("preselects every other member and sends them as absorbers", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { members: owing },
        {
          [`POST ${base}/members/${KARTHIK}/write-offs`]: () =>
            Response.json({}, { status: 201 }),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(
      screen.getByRole("button", { name: "Write off Karthik's balance" }),
    );

    const dialog = await screen.findByRole("dialog", {
      name: "Write off ₹10.00?",
    });
    const boxes = within(dialog).getAllByRole("checkbox") as HTMLInputElement[];
    expect(boxes).toHaveLength(2);
    expect(boxes.every((b) => b.checked)).toBe(true);
    expect(within(dialog).queryByLabelText("Karthik")).toBeNull();

    await user.click(within(dialog).getByLabelText("Bala"));
    expect(dialog.textContent).toMatch(/onto 1 member/);
    await user.click(within(dialog).getByLabelText("Bala"));
    await user.click(within(dialog).getByLabelText("Asha Rao"));
    await user.click(within(dialog).getByRole("button", { name: "Write off" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    const [post] = requestsTo(
      requests,
      "POST",
      `${base}/members/${KARTHIK}/write-offs`,
    );
    expect(post.body).toEqual({ absorberMemberIds: [bala().id] });
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("retries with the same Idempotency-Key, and a new one once the absorbers change", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { members: owing },
        {
          [`POST ${base}/members/${KARTHIK}/write-offs`]: () =>
            problem(503, "unavailable"),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(
      screen.getByRole("button", { name: "Write off Karthik's balance" }),
    );
    const dialog = await screen.findByRole("dialog");
    const confirm = () =>
      user.click(within(dialog).getByRole("button", { name: "Write off" }));
    const posts = () =>
      requestsTo(requests, "POST", `${base}/members/${KARTHIK}/write-offs`);

    await confirm();
    await waitFor(() => expect(posts()).toHaveLength(1));
    await confirm();
    await waitFor(() => expect(posts()).toHaveLength(2));
    await user.click(within(dialog).getByLabelText("Bala"));
    await confirm();
    await waitFor(() => expect(posts()).toHaveLength(3));

    const keys = posts().map((r) => r.headers.get("Idempotency-Key"));
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it("will not write off with no absorber picked", async () => {
    const user = userEvent.setup();
    renderApp(`/groups/${GROUP_ID}`, screens, handlers({ members: owing }));
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(
      screen.getByRole("button", { name: "Write off Karthik's balance" }),
    );
    const dialog = await screen.findByRole("dialog");
    for (const box of within(dialog).getAllByRole("checkbox"))
      await user.click(box);
    expect(
      (
        within(dialog).getByRole("button", {
          name: "Write off",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
  });

  it("removes a member at zero balance and reloads", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        { members: [aMember(), karthik({ version: 4 })] },
        {
          [`DELETE ${base}/members/${KARTHIK}`]: () =>
            new Response(null, { status: 204 }),
        },
      ),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });
    await user.click(screen.getByRole("button", { name: "Remove Karthik" }));
    await user.click(screen.getByRole("button", { name: "Remove" }));
    await waitFor(() =>
      expect(requestsTo(requests, "GET", `${base}/members`)).toHaveLength(2),
    );
  });
});

describe("MemberPicker", () => {
  it("waits for 3 characters, then searches and direct-adds with a key", async () => {
    const user = userEvent.setup();
    const match = {
      id: "66666666-6666-4666-8666-666666666666",
      displayName: "Meera Nair",
      maskedEmail: "m***@example.com",
    };
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers(
        {},
        {
          [`GET ${base}/user-search`]: () => Response.json([match]),
          [`POST ${base}/members`]: () =>
            Response.json(bala(), { status: 201 }),
        },
      ),
    );
    const find = await screen.findByLabelText("Find people");
    await user.type(find, "me");
    await new Promise((r) => setTimeout(r, 400));
    expect(requestsTo(requests, "GET", `${base}/user-search`)).toHaveLength(0);

    await user.type(find, "e");
    const row = await screen.findByText("Meera Nair");
    expect(screen.getByText("m***@example.com")).toBeTruthy();
    expect(row).toBeTruthy();
    expect(requestsTo(requests, "GET", `${base}/user-search`)[0]).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "Add Meera Nair" }));
    await waitFor(() =>
      expect(requestsTo(requests, "POST", `${base}/members`)).toHaveLength(1),
    );
    const [post] = requestsTo(requests, "POST", `${base}/members`);
    expect(post.body).toEqual({ userId: match.id });
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    await waitFor(() => expect(screen.queryByText("Meera Nair")).toBeNull());
  });

  it("says when nobody can be added", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({}, { [`GET ${base}/user-search`]: () => Response.json([]) }),
    );
    await user.type(await screen.findByLabelText("Find people"), "zzz");
    expect(
      await screen.findByText("No one to add for that search."),
    ).toBeTruthy();
  });
});
