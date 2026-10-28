import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { type RouteObject, useLocation } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { profileRoutes } from "../../app/routes";
import { aProfile, problem, renderApp, type Seen } from "../../test/harness";

afterEach(cleanup);

function Here() {
  const location = useLocation();
  return <p>{`now at ${location.pathname}${location.search}`}</p>;
}

const stubs: RouteObject[] = [{ path: "/sign-in", element: <Here /> }];
const screens = [...profileRoutes, ...stubs];
const signedIn = { "GET /api/v1/me": () => Response.json(aProfile) };

function puts(requests: Seen[]) {
  return requests.filter((r) => r.method === "PUT" && r.path === "/api/v1/me");
}

describe("signed out", () => {
  it("sends every profile screen to sign-in with a way back", async () => {
    renderApp("/me/profile", screens, {
      "GET /api/v1/me": () => problem(401, "unauthenticated"),
    });
    expect(
      await screen.findByText("now at /sign-in?returnTo=%2Fme%2Fprofile"),
    ).toBeTruthy();
  });
});

describe("ProfilePage layout", () => {
  it("is a sub-page of Me: back link, h1 in the top bar, form and phone in Cards", async () => {
    renderApp("/me/profile", screens, signedIn);
    const h1 = await screen.findByRole("heading", {
      level: 1,
      name: "Profile",
    });
    // The back link lives in the top bar and shows below 768 px only.
    const back = within(h1.closest("header") as HTMLElement).getByRole("link", {
      name: "Me",
    });
    expect(back.getAttribute("href")).toBe("/me");
    expect(back.className).toContain("md:hidden");
    const form = screen.getByLabelText("Display name").closest("section");
    expect(form?.className).toContain("bg-surface");
    expect(
      screen.getByRole("region", { name: "Mobile number" }).className,
    ).toContain("border-border");
  });
});

describe("MePage", () => {
  it("shows who you are, the Profile row and Sign out, and nothing unbuilt", async () => {
    renderApp("/me", screens, signedIn);
    expect(
      await screen.findByRole("heading", { name: "Asha Rao" }),
    ).toBeTruthy();
    expect(screen.getByText("asha@example.com")).toBeTruthy();
    expect(
      screen.getByRole("link", { name: "Edit profile" }).getAttribute("href"),
    ).toBe("/me/profile");
    expect(screen.getByRole("link", { name: /Profile/ })).toBeTruthy();
    expect(
      within(screen.getByRole("main")).getByRole("img", { name: "Asha Rao" })
        .textContent,
    ).toBe("AR");
    expect(
      screen.queryByText(/Appearance|Notifications|Security|Locale|Test link/),
    ).toBeNull();
    expect(screen.getByRole("button", { name: "Sign out" })).toBeTruthy();
  });

  it("draws the header as a Card with a 72 px avatar and the list as one flush Card of 64 px rows", async () => {
    renderApp("/me", screens, signedIn);
    const name = await screen.findByRole("heading", { name: "Asha Rao" });
    const main = screen.getByRole("main");
    expect(name.closest("header")?.className).toContain("bg-surface");
    expect(
      within(main).getByRole("img", { name: "Asha Rao" }).style.width,
    ).toBe("72px");
    const list = screen.getByRole("region", { name: "Settings" });
    expect(list.className).toContain("overflow-hidden");
    const row = within(list).getByRole("link", { name: /Profile/ });
    expect(row.className).toContain("min-h-16");
    expect(within(list).getAllByRole("link")).toHaveLength(1);
  });

  it("makes Sign out a full-width row with a negative icon tile, like the Profile row", async () => {
    renderApp("/me", screens, signedIn);
    const button = await screen.findByRole("button", { name: "Sign out" });
    expect(button.className).toContain("w-full");
    expect(button.className).toContain("min-h-16");
    expect(button.querySelector(".bg-negative-soft")).toBeTruthy();
  });

  it("uses the full content width, not a narrow column", async () => {
    renderApp("/me", screens, signedIn);
    const name = await screen.findByRole("heading", { name: "Asha Rao" });
    expect(name.closest("header")?.parentElement?.className).not.toMatch(
      /max-w-/,
    );
  });

  it("signs out and returns to sign-in", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me", screens, {
      ...signedIn,
      "POST /api/v1/auth/sign-out": () => new Response(null, { status: 204 }),
    });
    await user.click(await screen.findByRole("button", { name: "Sign out" }));

    expect(await screen.findByText("now at /sign-in")).toBeTruthy();
    expect(
      requests
        .find((r) => r.path === "/api/v1/auth/sign-out")
        ?.headers.get("X-XSRF-TOKEN"),
    ).toBe("tok-1");
  });

  it("keeps the person here with words when sign-out fails", async () => {
    const user = userEvent.setup();
    renderApp("/me", screens, {
      ...signedIn,
      "POST /api/v1/auth/sign-out": () => problem(500, "internal"),
    });
    await user.click(await screen.findByRole("button", { name: "Sign out" }));
    expect((await screen.findByRole("alert")).textContent).toMatch(/our side/);
    expect(screen.queryByText(/now at/)).toBeNull();
  });
});

