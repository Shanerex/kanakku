import { act, cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { profileRoutes } from "../../app/routes";
import { aProfile, problem, renderApp, type Seen } from "../../test/harness";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const PHONE = "/api/v1/me/phone";
const SEND = "/api/v1/me/phone/verification";
const CONFIRM = "/api/v1/me/phone/verification/confirm";

const withNumber = {
  ...aProfile,
  phone: "+919876543210",
  phoneVerified: false,
};
const verified = { ...withNumber, phoneVerified: true, version: 2 };

function handlers(
  profile: object,
  codes: boolean,
  extra: Record<string, () => Response> = {},
) {
  return {
    "GET /api/v1/me": () => Response.json(profile),
    "GET /api/v1/auth/sign-up-options": () =>
      Response.json({ phoneVerification: codes }),
    ...extra,
  };
}

function section() {
  return within(screen.getByRole("region", { name: "Mobile number" }));
}

function calls(requests: Seen[], method: string, path: string) {
  return requests.filter((r) => r.method === method && r.path === path);
}

describe("PhoneSection", () => {
  it("invites you to add a number when there is none", async () => {
    renderApp("/me/profile", profileRoutes, handlers(aProfile, true));
    await screen.findByRole("heading", { name: "Mobile number" });

    expect(
      section().getByText("Add your number so friends can find you."),
    ).toBeTruthy();
    expect(section().getByLabelText("Mobile number")).toBeTruthy();
    expect(section().queryByText("Unverified")).toBeNull();
  });

  it("saves a number with the profile's version and shows it as unverified", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/me/profile",
      profileRoutes,
      handlers(aProfile, true, {
        [`PUT ${PHONE}`]: () => Response.json({ ...withNumber, version: 1 }),
      }),
    );
    await user.type(await section_find(), "98765 43210");
    await user.click(section().getByRole("button", { name: "Save number" }));

    expect(await section().findByText("+91 98765 43210")).toBeTruthy();
    expect(section().getByText("Unverified")).toBeTruthy();
    expect(calls(requests, "PUT", PHONE)[0].body).toEqual({
      phone: "98765 43210",
      version: 0,
    });
    expect(section().getByRole("button", { name: "Send code" })).toBeTruthy();
  });

  async function section_find() {
    await screen.findByRole("heading", { name: "Mobile number" });
    return section().getByLabelText("Mobile number");
  }

  it("asks for a number before saving and shows invalid and taken numbers on the field", async () => {
    const user = userEvent.setup();
    let answer: Response = problem(400, "invalid-phone");
    const { requests } = renderApp(
      "/me/profile",
      profileRoutes,
      handlers(aProfile, true, { [`PUT ${PHONE}`]: () => answer }),
    );
    const field = await section_find();
    await user.click(section().getByRole("button", { name: "Save number" }));
    expect(section().getByText("Enter your mobile number.")).toBeTruthy();
    expect(calls(requests, "PUT", PHONE)).toHaveLength(0);

    await user.type(field, "12345");
    await user.click(section().getByRole("button", { name: "Save number" }));
    expect(
      await section().findByText("Enter a mobile number like 98765 43210."),
    ).toBeTruthy();

    answer = problem(409, "phone-taken");
    await user.click(section().getByRole("button", { name: "Save number" }));
    await waitFor(() =>
      expect(section().getByText(/verified on another account/)).toBeTruthy(),
    );
  });

  it("texts a code, then verifies the number with the right one", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/me/profile",
      profileRoutes,
      handlers(withNumber, true, {
        [`POST ${SEND}`]: () =>
          Response.json(
            { codeExpiresAt: "2026-10-25T12:00:00Z", smsSent: true },
            { status: 202 },
          ),
        [`POST ${CONFIRM}`]: () => Response.json(verified),
      }),
    );
    await user.click(await section_button("Send code"));

    expect(
      await section().findByText(
        /We texted a 6-digit code to \+91 98765 43210/,
      ),
    ).toBeTruthy();
    expect(
      calls(requests, "POST", SEND)[0].headers.get("Idempotency-Key"),
    ).toMatch(/^[0-9a-f-]{36}$/);
    await user.type(section().getByLabelText("6-digit code"), "123456");
    await user.click(section().getByRole("button", { name: "Confirm number" }));

    expect(await section().findByText("Verified")).toBeTruthy();
    expect(section().queryByText("Unverified")).toBeNull();
    expect(calls(requests, "POST", CONFIRM)[0].body).toEqual({
      code: "123456",
    });
    expect(section().queryByLabelText("6-digit code")).toBeNull();
  });

  async function section_button(name: string) {
    await screen.findByRole("heading", { name: "Mobile number" });
    return section().findByRole("button", { name });
  }

  it("shows a wrong code on the field and keeps the number unverified", async () => {
    const user = userEvent.setup();
    renderApp(
      "/me/profile",
      profileRoutes,
      handlers(withNumber, true, {
        [`POST ${SEND}`]: () =>
          Response.json(
            { codeExpiresAt: "2026-10-25T12:00:00Z", smsSent: true },
            { status: 202 },
          ),
        [`POST ${CONFIRM}`]: () => problem(422, "wrong-code"),
      }),
    );
    await user.click(await section_button("Send code"));
    await user.type(await section().findByLabelText("6-digit code"), "000000");
    await user.click(section().getByRole("button", { name: "Confirm number" }));

    expect(
      await section().findByText(
        "That code isn't right. Check the text and try again.",
      ),
    ).toBeTruthy();
    expect(section().getByText("Unverified")).toBeTruthy();
  });

  it("says how long a lock lasts after too many wrong codes", async () => {
    const user = userEvent.setup();
    renderApp(
      "/me/profile",
      profileRoutes,
      handlers(withNumber, true, {
        [`POST ${SEND}`]: () =>
          Response.json(
            { codeExpiresAt: "2026-10-25T12:00:00Z", smsSent: true },
            { status: 202 },
          ),
        [`POST ${CONFIRM}`]: () =>
          problem(429, "code-locked", { retryAfterSeconds: 600 }),
      }),
    );
    await user.click(await section_button("Send code"));
    await user.type(await section().findByLabelText("6-digit code"), "123456");
    await user.click(section().getByRole("button", { name: "Confirm number" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "10 minutes",
    );
  });

  it("makes you wait 30 seconds before the next text", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { requests } = renderApp(
      "/me/profile",
      profileRoutes,
      handlers(withNumber, true, {
        [`POST ${SEND}`]: () =>
          Response.json(
            { codeExpiresAt: "2026-10-25T12:00:00Z", smsSent: true },
            { status: 202 },
          ),
      }),
    );
    await user.click(await section_button("Send code"));

    const wait = await section().findByRole("button", {
      name: /Resend in 30 s/,
    });
    expect((wait as HTMLButtonElement).disabled).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    await user.click(
      await section().findByRole("button", { name: "Resend code" }),
    );
    await waitFor(() => expect(calls(requests, "POST", SEND)).toHaveLength(2));
    const keys = calls(requests, "POST", SEND).map((r) =>
      r.headers.get("Idempotency-Key"),
    );
    expect(keys[0]).not.toBe(keys[1]);
  });

  it("says so when the text could not be sent", async () => {
    const user = userEvent.setup();
    renderApp(
      "/me/profile",
      profileRoutes,
      handlers(withNumber, true, {
        [`POST ${SEND}`]: () =>
          Response.json(
            { codeExpiresAt: "2026-10-25T12:00:00Z", smsSent: false },
            { status: 202 },
          ),
      }),
    );
    await user.click(await section_button("Send code"));

    expect(
      await section().findByText("We couldn't send the text"),
    ).toBeTruthy();
  });

  it("shows Unverified with no way to verify when the server cannot text", async () => {
    renderApp("/me/profile", profileRoutes, handlers(withNumber, false));
    await screen.findByRole("heading", { name: "Mobile number" });

    expect(await section().findByText("Unverified")).toBeTruthy();
    expect(
      await section().findByText(
        "This server can't send texts, so your number stays unverified.",
      ),
    ).toBeTruthy();
    expect(section().queryByRole("button", { name: "Send code" })).toBeNull();
    expect(
      section().getByRole("button", { name: "Change number" }),
    ).toBeTruthy();
  });

  it("shows a verified number and replaces it from 'Change number'", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/me/profile",
      profileRoutes,
      handlers(verified, true, {
        [`PUT ${PHONE}`]: () =>
          Response.json({
            ...verified,
            phone: "+919123456780",
            phoneVerified: false,
            version: 3,
          }),
      }),
    );
    await screen.findByRole("heading", { name: "Mobile number" });
    expect(await section().findByText("Verified")).toBeTruthy();
    expect(section().queryByRole("button", { name: "Send code" })).toBeNull();

    await user.click(section().getByRole("button", { name: "Change number" }));
    await user.type(section().getByLabelText("Mobile number"), "91234 56780");
    await user.click(section().getByRole("button", { name: "Save number" }));

    expect(await section().findByText("Unverified")).toBeTruthy();
    expect(calls(requests, "PUT", PHONE)[0].body).toEqual({
      phone: "91234 56780",
      version: 2,
    });
  });

  it("removes a number with the profile's version and offers to add one again", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp(
      "/me/profile",
      profileRoutes,
      handlers(verified, false, {
        [`DELETE ${PHONE}`]: () =>
          Response.json({
            ...verified,
            phone: undefined,
            phoneVerified: false,
            version: 3,
          }),
      }),
    );
    await user.click(
      await section().findByRole("button", { name: "Remove number" }),
    );

    expect(
      await section().findByText("Add your number so friends can find you."),
    ).toBeTruthy();
    expect(section().queryByText("+91 98765 43210")).toBeNull();
    expect(calls(requests, "DELETE", PHONE)[0].search).toBe("?version=2");
  });

  it("offers the latest profile after a version conflict", async () => {
    const user = userEvent.setup();
    renderApp(
      "/me/profile",
      profileRoutes,
      handlers(aProfile, true, {
        [`PUT ${PHONE}`]: () => problem(409, "version-conflict"),
      }),
    );
    await user.type(await section_find(), "98765 43210");
    await user.click(section().getByRole("button", { name: "Save number" }));

    expect(
      await screen.findByRole("button", { name: "Load the latest version" }),
    ).toBeTruthy();
  });

  it("keeps unsaved profile edits when the number is saved", async () => {
    const user = userEvent.setup();
    renderApp(
      "/me/profile",
      profileRoutes,
      handlers(aProfile, true, {
        [`PUT ${PHONE}`]: () => Response.json({ ...withNumber, version: 1 }),
      }),
    );
    const name = await screen.findByLabelText("Display name");
    await user.clear(name);
    await user.type(name, "Asha K");
    await user.type(section().getByLabelText("Mobile number"), "98765 43210");
    await user.click(section().getByRole("button", { name: "Save number" }));
    await section().findByText("Unverified");

    expect(
      (screen.getByLabelText("Display name") as HTMLInputElement).value,
    ).toBe("Asha K");
  });
});
