import { cleanup, screen, waitFor } from "@testing-library/react";
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

const ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const SUGGESTION = {
  id: ID,
  groupId: GROUP_ID,
  groupName: "Goa Trip",
  guestMemberId: "33333333-3333-4333-8333-333333333332",
  guestName: "Bala (guest)",
  status: "PENDING",
  version: 0,
  createdAt: "2026-10-25T10:00:00Z",
};

function handlers(extra: Record<string, () => Response> = {}) {
  return {
    "GET /api/v1/me": () => Response.json(aProfile),
    "GET /api/v1/groups": () => Response.json([aGroup()]),
    "GET /api/v1/claim-suggestions": () => Response.json([SUGGESTION]),
    ...extra,
  };
}

describe("ClaimSuggestionsBanner", () => {
  it("shows the suggestion with Claim and Not me", async () => {
    renderApp("/groups", screens, handlers());

    expect(
      await screen.findByText("You might be Bala (guest) in Goa Trip."),
    ).toBeTruthy();
    expect(screen.getByText(/Nothing changes until you choose/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Claim" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Not me" })).toBeTruthy();
  });

  it("shows nothing when there are no suggestions", async () => {
    renderApp(
      "/groups",
      screens,
      handlers({ "GET /api/v1/claim-suggestions": () => Response.json([]) }),
    );
    await screen.findByRole("list", { name: "Your groups" });

    expect(screen.queryByText(/You might be/)).toBeNull();
    expect(screen.queryByRole("button", { name: "Claim" })).toBeNull();
  });

  it("still shows the groups when the suggestions cannot be loaded", async () => {
    renderApp(
      "/groups",
      screens,
      handlers({
        "GET /api/v1/claim-suggestions": () => problem(500, "internal"),
      }),
    );

    expect(
      await screen.findByRole("list", { name: "Your groups" }),
    ).toBeTruthy();
    expect(screen.queryByText(/You might be/)).toBeNull();
  });

  it("claims with the suggestion's version, tells you and removes the banner", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/groups",
      screens,
      handlers({
        [`POST /api/v1/claim-suggestions/${ID}/confirm`]: () =>
          Response.json({ ...SUGGESTION, status: "CONFIRMED", version: 1 }),
      }),
    );
    await user.click(await screen.findByRole("button", { name: "Claim" }));

    expect(
      await screen.findByText("Claimed. Bala (guest)'s balance is now yours."),
    ).toBeTruthy();
    await waitFor(() => expect(screen.queryByText(/You might be/)).toBeNull());
    const [confirm] = requestsTo(
      requests,
      "POST",
      `/api/v1/claim-suggestions/${ID}/confirm`,
    );
    expect(confirm.body).toEqual({ version: 0 });
    expect(
      requestsTo(requests, "POST", `/api/v1/claim-suggestions/${ID}/decline`),
    ).toHaveLength(0);
  });

  it("declines with 'Not me' and says it will not be suggested again", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/groups",
      screens,
      handlers({
        [`POST /api/v1/claim-suggestions/${ID}/decline`]: () =>
          Response.json({ ...SUGGESTION, status: "DECLINED", version: 1 }),
      }),
    );
    await user.click(await screen.findByRole("button", { name: "Not me" }));

    expect(
      await screen.findByText("Got it. We won't suggest this seat again."),
    ).toBeTruthy();
    await waitFor(() => expect(screen.queryByText(/You might be/)).toBeNull());
    expect(
      requestsTo(requests, "POST", `/api/v1/claim-suggestions/${ID}/decline`)[0]
        .body,
    ).toEqual({ version: 0 });
    expect(
      requestsTo(requests, "POST", `/api/v1/claim-suggestions/${ID}/confirm`),
    ).toHaveLength(0);
  });

  it("keeps the banner and explains when the suggestion no longer applies", async () => {
    const user = userEvent.setup();
    renderApp(
      "/groups",
      screens,
      handlers({
        [`POST /api/v1/claim-suggestions/${ID}/confirm`]: () =>
          problem(409, "suggestion-resolved"),
      }),
    );
    await user.click(await screen.findByRole("button", { name: "Claim" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "This suggestion no longer applies.",
    );
    expect(screen.getByText(/You might be/)).toBeTruthy();
    expect(screen.queryByText(/urn:|409/)).toBeNull();
  });

  it("explains when the server cannot be reached", async () => {
    const user = userEvent.setup();
    renderApp(
      "/groups",
      screens,
      handlers({
        [`POST /api/v1/claim-suggestions/${ID}/decline`]: () => {
          throw new TypeError("Failed to fetch");
        },
      }),
    );
    await user.click(await screen.findByRole("button", { name: "Not me" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "Can't reach Kanakku",
    );
  });

  it("handles several suggestions one at a time", async () => {
    const user = userEvent.setup();
    const second = {
      ...SUGGESTION,
      id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      groupName: "Flat 4B",
    };
    renderApp(
      "/groups",
      screens,
      handlers({
        "GET /api/v1/claim-suggestions": () =>
          Response.json([SUGGESTION, second]),
        [`POST /api/v1/claim-suggestions/${ID}/decline`]: () =>
          Response.json({ ...SUGGESTION, status: "DECLINED", version: 1 }),
      }),
    );
    expect(await screen.findByText(/in Flat 4B/)).toBeTruthy();
    await user.click(screen.getAllByRole("button", { name: "Not me" })[0]);

    await waitFor(() => expect(screen.queryByText(/in Goa Trip\./)).toBeNull());
    expect(screen.getByText(/in Flat 4B/)).toBeTruthy();
  });
});
