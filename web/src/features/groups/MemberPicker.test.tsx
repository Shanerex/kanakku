import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { groupRoutes } from "../../app/routes";
import {
  aGroup,
  aMember,
  bala,
  GROUP_ID,
  Here,
  karthik,
  requestsTo,
} from "../../test/groups";
import { aProfile, problem, renderApp } from "../../test/harness";
import { longEnough } from "./MemberPicker";

afterEach(cleanup);

const screens = [
  ...groupRoutes,
  { path: "/sign-in", element: <Here /> },
  { path: "/me", element: <Here /> },
];
const base = `/api/v1/groups/${GROUP_ID}`;
const SEARCH = `${base}/user-search`;

const MEERA = {
  id: "66666666-6666-4666-8666-666666666666",
  displayName: "Meera Nair",
  matchedBy: "NAME",
  maskedEmail: "m***@example.com",
  directAdd: true,
};
const BY_PHONE = {
  id: "77777777-7777-4777-8777-777777777777",
  displayName: "Ravi Shah",
  matchedBy: "PHONE",
  maskedPhone: "+91 ••••• ••10",
  directAdd: true,
};
const INVITE_ONLY = {
  id: "88888888-8888-4888-8888-888888888888",
  displayName: "Tara Menon",
  matchedBy: "EMAIL",
  maskedEmail: "t***@example.com",
  directAdd: false,
};

function handlers(
  extra: Record<string, () => Response | Promise<Response>> = {},
) {
  return {
    "GET /api/v1/me": () => Response.json(aProfile),
    [`GET ${base}`]: () => Response.json(aGroup()),
    [`GET ${base}/members`]: () => Response.json([aMember(), karthik()]),
    [`GET ${base}/invites`]: () => Response.json([]),
    ...extra,
  };
}

async function search(user: ReturnType<typeof userEvent.setup>, q: string) {
  await user.type(await screen.findByLabelText("Find people"), q);
}

describe("longEnough", () => {
  it("needs 3 characters for text and 4 digits for a phone search", () => {
    expect(longEnough("me")).toBe(false);
    expect(longEnough("mee")).toBe(true);
    expect(longEnough("987")).toBe(false);
    expect(longEnough("9876")).toBe(true);
    expect(longEnough("+9 1")).toBe(false);
    expect(longEnough("98 76")).toBe(true);
    expect(longEnough("asha 987")).toBe(true);
  });
});

