import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";

export type Profile = components["schemas"]["Profile"];
export type Problem = components["schemas"]["Problem"];
export type { paths };

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);
const CSRF_PROBLEM = "urn:kanakku:problem:csrf-token-invalid";

type CsrfToken = { headerName: string; token: string };

export type CsrfMiddleware = Middleware & { reset(): void };

/**
 * Adds the CSRF header to every unsafe request. The token is fetched once from
 * `/api/v1/auth/csrf` (which also sets the cookie), reused, and dropped when the server says it
 * is no longer valid, so the next write fetches a fresh one.
 */
export function csrfMiddleware(options: {
  baseUrl: string;
  fetch: typeof fetch;
}): CsrfMiddleware {
  const base = options.baseUrl.replace(/\/+$/, "");
  let pending: Promise<CsrfToken> | undefined;

  function load(): Promise<CsrfToken> {
    if (!pending) {
      pending = options
        .fetch(`${base}/api/v1/auth/csrf`, { credentials: "include" })
        .then(async (response) => {
          if (!response.ok) throw new Error(`csrf: HTTP ${response.status}`);
          return (await response.json()) as CsrfToken;
        })
        .catch((error: unknown) => {
          pending = undefined;
          throw error;
        });
    }
    return pending;
  }

  return {
    reset() {
      pending = undefined;
    },
    async onRequest({ request }) {
      if (!UNSAFE_METHODS.has(request.method.toUpperCase())) return undefined;
      const { headerName, token } = await load();
      request.headers.set(headerName, token);
      return request;
    },
    async onResponse({ request, response }) {
      // Sign-in rotates the CSRF token on the server, so the cached one is stale from here on.
      if (
        response.ok &&
        request.method.toUpperCase() === "POST" &&
        new URL(request.url).pathname.endsWith("/auth/sign-in")
      ) {
        this.reset();
      }
      if (
        response.status === 403 &&
        response.headers.get("content-type")?.includes("problem+json")
      ) {
        const problem = (await response.clone().json()) as Partial<Problem>;
        if (problem.type === CSRF_PROBLEM) this.reset();
      }
      return undefined;
    },
  };
}

/**
 * The typed API client. Every request carries the session cookie; unsafe ones carry the CSRF
 * header. This is the only way the web app calls `/api/v1`.
 */
export function createApiClient(options: {
  baseUrl: string;
  fetch?: typeof fetch;
}) {
  const fetchImpl =
    options.fetch ?? ((...args: Parameters<typeof fetch>) => fetch(...args));
  const client = createClient<paths>({
    baseUrl: options.baseUrl,
    credentials: "include",
    fetch: fetchImpl,
  });
  client.use(csrfMiddleware({ baseUrl: options.baseUrl, fetch: fetchImpl }));
  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;

/** A fresh `Idempotency-Key`. Make one per submit attempt and reuse it only for a retry. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
