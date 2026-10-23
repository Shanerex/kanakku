import { render } from "@testing-library/react";
import {
  createMemoryRouter,
  type RouteObject,
  RouterProvider,
} from "react-router";
import { createApiClient } from "../api/client";
import { buildRoutes } from "../app/routes";

export type Seen = {
  method: string;
  path: string;
  headers: Headers;
  body: unknown;
};

type Handler = (seen: Seen) => Response | Promise<Response>;

/**
 * Renders the app routes with a fake network. `handlers` is keyed "METHOD /path"; the CSRF token
 * endpoint answers by default. Returns every request the app made.
 */
export function renderApp(
  path: string,
  screens: RouteObject[],
  handlers: Record<string, Handler> = {},
) {
  const requests: Seen[] = [];
  const fetchStub = async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init);
    const url = new URL(request.url);
    let body: unknown;
    const text = request.method === "GET" ? "" : await request.clone().text();
    if (text) body = JSON.parse(text);
    const seen: Seen = {
      method: request.method,
      path: url.pathname,
      headers: request.headers,
      body,
    };
    requests.push(seen);
    const handler = handlers[`${request.method} ${url.pathname}`];
    if (handler) return handler(seen);
    if (seen.method === "GET" && seen.path === "/api/v1/auth/csrf")
      return Response.json({ headerName: "X-XSRF-TOKEN", token: "tok-1" });
    throw new Error(`unexpected request ${request.method} ${url.pathname}`);
  };
  const client = createApiClient({
    baseUrl: "http://api.test",
    fetch: fetchStub as unknown as typeof fetch,
  });
  const router = createMemoryRouter(buildRoutes(client, screens), {
    initialEntries: [path],
  });
  const view = render(<RouterProvider router={router} />);
  return { ...view, router, requests };
}

export function problem(
  status: number,
  slug: string,
  extra: Record<string, unknown> = {},
) {
  return new Response(
    JSON.stringify({
      type: `urn:kanakku:problem:${slug}`,
      title: "x",
      status,
      ...extra,
    }),
    { status, headers: { "content-type": "application/problem+json" } },
  );
}

export const aProfile = {
  id: "11111111-1111-4111-8111-111111111111",
  email: "asha@example.com",
  emailVerified: false,
  displayName: "Asha Rao",
  defaultCurrency: "INR",
  version: 0,
};