describe("MemberPicker", () => {
  it("waits for 3 characters, then searches by POST body and direct-adds with a key", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({
        [`POST ${SEARCH}`]: () => Response.json([MEERA]),
        [`POST ${base}/members`]: () => Response.json(bala(), { status: 201 }),
      }),
    );
    const find = await screen.findByLabelText("Find people");
    await user.type(find, "me");
    await new Promise((r) => setTimeout(r, 500));
    expect(requestsTo(requests, "POST", SEARCH)).toHaveLength(0);

    await user.type(find, "e");
    expect(await screen.findByText("Meera Nair")).toBeTruthy();
    expect(screen.getByText("m***@example.com")).toBeTruthy();
    const [sent] = requestsTo(requests, "POST", SEARCH);
    expect(sent.body).toEqual({ q: "mee" });
    expect(requestsTo(requests, "GET", SEARCH)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Add Meera Nair" }));
    await waitFor(() =>
      expect(requestsTo(requests, "POST", `${base}/members`)).toHaveLength(1),
    );
    const [post] = requestsTo(requests, "POST", `${base}/members`);
    expect(post.body).toEqual({ userId: MEERA.id });
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    await waitFor(() => expect(screen.queryByText("Meera Nair")).toBeNull());
  });

  it("finds by phone from 4 digits and shows only the masked number", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({ [`POST ${SEARCH}`]: () => Response.json([BY_PHONE]) }),
    );
    await search(user, "987");
    await new Promise((r) => setTimeout(r, 500));
    expect(requestsTo(requests, "POST", SEARCH)).toHaveLength(0);

    await user.type(screen.getByLabelText("Find people"), "6");
    expect(await screen.findByText("Ravi Shah")).toBeTruthy();
    expect(screen.getByText("+91 ••••• ••10")).toBeTruthy();
    expect(screen.queryByText(/@/)).toBeNull();
    expect(requestsTo(requests, "POST", SEARCH)[0].body).toEqual({ q: "9876" });
    expect(screen.getByRole("button", { name: "Add Ravi Shah" })).toBeTruthy();
  });

  it("shows Invite and no Add for an invite-only user", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({ [`POST ${SEARCH}`]: () => Response.json([INVITE_ONLY]) }),
    );
    await search(user, "tara");

    expect(
      await screen.findByRole("button", { name: "Invite Tara Menon" }),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Add Tara Menon" })).toBeNull();
  });

  it("invites with a key, shows the link and adds no member", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({
        [`POST ${SEARCH}`]: () => Response.json([INVITE_ONLY]),
        [`POST ${base}/user-invites`]: () =>
          Response.json(
            {
              invite: {
                id: "99999999-9999-4999-8999-999999999999",
                memberId: "33333333-3333-4333-8333-333333333399",
                guestName: "Tara Menon",
                status: "ACTIVE",
                expiresAt: "2026-11-01T00:00:00Z",
                createdAt: "2026-10-25T00:00:00Z",
                version: 0,
              },
              token: "tok-abc",
            },
            { status: 201 },
          ),
      }),
    );
    await search(user, "tara");
    await user.click(
      await screen.findByRole("button", { name: "Invite Tara Menon" }),
    );

    expect(await screen.findByText("Invite ready for Tara Menon")).toBeTruthy();
    const link = screen.getByLabelText(
      "Invite link for Tara Menon",
    ) as HTMLInputElement;
    expect(link.value).toMatch(/\/invite#tok-abc$/);
    const [invite] = requestsTo(requests, "POST", `${base}/user-invites`);
    expect(invite.body).toEqual({ userId: INVITE_ONLY.id });
    expect(invite.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(requestsTo(requests, "POST", `${base}/members`)).toHaveLength(0);
    // The members list reloads: the new guest appears there.
    await waitFor(() =>
      expect(
        requestsTo(requests, "GET", `${base}/members`).length,
      ).toBeGreaterThan(1),
    );
  });

  it("marks an unverified phone match and still adds that person with no error", async () => {
    const user = userEvent.setup();
    const VERIFIED = {
      ...BY_PHONE,
      id: "99999999-9999-4999-8999-999999999999",
      displayName: "Vera Das",
      phoneVerified: true,
    };
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({
        [`POST ${SEARCH}`]: () =>
          Response.json([{ ...BY_PHONE, phoneVerified: false }, VERIFIED]),
        [`POST ${base}/members`]: () =>
          Response.json(aMember({ displayName: "Ravi Shah" }), { status: 201 }),
      }),
    );
    await search(user, "9876");

    const ravi = (await screen.findByText("Ravi Shah")).closest("li");
    const vera = (await screen.findByText("Vera Das")).closest("li");
    expect(ravi?.textContent).toContain("Unverified");
    expect(vera?.textContent).not.toContain("Unverified");

    await user.click(screen.getByRole("button", { name: "Add Ravi Shah" }));

    expect(requestsTo(requests, "POST", `${base}/members`)).toHaveLength(1);
    await waitFor(() => expect(screen.queryByText("Ravi Shah")).toBeNull());
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByText(/not verified/)).toBeNull();
  });

  it("says how long to wait after too many searches", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({
        [`POST ${SEARCH}`]: () =>
          problem(429, "search-rate-limited", { retryAfterSeconds: 40 }),
      }),
    );
    await search(user, "tara");

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("You searched too often");
    expect(alert.textContent).toContain("40 seconds");
  });

  it("says when nobody matches and points to guests", async () => {
    const user = userEvent.setup();
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({ [`POST ${SEARCH}`]: () => Response.json([]) }),
    );
    await search(user, "zzz");

    expect(
      await screen.findByText(
        "No one found for that search. Add them as a guest instead.",
      ),
    ).toBeTruthy();
  });
});

describe("Add a guest with a phone number", () => {
  it("sends the optional number and shows an invalid one on the field", async () => {
    const user = userEvent.setup();
    let answer: Response = problem(400, "invalid-phone");
    const { requests } = renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({ [`POST ${base}/guests`]: () => answer }),
    );
    await user.type(await screen.findByLabelText("Guest name"), "Meera");
    await user.type(screen.getByLabelText("Mobile number (optional)"), "12345");
    await user.click(screen.getByRole("button", { name: "Add guest" }));

    expect(
      await screen.findByText("Enter a mobile number like 98765 43210."),
    ).toBeTruthy();
    expect(requestsTo(requests, "POST", `${base}/guests`)[0].body).toEqual({
      name: "Meera",
      phone: "12345",
    });

    answer = Response.json(karthik({ displayName: "Meera" }), { status: 201 });
    await user.clear(screen.getByLabelText("Mobile number (optional)"));
    await user.type(
      screen.getByLabelText("Mobile number (optional)"),
      "98765 43210",
    );
    await user.click(screen.getByRole("button", { name: "Add guest" }));
    await waitFor(() =>
      expect(requestsTo(requests, "POST", `${base}/guests`)).toHaveLength(2),
    );
    expect(requestsTo(requests, "POST", `${base}/guests`)[1].body).toEqual({
      name: "Meera",
      phone: "98765 43210",
    });
  });

  it("shows a guest's masked number to the Owner on the member row", async () => {
    renderApp(
      `/groups/${GROUP_ID}`,
      screens,
      handlers({
        [`GET ${base}/members`]: () =>
          Response.json([
            aMember(),
            karthik({ guestPhoneMasked: "+91 ••••• ••10" }),
          ]),
      }),
    );
    await screen.findByRole("heading", { name: "Goa Trip" });

    expect(screen.getByText(/Guest · \+91 ••••• ••10/)).toBeTruthy();
  });
});
