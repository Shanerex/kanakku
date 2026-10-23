import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { RouteObject } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { authRoutes } from "../../app/routes";
import { aProfile, problem, renderApp, type Seen } from "../../test/harness";

afterEach(cleanup);

const welcome: RouteObject = { path: "/welcome", element: <p>Welcome page</p> };
const screens = [...authRoutes, welcome];
const signedOut = { "GET /api/v1/me": () => problem(401, "unauthenticated") };

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  values: { name?: string; email?: string; password?: string },
) {
  if (values.name !== undefined)
    await user.type(await screen.findByLabelText("Display name"), values.name);
  if (values.email !== undefined)
    await user.type(await screen.findByLabelText("Email"), values.email);
  if (values.password !== undefined)
    await user.type(await screen.findByLabelText("Password"), values.password);
}

function posts(requests: Seen[], path: string) {
  return requests.filter((r) => r.method === "POST" && r.path === path);
}

describe("SignUpPage", () => {
  it("shows the form and none of the hidden extras", async () => {
    renderApp("/sign-up", screens, signedOut);
    expect(
      await screen.findByRole("heading", { name: "Create your account" }),
    ).toBeTruthy();
    expect(
      screen.getByText("8 to 128 characters. A passphrase works well."),
    ).toBeTruthy();
    expect(screen.queryByText(/Continue with/)).toBeNull();
    expect(screen.queryByText("or")).toBeNull();
    expect(
      screen.getByRole("link", { name: "Sign in" }).getAttribute("href"),
    ).toBe("/sign-in");
  });

  it("validates before sending anything and focuses the first problem", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-up", screens, signedOut);
    await fill(user, { name: "Asha", email: "nope", password: "short" });
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      screen.getByText("Enter an email like name@example.com."),
    ).toBeTruthy();
    expect(screen.getByText("Use 8 to 128 characters.")).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByLabelText("Email"));
    expect(posts(requests, "/api/v1/auth/sign-up")).toHaveLength(0);
  });

  it("shows the password strength with words", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, signedOut);
    await fill(user, { password: "abc" });
    expect(screen.getByTestId("strength").textContent).toContain(
      "Strength: Too short",
    );
    await user.type(screen.getByLabelText("Password"), "defgh");
    expect(screen.getByTestId("strength").textContent).toContain(
      "Strength: Okay",
    );
    await user.type(screen.getByLabelText("Password"), "ijklm");
    expect(screen.getByTestId("strength").textContent).toContain(
      "Strength: Strong",
    );
  });

  it("creates the account, signs in and goes to /welcome", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-up", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-up": () =>
        Response.json(aProfile, { status: 201 }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await fill(user, {
      name: "Asha Rao",
      email: "asha@example.com",
      password: "correct horse",
    });
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    const [signUp] = posts(requests, "/api/v1/auth/sign-up");
    expect(signUp.body).toEqual({
      displayName: "Asha Rao",
      email: "asha@example.com",
      password: "correct horse",
    });
    expect(signUp.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(signUp.headers.get("X-XSRF-TOKEN")).toBe("tok-1");
    const [signIn] = posts(requests, "/api/v1/auth/sign-in");
    expect(signIn.body).toEqual({
      email: "asha@example.com",
      password: "correct horse",
    });
    expect(
      requests
        .map((r) => `${r.method} ${r.path}`)
        .filter((r) => r.startsWith("POST")),
    ).toEqual(["POST /api/v1/auth/sign-up", "POST /api/v1/auth/sign-in"]);
  });

  it("keeps the same Idempotency-Key when retrying the same form, and a new one after a change", async () => {
    const user = userEvent.setup();
    let calls = 0;
    const { requests } = renderApp("/sign-up", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-up": () => {
        calls += 1;
        return calls < 3
          ? problem(500, "internal")
          : Response.json(aProfile, { status: 201 });
      },
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await fill(user, {
      name: "Asha",
      email: "asha@example.com",
      password: "correct horse",
    });
    const submit = screen.getByRole("button", { name: "Create account" });

    await user.click(submit);
    expect((await screen.findByRole("alert")).textContent).toMatch(/our side/);
    await user.click(submit);
    await waitFor(() =>
      expect(posts(requests, "/api/v1/auth/sign-up")).toHaveLength(2),
    );
    await user.type(screen.getByLabelText("Display name"), "!");
    await waitFor(() => expect(screen.queryByText("Welcome page")).toBeNull());
    await user.click(submit);

    expect(await screen.findByText("Welcome page")).toBeTruthy();
    const keys = posts(requests, "/api/v1/auth/sign-up").map((r) =>
      r.headers.get("Idempotency-Key"),
    );
    expect(keys).toHaveLength(3);
    expect(keys[0]).toBe(keys[1]);
    expect(keys[2]).not.toBe(keys[1]);
  });

  it("says in words when the email is taken", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-up": () => problem(409, "email-taken"),
    });
    await fill(user, {
      name: "Asha",
      email: "asha@example.com",
      password: "correct horse",
    });
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "already has an account",
    );
    expect(screen.getByText("That email already has an account.")).toBeTruthy();
    expect(screen.queryByText(/409|urn:/)).toBeNull();
  });

  it("shows field errors from a 400 on the fields", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-up": () =>
        problem(400, "validation", {
          errors: [
            { field: "email", message: "must be a well-formed email address" },
          ],
        }),
    });
    await fill(user, { name: "Asha", email: "a@b", password: "correct horse" });
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByText("must be a well-formed email address"),
    ).toBeTruthy();
    expect(screen.getByLabelText("Email").getAttribute("aria-invalid")).toBe(
      "true",
    );
  });

  it("shows words when the server cannot be reached", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-up": () => {
        throw new TypeError("Failed to fetch");
      },
    });
    await fill(user, {
      name: "Asha",
      email: "asha@example.com",
      password: "correct horse",
    });
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "Can't reach Kanakku",
    );
  });

  it("ignores a second press while the first is in flight", async () => {
    const user = userEvent.setup();
    let release: (response: Response) => void = () => {};
    const { requests } = renderApp("/sign-up", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-up": () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await fill(user, {
      name: "Asha",
      email: "asha@example.com",
      password: "correct horse",
    });
    const submit = screen.getByRole("button", { name: "Create account" });
    await user.click(submit);
    await user.click(submit);
    await waitFor(() =>
      expect(posts(requests, "/api/v1/auth/sign-up")).toHaveLength(1),
    );
    release(Response.json(aProfile, { status: 201 }));
    expect(await screen.findByText("Welcome page")).toBeTruthy();
    expect(posts(requests, "/api/v1/auth/sign-up")).toHaveLength(1);
  });

  it("reveals the password only on request", async () => {
    const user = userEvent.setup();
    renderApp("/sign-up", screens, signedOut);
    const field = await screen.findByLabelText("Password");
    expect(field.getAttribute("type")).toBe("password");
    const toggle = screen.getByRole("button", { name: "Show password" });
    expect(toggle.getAttribute("aria-pressed")).toBe("false");
    await user.click(toggle);
    expect(field.getAttribute("type")).toBe("text");
    expect(toggle.getAttribute("aria-pressed")).toBe("true");
  });
});