describe("ProfilePage", () => {
  it("shows the saved values with a read-only email and no unbuilt rows", async () => {
    renderApp("/me/profile", screens, {
      "GET /api/v1/me": () =>
        Response.json({
          ...aProfile,
          defaultCurrency: "USD",
          upiId: "asha@okbank",
        }),
    });
    expect(
      ((await screen.findByLabelText("Display name")) as HTMLInputElement)
        .value,
    ).toBe("Asha Rao");
    expect((screen.getByLabelText("Email") as HTMLInputElement).readOnly).toBe(
      true,
    );
    expect(
      (screen.getByLabelText("Default currency") as HTMLSelectElement).value,
    ).toBe("USD");
    expect(
      (screen.getByLabelText("UPI ID (optional)") as HTMLInputElement).value,
    ).toBe("asha@okbank");
    expect(screen.queryByText(/Test link|Locale/)).toBeNull();
  });

  it("saves with the version it started from and says so", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me/profile", screens, {
      ...signedIn,
      "PUT /api/v1/me": () =>
        Response.json({
          ...aProfile,
          displayName: "Asha K",
          upiId: "asha@okbank",
          version: 1,
        }),
    });
    const name = await screen.findByLabelText("Display name");
    await user.clear(name);
    await user.type(name, "Asha K");
    await user.type(screen.getByLabelText("UPI ID (optional)"), "asha@okbank");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("status")).toBeTruthy();
    expect(screen.getByRole("status").textContent).toBe("Saved.");
    expect(puts(requests)[0].body).toEqual({
      displayName: "Asha K",
      defaultCurrency: "INR",
      upiId: "asha@okbank",
      directAdd: "ALLOW",
      version: 0,
    });
    expect(puts(requests)[0].headers.get("X-XSRF-TOKEN")).toBe("tok-1");
  });

  it("allows the UPI ID to be empty and leaves it out of the request", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me/profile", screens, {
      ...signedIn,
      "PUT /api/v1/me": () => Response.json({ ...aProfile, version: 1 }),
    });
    await user.click(await screen.findByRole("button", { name: "Save" }));
    await screen.findByRole("status");
    expect(puts(requests)[0].body).toEqual({
      displayName: "Asha Rao",
      defaultCurrency: "INR",
      directAdd: "ALLOW",
      version: 0,
    });
  });

  it("refuses a malformed UPI ID before calling the server", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me/profile", screens, signedIn);
    await user.type(
      await screen.findByLabelText("UPI ID (optional)"),
      "not-a-upi",
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(screen.getByText("Use a UPI ID like name@okbank.")).toBeTruthy();
    expect(puts(requests)).toHaveLength(0);
  });

  it("changes the currency", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me/profile", screens, {
      ...signedIn,
      "PUT /api/v1/me": () =>
        Response.json({ ...aProfile, defaultCurrency: "EUR", version: 1 }),
    });
    await user.selectOptions(
      await screen.findByLabelText("Default currency"),
      "EUR",
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByRole("status");
    expect(
      (puts(requests)[0].body as { defaultCurrency: string }).defaultCurrency,
    ).toBe("EUR");
  });

  it("explains a version conflict in words and offers the latest version", async () => {
    const user = userEvent.setup();
    let reads = 0;
    renderApp("/me/profile", screens, {
      "GET /api/v1/me": () => {
        reads += 1;
        return Response.json(
          reads === 1
            ? aProfile
            : { ...aProfile, displayName: "Changed Elsewhere", version: 3 },
        );
      },
      "PUT /api/v1/me": () => problem(409, "version-conflict"),
    });
    await user.click(await screen.findByRole("button", { name: "Save" }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("changed somewhere else");
    expect(alert.textContent).not.toMatch(/409|urn:/);
    await user.click(
      screen.getByRole("button", { name: "Load the latest version" }),
    );
    await waitFor(() =>
      expect(
        (screen.getByLabelText("Display name") as HTMLInputElement).value,
      ).toBe("Changed Elsewhere"),
    );
    expect(screen.queryByRole("alert")).toBeNull();
  });
});

