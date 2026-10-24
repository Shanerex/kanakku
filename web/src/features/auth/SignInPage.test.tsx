import { act, cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { type RouteObject, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { authRoutes } from "../../app/routes";
import { aProfile, problem, renderApp } from "../../test/harness";
import { safeReturnTo } from "./SignInPage";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function Here() {
  const location = useLocation();
  return <p>{`now at ${location.pathname}${location.search}`}</p>;
}

const destinations: RouteObject[] = [
  { path: "/me", element: <Here /> },
  { path: "/groups/:id", element: <Here /> },
];
const screens = [...authRoutes, ...destinations];
const signedOut = { "GET /api/v1/me": () => problem(401, "unauthenticated") };

async function signInWith(
  user: ReturnType<typeof userEvent.setup>,
  password = "right horse",
) {
  await user.type(await screen.findByLabelText("Email"), "asha@example.com");
  await user.type(screen.getByLabelText("Password"), password);
  await user.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("safeReturnTo", () => {
  it("accepts same-site paths and rejects the rest", () => {
    expect(safeReturnTo("/groups/1?x=2")).toBe("/groups/1?x=2");
    expect(safeReturnTo(null)).toBe("/me");
    expect(safeReturnTo("https://evil.example")).toBe("/me");
    expect(safeReturnTo("//evil.example")).toBe("/me");
    expect(safeReturnTo("/\\evil.example")).toBe("/me");
    expect(safeReturnTo("groups")).toBe("/me");
  });

  it("rejects control characters, tabs and newlines", () => {
    expect(safeReturnTo("/ok\npath")).toBe("/me");
    expect(safeReturnTo("/\t/evil.example")).toBe("/me");
    expect(safeReturnTo("/a\r\nSet-Cookie: x")).toBe("/me");
    expect(safeReturnTo("/a\u0000b")).toBe("/me");
    expect(safeReturnTo("/a\u007fb")).toBe("/me");
  });
});

describe("SignInPage", () => {
  it("shows the form without providers, divider or forgot-password", async () => {
    renderApp("/sign-in", screens, signedOut);
    expect(
      await screen.findByRole("heading", { name: "Welcome back" }),
    ).toBeTruthy();
    expect(screen.queryByText(/Continue with/)).toBeNull();
    expect(screen.queryByText(/Forgot password/)).toBeNull();
    expect(screen.queryByText(/Verify your email/)).toBeNull();
    expect(
      screen
        .getByRole("link", { name: "Create an account" })
        .getAttribute("href"),
    ).toBe("/sign-up");
  });

  it("signs in and goes home", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-in", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await signInWith(user);

    expect(await screen.findByText("now at /me")).toBeTruthy();
    const signIn = requests.find((r) => r.path === "/api/v1/auth/sign-in");
    expect(signIn?.body).toEqual({
      email: "asha@example.com",
      password: "right horse",
    });
    expect(signIn?.headers.get("X-XSRF-TOKEN")).toBe("tok-1");
  });

  it("returns to the page the visitor was going to", async () => {
    const user = userEvent.setup();
    renderApp("/sign-in?returnTo=%2Fgroups%2F7", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await signInWith(user);
    expect(await screen.findByText("now at /groups/7")).toBeTruthy();
  });

  it("ignores a returnTo that leaves the site", async () => {
    const user = userEvent.setup();
    renderApp("/sign-in?returnTo=https%3A%2F%2Fevil.example", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-in": () => Response.json(aProfile),
    });
    await signInWith(user);
    expect(await screen.findByText("now at /me")).toBeTruthy();
  });

  it("gives one generic message for wrong credentials", async () => {
    const user = userEvent.setup();
    renderApp("/sign-in", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-in": () => problem(401, "invalid-credentials"),
    });
    await signInWith(user, "wrong");

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toBe("That email and password don't match.");
    expect(
      screen.getByLabelText("Email").getAttribute("aria-invalid"),
    ).toBeNull();
  });

  it("asks for missing fields without calling the server", async () => {
    const user = userEvent.setup();
    const { requests } = renderApp("/sign-in", screens, signedOut);
    await user.click(await screen.findByRole("button", { name: "Sign in" }));
    expect(screen.getByText("Enter your email.")).toBeTruthy();
    expect(requests.some((r) => r.path === "/api/v1/auth/sign-in")).toBe(false);
  });

  it("counts down a 429 wait, disables the button, then lets the visitor try again", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderApp("/sign-in", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-in": () =>
        problem(429, "too-many-attempts", { retryAfterSeconds: 480 }),
    });
    await signInWith(user);

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("Too many tries");
    expect(alert.textContent).toContain("Try again in 8 minutes.");
    const button = screen.getByRole("button", {
      name: "Sign in",
    }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
    });
    expect(screen.getByRole("alert").textContent).toContain(
      "Try again in 3 minutes.",
    );
    expect(button.disabled).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3 * 60 * 1000 + 1000);
    });
    await waitFor(() => expect(button.disabled).toBe(false));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("shows words when the server cannot be reached", async () => {
    const user = userEvent.setup();
    renderApp("/sign-in", screens, {
      ...signedOut,
      "POST /api/v1/auth/sign-in": () => {
        throw new TypeError("Failed to fetch");
      },
    });
    await signInWith(user);
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Can't reach Kanakku",
    );
  });

  it("redirects a visitor who is already signed in", async () => {
    renderApp("/sign-in", screens, {
      "GET /api/v1/me": () => Response.json(aProfile),
    });
    expect(await screen.findByText("now at /me")).toBeTruthy();
  });
});
