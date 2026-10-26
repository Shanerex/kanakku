import { act, cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { RouteObject } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { authRoutes } from "../../app/routes";
import { aProfile, problem, renderApp, type Seen } from "../../test/harness";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const welcome: RouteObject = { path: "/welcome", element: <p>Welcome page</p> };
const screens = [...authRoutes, welcome];
const signedOut = { "GET /api/v1/me": () => problem(401, "unauthenticated") };
const withCodes = {
  ...signedOut,
  "GET /api/v1/auth/sign-up-options": () =>
    Response.json({ phoneVerification: true }),
};
const withoutCodes = {
  ...signedOut,
  "GET /api/v1/auth/sign-up-options": () =>
    Response.json({ phoneVerification: false }),
};

const PENDING = {
  id: "55555555-5555-4555-8555-555555555555",
  maskedPhone: "+91 ••••• ••10",
  codeExpiresAt: "2026-10-25T12:00:00Z",
  smsSent: true,
};

function posts(requests: Seen[], path: string) {
  return requests.filter((r) => r.method === "POST" && r.path === path);
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  phone?: string,
) {
  await user.type(await screen.findByLabelText("Display name"), "Asha Rao");
  await user.type(screen.getByLabelText("Email"), "asha@example.com");
  await user.type(screen.getByLabelText("Password"), "correct horse");
  if (phone)
    await user.type(await screen.findByLabelText(/Mobile number/), phone);
}

const VERIFY = `/api/v1/auth/sign-ups/${PENDING.id}/verify`;
const RESEND = `/api/v1/auth/sign-ups/${PENDING.id}/resend`;
const FINISH = `/api/v1/auth/sign-ups/${PENDING.id}/finish-unverified`;

describe("SignUpPage phone number", () => {
  it("asks for the number and says it is optional when the server cannot text", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-up", screens, {
      ...withoutCodes,
      "POST /api/v1/auth/sign-up": () =>
        Response.json(aProfile, { status: 201 }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    expect(
      await screen.findByLabelText("Mobile number (optional)"),
    ).toBeTruthy();
    expect(screen.getByText(/stays unverified on this server/)).toBeTruthy();

    await fillForm(user, "98765 43210");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    expect(posts(requests, "/api/v1/auth/sign-up")[0].body).toEqual({
      displayName: "Asha Rao",
      email: "asha@example.com",
      password: "correct horse",
      phone: "98765 43210",
    });
  });

  it("lets the number stay empty when the server cannot text", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-up", screens, {
      ...withoutCodes,
      "POST /api/v1/auth/sign-up": () =>
        Response.json(aProfile, { status: 201 }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await screen.findByLabelText("Mobile number (optional)");
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    expect(posts(requests, "/api/v1/auth/sign-up")[0].body).not.toHaveProperty(
      "phone",
    );
  });

  it("lets the number stay empty even when the server texts codes", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-up", screens, {
      ...withCodes,
      "POST /api/v1/auth/sign-up": () =>
        Response.json(aProfile, { status: 201 }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    expect(
      await screen.findByLabelText("Mobile number (optional)"),
    ).toBeTruthy();
    expect(screen.getByText(/or you can skip that/)).toBeTruthy();
    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    expect(posts(requests, "/api/v1/auth/sign-up")[0].body).not.toHaveProperty(
      "phone",
    );
  });

  it("shows an invalid number on the phone field in words", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, {
      ...withCodes,
      "POST /api/v1/auth/sign-up": () => problem(400, "invalid-phone"),
    });
    await fillForm(user, "12345");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByText("Enter a mobile number like 98765 43210."),
    ).toBeTruthy();
    expect(
      screen.getByLabelText(/Mobile number/).getAttribute("aria-invalid"),
    ).toBe("true");
  });

  it("says a number verified elsewhere belongs to another account", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, {
      ...withCodes,
      "POST /api/v1/auth/sign-up": () => problem(409, "phone-taken"),
    });
    await fillForm(user, "98765 43210");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText(/verified on another account/)).toBeTruthy();
    expect(
      screen.getByRole("heading", { name: "Create your account" }),
    ).toBeTruthy();
  });
});