describe("WelcomePage", () => {
  it("preselects INR, previews the avatar and goes to Me with Let's go", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/welcome", screens, {
      ...signedIn,
      "PUT /api/v1/me": () =>
        Response.json({
          ...aProfile,
          displayName: "Priya Raman",
          version: 1,
        }),
    });
    expect(
      ((await screen.findByLabelText("Default currency")) as HTMLSelectElement)
        .value,
    ).toBe("INR");
    expect(
      within(screen.getByRole("main")).getByRole("img", { name: "Asha Rao" })
        .textContent,
    ).toBe("AR");
    const name = screen.getByLabelText("Display name");
    await user.clear(name);
    await user.type(name, "Priya Raman");
    expect(screen.getByRole("img", { name: "Priya Raman" }).textContent).toBe(
      "PR",
    );
    await user.click(screen.getByRole("button", { name: "Let's go" }));
    await waitFor(() => expect(puts(requests)).toHaveLength(1));
    expect(
      await screen.findByRole("heading", { name: "Priya Raman" }),
    ).toBeTruthy();
  });

  it("lets the person skip without saving", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/welcome", screens, signedIn);
    await user.click(
      await screen.findByRole("button", { name: "Skip for now" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Asha Rao" }),
    ).toBeTruthy();
    expect(puts(requests)).toHaveLength(0);
  });

  it("has an optional UPI helper in the design's words", async () => {
    renderApp("/welcome", screens, signedIn);
    expect(
      await screen.findByText(
        "So friends can pay you with one tap. Looks like name@okbank.",
      ),
    ).toBeTruthy();
    expect(within(document.body).queryByText(/Test link/)).toBeNull();
  });
});

describe("who can add me directly", () => {
  it("is on by default and explains what it does", async () => {
    renderApp("/me/profile", screens, signedIn);
    const toggle = (await screen.findByLabelText(
      "Let group Owners add me directly",
    )) as HTMLInputElement;
    expect(toggle.checked).toBe(true);
    expect(
      screen.getByText(
        "Turn this off to join groups only through an invite link.",
      ),
    ).toBeTruthy();
  });

  it("saves invite-only with the version the form started from", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me/profile", screens, {
      ...signedIn,
      "PUT /api/v1/me": () =>
        Response.json({ ...aProfile, directAdd: "INVITE_ONLY", version: 1 }),
    });
    await user.click(
      await screen.findByLabelText("Let group Owners add me directly"),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect((await screen.findByRole("status")).textContent).toBe("Saved.");
    expect(puts(requests)[0].body).toEqual({
      displayName: "Asha Rao",
      defaultCurrency: "INR",
      directAdd: "INVITE_ONLY",
      version: 0,
    });
    expect(
      (
        screen.getByLabelText(
          "Let group Owners add me directly",
        ) as HTMLInputElement
      ).checked,
    ).toBe(false);
  });

  it("shows the stored invite-only choice and switches back to allow", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/me/profile", screens, {
      "GET /api/v1/me": () =>
        Response.json({ ...aProfile, directAdd: "INVITE_ONLY", version: 3 }),
      "PUT /api/v1/me": () =>
        Response.json({ ...aProfile, directAdd: "ALLOW", version: 4 }),
    });
    const toggle = (await screen.findByLabelText(
      "Let group Owners add me directly",
    )) as HTMLInputElement;
    expect(toggle.checked).toBe(false);

    await user.click(toggle);
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByRole("status");
    expect((puts(requests)[0].body as { directAdd: string }).directAdd).toBe(
      "ALLOW",
    );
  });

  it("is not part of the welcome step, which leaves the stored choice alone", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/welcome", screens, {
      ...signedIn,
      "PUT /api/v1/me": () => Response.json({ ...aProfile, version: 1 }),
    });
    await screen.findByLabelText("Display name");
    expect(
      screen.queryByLabelText("Let group Owners add me directly"),
    ).toBeNull();
    await user.click(screen.getByRole("button", { name: "Let's go" }));
    await waitFor(() => expect(puts(requests)).toHaveLength(1));
    expect(puts(requests)[0].body).not.toHaveProperty("directAdd");
  });
});
