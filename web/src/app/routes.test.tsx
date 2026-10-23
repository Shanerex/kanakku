import { cleanup, screen, waitFor } from "@testing-library/react";
import { Outlet, type RouteObject, useLocation } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { aProfile, problem, renderApp } from "../test/harness";
import { RedirectIfSignedIn, RequireSession } from "./routes";

afterEach(cleanup);

function Where() {
  const location = useLocation();
  return <p>{`at ${location.pathname}${location.search}`}</p>;
}

const screens: RouteObject[] = [
  {
    element: <RedirectIfSignedIn />,
    children: [{ path: "/sign-in", element: <Where /> }],
  },
  {
    element: <RequireSession />,
    children: [
      { element: <Outlet />, children: [{ path: "/me", element: <Where /> }] },
    ],
  },
];

const signedOut = { "GET /api/v1/me": () => problem(401, "unauthenticated") };
const signedIn = { "GET /api/v1/me": () => Response.json(aProfile) };

describe("routes", () => {
  it("sends / to sign-in when there is no session", async () => {
    renderApp("/", screens, signedOut);
    expect(await screen.findByText("at /sign-in")).toBeTruthy();
  });

  it("sends / to the profile when signed in", async () => {
    renderApp("/", screens, signedIn);
    expect(await screen.findByText("at /me")).toBeTruthy();
  });

  it("sends a signed-out visitor to sign-in and remembers where they were going", async () => {
    renderApp("/me?tab=1", screens, signedOut);
    expect(
      await screen.findByText("at /sign-in?returnTo=%2Fme%3Ftab%3D1"),
    ).toBeTruthy();
  });

  it("keeps a signed-in visitor away from sign-in", async () => {
    renderApp("/sign-in", screens, signedIn);
    expect(await screen.findByText("at /me")).toBeTruthy();
  });

  it("shows words and a retry when the session check fails", async () => {
    renderApp("/me", screens, {
      "GET /api/v1/me": () => problem(500, "internal"),
    });
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/our side/);
    expect(screen.getByRole("button", { name: "Try again" })).toBeTruthy();
  });

  it("shows a friendly not-found page", async () => {
    renderApp("/nowhere", screens, signedOut);
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "This isn't available" }),
      ).toBeTruthy(),
    );
  });
});