describe("SignUpPage code step", () => {
  function start(extra: Record<string, () => Response> = {}) {
    return renderApp("/sign-up", screens, {
      ...withCodes,
      "POST /api/v1/auth/sign-up": () =>
        Response.json(PENDING, { status: 202 }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
      ...extra,
    });
  }

  async function toCodeStep(user: ReturnType<typeof userEvent.setup>) {
    await fillForm(user, "98765 43210");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    await screen.findByRole("heading", { name: "Enter your code" });
  }

  it("swaps the form for the code step showing the masked number only", async () => {
    const user = userEvent.setup();
    start();
    await toCodeStep(user);

    expect(screen.getByText("+91 ••••• ••10")).toBeTruthy();
    expect(screen.queryByText(/98765/)).toBeNull();
    expect(screen.queryByLabelText("Email")).toBeNull();
    const field = screen.getByLabelText("6-digit code");
    expect(field.getAttribute("autocomplete")).toBe("one-time-code");
    expect(field.getAttribute("inputmode")).toBe("numeric");
  });

  it("keeps digits only, six at most, and enables the button at six", async () => {
    const user = userEvent.setup();
    start();
    await toCodeStep(user);
    const button = screen.getByRole("button", { name: "Create account" });
    expect((button as HTMLButtonElement).disabled).toBe(true);

    await user.type(screen.getByLabelText("6-digit code"), "12ab34567");

    expect(
      (screen.getByLabelText("6-digit code") as HTMLInputElement).value,
    ).toBe("123456");
    expect((button as HTMLButtonElement).disabled).toBe(false);
  });

  it("creates the account on the right code, signs in and goes to /welcome", async () => {
    const user = userEvent.setup();
    const { requests } = start({
      [`POST ${VERIFY}`]: () => Response.json(aProfile, { status: 201 }),
    });
    await toCodeStep(user);
    await user.type(screen.getByLabelText("6-digit code"), "123456");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    const [verify] = posts(requests, VERIFY);
    expect(verify.body).toEqual({ code: "123456" });
    expect(verify.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(posts(requests, "/api/v1/auth/sign-in")[0].body).toEqual({
      email: "asha@example.com",
      password: "correct horse",
    });
  });

  it("shows a wrong code on the field and sends a fresh key for the next try", async () => {
    const user = userEvent.setup();
    let calls = 0;
    const { requests } = start({
      [`POST ${VERIFY}`]: () => {
        calls += 1;
        return calls === 1
          ? problem(422, "wrong-code")
          : Response.json(aProfile, { status: 201 });
      },
    });
    await toCodeStep(user);
    await user.type(screen.getByLabelText("6-digit code"), "000000");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByText(
        "That code isn't right. Check the text and try again.",
      ),
    ).toBeTruthy();
    expect(screen.queryByText("Welcome page")).toBeNull();

    await user.clear(screen.getByLabelText("6-digit code"));
    await user.type(screen.getByLabelText("6-digit code"), "123456");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Welcome page")).toBeTruthy();
    const keys = posts(requests, VERIFY).map((r) =>
      r.headers.get("Idempotency-Key"),
    );
    expect(keys).toHaveLength(2);
    expect(keys[0]).not.toBe(keys[1]);
  });

  it("locks the button after too many wrong codes and says how long", async () => {
    const user = userEvent.setup();
    start({
      [`POST ${VERIFY}`]: () =>
        problem(429, "code-locked", { retryAfterSeconds: 720 }),
    });
    await toCodeStep(user);
    await user.type(screen.getByLabelText("6-digit code"), "123456");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("Too many wrong codes");
    expect(alert.textContent).toContain("12 minutes");
    expect(
      (
        screen.getByRole("button", {
          name: "Create account",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
  });

  it("asks for a new code when the code expired", async () => {
    const user = userEvent.setup();
    start({ [`POST ${VERIFY}`]: () => problem(410, "code-expired") });
    await toCodeStep(user);
    await user.type(screen.getByLabelText("6-digit code"), "123456");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByText("That code has expired. Send a new one."),
    ).toBeTruthy();
  });

  it("goes back to the form with a note when the sign-up timed out", async () => {
    const user = userEvent.setup();
    start({ [`POST ${VERIFY}`]: () => problem(410, "sign-up-expired") });
    await toCodeStep(user);
    await user.type(screen.getByLabelText("6-digit code"), "123456");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByRole("heading", { name: "Create your account" }),
    ).toBeTruthy();
    expect((await screen.findByRole("alert")).textContent).toContain(
      "timed out",
    );
    expect((screen.getByLabelText("Email") as HTMLInputElement).value).toBe(
      "asha@example.com",
    );
  });

  it("returns to the form with everything kept on 'Use a different number'", async () => {
    const user = userEvent.setup();
    start();
    await toCodeStep(user);
    await user.click(
      screen.getByRole("button", { name: "Use a different number" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Create your account" }),
    ).toBeTruthy();
    expect(
      (screen.getByLabelText(/Mobile number/) as HTMLInputElement).value,
    ).toBe("98765 43210");
  });

  it("waits 30 seconds before the code can be sent again, then resends", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { requests } = start({
      [`POST ${RESEND}`]: () => Response.json(PENDING, { status: 202 }),
    });
    await toCodeStep(user);
    const resend = screen.getByRole("button", { name: /Resend in 30 s/ });
    expect((resend as HTMLButtonElement).disabled).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    const ready = await screen.findByRole("button", { name: "Resend code" });
    expect((ready as HTMLButtonElement).disabled).toBe(false);
    await user.click(ready);

    await waitFor(() => expect(posts(requests, RESEND)).toHaveLength(1));
    expect(posts(requests, RESEND)[0].headers.get("Idempotency-Key")).toMatch(
      /^[0-9a-f-]{36}$/,
    );
    expect(await screen.findByRole("status")).toBeTruthy();
    expect(screen.getByRole("button", { name: /Resend in 30 s/ })).toBeTruthy();
  });

  it("offers 'Finish without verifying' when the text could not be sent", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-up", screens, {
      ...withCodes,
      "POST /api/v1/auth/sign-up": () =>
        Response.json({ ...PENDING, smsSent: false }, { status: 202 }),
      [`POST ${FINISH}`]: () => Response.json(aProfile, { status: 201 }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await toCodeStep(user);

    expect(screen.getByText("We couldn't send the text")).toBeTruthy();
    await user.click(
      screen.getByRole("button", { name: "Finish without verifying" }),
    );

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    expect(posts(requests, FINISH)).toHaveLength(1);
    expect(posts(requests, VERIFY)).toHaveLength(0);
  });

  it("offers 'Finish without verifying' even when the text was sent", async () => {
    const user = userEvent.setup();
    const { requests } = start({
      [`POST ${FINISH}`]: () => Response.json(aProfile, { status: 201 }),
    });
    await toCodeStep(user);

    expect(screen.getByText(/We texted a 6-digit code/)).toBeTruthy();
    await user.click(
      screen.getByRole("button", { name: "Finish without verifying" }),
    );

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    expect(posts(requests, FINISH)).toHaveLength(1);
    expect(posts(requests, VERIFY)).toHaveLength(0);
  });

  it("shows how long to wait when too many texts were asked for", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    start({
      [`POST ${RESEND}`]: () =>
        problem(429, "too-many-codes", { retryAfterSeconds: 2400 }),
    });
    await toCodeStep(user);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    await user.click(
      await screen.findByRole("button", { name: "Resend code" }),
    );

    expect((await screen.findByRole("alert")).textContent).toContain(
      "40 minutes",
    );
  });
});
