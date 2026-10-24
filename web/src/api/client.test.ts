import { describe, expect, it, vi } from "vitest";
import { createApiClient, newIdempotencyKey } from "./client";

const BASE = "http://api.test";

type Seen = {
  url: string;
  method: string;
  headers: Headers;
  credentials: string;
};

function setup(handlers: Record<string, () => Response> = {}) {
  const seen: Seen[] = [];
  const fetchMock = vi.fn(
    async (input: RequestInfo | URL, init?: RequestInit) => {
      const request =
        input instanceof Request ? input : new Request(input, init);
      seen.push({
        url: request.url,
        method: request.method,
        headers: request.headers,
        credentials:
          input instanceof Request
            ? input.credentials
            : (init?.credentials ?? ""),
      });
      const key = `${request.method} ${new URL(request.url).pathname}`;
      const handler = handlers[key];
      if (handler) return handler();
      if (key === "GET /api/v1/auth/csrf")
        return Response.json({ headerName: "X-XSRF-TOKEN", token: "tok-1" });
      return Response.json({});
    },
  );
  const client = createApiClient({
    baseUrl: BASE,
    fetch: fetchMock as unknown as typeof fetch,
  });
  return { client, seen, fetchMock };
}

describe("createApiClient", () => {
  it("sends the session cookie but no CSRF header on a read", async () => {
    const { client, seen } = setup();
    await client.GET("/api/v1/me");
    expect(seen).toHaveLength(1);
    expect(seen[0].url).toBe(`${BASE}/api/v1/me`);
    expect(seen[0].credentials).toBe("include");
    expect(seen[0].headers.has("X-XSRF-TOKEN")).toBe(false);
  });

  it("fetches the CSRF token before the first write and sends it as the header", async () => {
    const { client, seen } = setup();
    await client.PUT("/api/v1/me", {
      body: { displayName: "A", defaultCurrency: "INR", version: 0 },
    });
    expect(seen.map((s) => `${s.method} ${new URL(s.url).pathname}`)).toEqual([
      "GET /api/v1/auth/csrf",
      "PUT /api/v1/me",
    ]);
    expect(seen[0].credentials).toBe("include");
    expect(seen[1].headers.get("X-XSRF-TOKEN")).toBe("tok-1");
    expect(seen[1].credentials).toBe("include");
  });

  it("reuses the token for later writes", async () => {
    const { client, seen } = setup();
    await client.POST("/api/v1/auth/sign-out");
    await client.POST("/api/v1/auth/sign-out");
    expect(seen.filter((s) => s.url.endsWith("/auth/csrf"))).toHaveLength(1);
    expect(seen.filter((s) => s.method === "POST")).toHaveLength(2);
  });

  it("shares one token request between concurrent writes", async () => {
    const { client, seen } = setup();
    await Promise.all([
      client.POST("/api/v1/auth/sign-out"),
      client.POST("/api/v1/auth/sign-out"),
    ]);
    expect(seen.filter((s) => s.url.endsWith("/auth/csrf"))).toHaveLength(1);
  });

  it("drops the token after a CSRF refusal so the next write fetches a new one", async () => {
    let calls = 0;
    const { client, seen } = setup({
      "POST /api/v1/auth/sign-out": () => {
        calls += 1;
        return calls === 1
          ? new Response(
              JSON.stringify({
                type: "urn:kanakku:problem:csrf-token-invalid",
                status: 403,
                title: "x",
              }),
              {
                status: 403,
                headers: { "content-type": "application/problem+json" },
              },
            )
          : new Response(null, { status: 204 });
      },
    });
    const first = await client.POST("/api/v1/auth/sign-out");
    expect(first.response.status).toBe(403);
    await client.POST("/api/v1/auth/sign-out");
    expect(seen.filter((s) => s.url.endsWith("/auth/csrf"))).toHaveLength(2);
  });

  it("drops the token after a successful sign-in, which rotates it on the server", async () => {
    const { client, seen } = setup({
      "POST /api/v1/auth/sign-in": () => Response.json({ id: "u" }),
    });
    await client.POST("/api/v1/auth/sign-in", {
      body: { email: "a@b.co", password: "x" },
    });
    await client.POST("/api/v1/auth/sign-out");
    expect(seen.filter((s) => s.url.endsWith("/auth/csrf"))).toHaveLength(2);
  });

  it("keeps the token after an unrelated 403", async () => {
    const { client, seen } = setup({
      "POST /api/v1/auth/sign-out": () =>
        new Response(
          JSON.stringify({
            type: "urn:kanakku:problem:forbidden",
            status: 403,
            title: "x",
          }),
          {
            status: 403,
            headers: { "content-type": "application/problem+json" },
          },
        ),
    });
    await client.POST("/api/v1/auth/sign-out");
    await client.POST("/api/v1/auth/sign-out");
    expect(seen.filter((s) => s.url.endsWith("/auth/csrf"))).toHaveLength(1);
  });

  it("surfaces a failed token fetch and retries it next time", async () => {
    let csrfCalls = 0;
    const { client } = setup({
      "GET /api/v1/auth/csrf": () => {
        csrfCalls += 1;
        return csrfCalls === 1
          ? new Response("nope", { status: 500 })
          : Response.json({ headerName: "X-XSRF-TOKEN", token: "tok-2" });
      },
    });
    await expect(client.POST("/api/v1/auth/sign-out")).rejects.toThrow();
    await client.POST("/api/v1/auth/sign-out");
    expect(csrfCalls).toBe(2);
  });
});

describe("newIdempotencyKey", () => {
  it("returns a different UUID each time", () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).toMatch(/^[0-9a-f-]{36}$/);
    expect(a).not.toBe(b);
  });
});
