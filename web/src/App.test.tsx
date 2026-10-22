import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

type Route = Response | Error;

function stubRoutes(routes: Record<string, Route>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const route = routes[url];
      if (route === undefined) throw new Error(`unexpected fetch ${url}`);
      if (route instanceof Error) throw route;
      return route;
    }),
  );
}

const config = () => Response.json({ apiBaseUrl: "http://api.test" });

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("App", () => {
  it("shows a config error when config.json cannot be loaded", async () => {
    stubRoutes({ "/config.json": new Response("", { status: 404 }) });
    render(<App />);
    expect(await screen.findByRole("alert")).toHaveProperty(
      "textContent",
      expect.stringContaining("configuration"),
    );
  });

  it("shows the API as up", async () => {
    stubRoutes({
      "/config.json": config(),
      "http://api.test/actuator/health/readiness": Response.json({
        status: "UP",
      }),
    });
    render(<App />);
    expect(await screen.findByText("API status: UP")).toBeTruthy();
  });

  it("shows the API as down", async () => {
    stubRoutes({
      "/config.json": config(),
      "http://api.test/actuator/health/readiness": Response.json(
        { status: "DOWN" },
        { status: 503 },
      ),
    });
    render(<App />);
    expect(await screen.findByText("API status: DOWN")).toBeTruthy();
  });

  it("shows the API as unreachable", async () => {
    stubRoutes({
      "/config.json": config(),
      "http://api.test/actuator/health/readiness": new TypeError(
        "Failed to fetch",
      ),
    });
    render(<App />);
    expect(await screen.findByText("API status: unreachable")).toBeTruthy();
  });
});
