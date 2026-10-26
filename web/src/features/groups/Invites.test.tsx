import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { groupRoutes } from "../../app/routes";
import {
  aGroup,
  aMember,
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
  { path: "/groups/:id", element: <Here /> },
];
const base = `/api/v1/groups/${GROUP_ID}`;
const INVITE_ID = "77777777-7777-4777-8777-777777777777";

function anInvite(over: Record<string, unknown> = {}) {
  return {
    id: INVITE_ID,
    memberId: KARTHIK,
    guestName: "Karthik",
    status: "ACTIVE",
    expiresAt: "2026-11-01T00:00:00Z",
    createdAt: "2026-10-25T00:00:00Z",
    version: 5,
    ...over,
  };
}

function groupHandlers(
  invites: () => Record<string, unknown>[],
  extra: Record<string, () => Response | Promise<Response>> = {},
) {
  return {
    "GET /api/v1/me": () => Response.json(aProfile),
    [`GET ${base}`]: () => Response.json(aGroup()),
    [`GET ${base}/members`]: () => Response.json([aMember(), karthik()]),
    [`GET ${base}/invites`]: () => Response.json(invites()),
    ...extra,
  };
}

describe("InvitesPanel", () => {
  it("asks for a guest first when there is none", async () => {
    renderApp(`/groups/${GROUP_ID}`, screens, {
      ...groupHandlers(() => []),
      [`GET ${base}/members`]: () => Response.json([aMember()]),
    });
    expect(await screen.findByText(/Add a guest first/)).toBeTruthy();
    expect(
      screen.queryByRole("button", { name: "Create invite link" }),
    ).toBeNull();
  });

  it("creates a link with a 7 day default, shows it once in the fragment, and copies it", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      groupHandlers(() => [], {
        [`POST ${base}/invites`]: () =>
          Response.json(
            { invite: anInvite(), token: "tok_abc" },
            { status: 201 },
          ),
      }),
    );
    const expiry = (await screen.findByLabelText(
      "Expires after",
    )) as HTMLSelectElement;
    expect(expiry.value).toBe("7");
    await user.click(
      screen.getByRole("button", { name: "Create invite link" }),
    );

    const link = (await screen.findByLabelText(
      "Invite link",
    )) as HTMLInputElement;
    expect(link.value).toBe(`${window.location.origin}/invite#tok_abc`);
    expect(link.value).not.toContain("?");
    const [post] = requestsTo(requests, "POST", `${base}/invites`);
    expect(post.body).toEqual({ memberId: KARTHIK, expiryDays: 7 });
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);

    await user.click(screen.getByRole("button", { name: "Copy link" }));
    expect(await screen.findByText(/Link copied|Select the link/)).toBeTruthy();
  });

  it("sends the expiry the Owner picks", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      groupHandlers(() => [], {
        [`POST ${base}/invites`]: () =>
          Response.json({ invite: anInvite(), token: "t" }, { status: 201 }),
      }),
    );
    await user.selectOptions(
      await screen.findByLabelText("Expires after"),
      "30",
    );
    await user.click(
      screen.getByRole("button", { name: "Create invite link" }),
    );
    await screen.findByLabelText("Invite link");
    expect(requestsTo(requests, "POST", `${base}/invites`)[0].body).toEqual({
      memberId: KARTHIK,
      expiryDays: 30,
    });
  });

  it("lists invites and revokes one with its version", async () => {
    const user = userEvent.setup();
    let revoked = false;
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      groupHandlers(
        () => [anInvite({ status: revoked ? "REVOKED" : "ACTIVE" })],
        {
          [`POST ${base}/invites/${INVITE_ID}/revoke`]: () => {
            revoked = true;
            return Response.json(anInvite({ status: "REVOKED", version: 6 }));
          },
        },
      ),
    );
    const list = await screen.findByRole("list", { name: "Invites" });
    expect(list.textContent).toMatch(/Karthik/);
    expect(list.textContent).toMatch(/Active/);
    await user.click(
      within(list).getByRole("button", {
        name: "Revoke invite for Karthik",
      }),
    );

    await waitFor(() =>
      expect(
        within(screen.getByRole("list", { name: "Invites" })).getByText(
          /Revoked/,
        ),
      ).toBeTruthy(),
    );
    expect(
      requestsTo(requests, "POST", `${base}/invites/${INVITE_ID}/revoke`)[0]
        .body,
    ).toEqual({ version: 5 });
    expect(screen.queryByRole("button", { name: /Revoke invite/ })).toBeNull();
  });
});

describe("ClaimInvitePage", () => {
  const preview = { groupName: "Goa Trip", guestName: "Karthik" };

  it("reads the token from the fragment, previews, and joins", async () => {
    const user = userEvent.setup();
    const { requests, router } = renderApp("/invite#tok_abc", screens, {
      "GET /api/v1/me": () => Response.json(aProfile),
      "POST /api/v1/invites/preview": () => Response.json(preview),
      "POST /api/v1/invites/claim": () => Response.json(aGroup()),
    });
    expect(
      await screen.findByRole("heading", { name: "Join Goa Trip" }),
    ).toBeTruthy();
    expect(screen.getByText(/take over Karthik's place/)).toBeTruthy();
    const previewReq = requestsTo(
      requests,
      "POST",
      "/api/v1/invites/preview",
    )[0];
    expect(previewReq.body).toEqual({ token: "tok_abc" });

    await user.click(screen.getByRole("button", { name: "Join Goa Trip" }));
    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/groups/${GROUP_ID}`),
    );
    expect(
      requestsTo(requests, "POST", "/api/v1/invites/claim")[0].body,
    ).toEqual({ token: "tok_abc" });
    for (const r of requests) expect(r.path).not.toContain("tok_abc");
  });

  it("keeps the fragment through sign-in for a signed-out visitor", async () => {
    renderApp("/invite#tok_abc", screens, {
      "GET /api/v1/me": () => problem(401, "unauthenticated"),
    });
    expect(
      await screen.findByText("now at /sign-in?returnTo=%2Finvite#tok_abc"),
    ).toBeTruthy();
  });

  it("tells the visitor an expired, revoked or used link no longer works", async () => {
    renderApp("/invite#tok_dead", screens, {
      "GET /api/v1/me": () => Response.json(aProfile),
      "POST /api/v1/invites/preview": () => problem(410, "invite-unusable"),
    });
    expect((await screen.findByRole("alert")).textContent).toBeTruthy();
    expect(screen.queryByRole("button", { name: /^Join/ })).toBeNull();
    expect(
      screen.getByRole("link", { name: "Go to your groups" }),
    ).toBeTruthy();
  });

  it("shows a failed claim and stays on the page", async () => {
    const user = userEvent.setup();
    renderApp("/invite#tok_abc", screens, {
      "GET /api/v1/me": () => Response.json(aProfile),
      "POST /api/v1/invites/preview": () => Response.json(preview),
      "POST /api/v1/invites/claim": () => problem(409, "already-member"),
    });
    await user.click(
      await screen.findByRole("button", { name: "Join Goa Trip" }),
    );
    expect((await screen.findByRole("alert")).textContent).toBeTruthy();
    expect(screen.getByRole("button", { name: "Join Goa Trip" })).toBeTruthy();
  });

  it("explains a link with no token and sends no request", async () => {
    const { requests } = renderApp("/invite", screens, {
      "GET /api/v1/me": () => Response.json(aProfile),
    });
    expect(await screen.findByText("No invite here")).toBeTruthy();
    expect(
      requestsTo(requests, "POST", "/api/v1/invites/preview"),
    ).toHaveLength(0);
  });
});
