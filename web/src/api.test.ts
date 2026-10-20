import { afterEach, describe, expect, it, vi } from "vitest";
import { getReadiness, loadConfig } from "./api";

function stubFetch(
  impl: (url: string, init?: RequestInit) => Promise<Response>,
) {
  const fn = vi.fn(impl);
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe("loadConfig", () => {
  it("fetches /config.json with no-store and returns apiBaseUrl", async () => {
    const fn = stubFetch(async () =>
      Response.json({ apiBaseUrl: "http://localhost:8080" }),
    );
    await expect(loadConfig()).resolves.toEqual({
      apiBaseUrl: "http://localhost:8080",
    });
    expect(fn).toHaveBeenCalledWith("/config.json", { cache: "no-store" });
  });

  it("rejects a missing apiBaseUrl", async () => {
    stubFetch(async () => Response.json({}));
    await expect(loadConfig()).rejects.toThrow(/apiBaseUrl/);
  });

  it("rejects an apiBaseUrl that is not a URL", async () => {
    stubFetch(async () => Response.json({ apiBaseUrl: "not a url" }));
    await expect(loadConfig()).rejects.toThrow(/apiBaseUrl/);
  });

  it("rejects a non-http scheme", async () => {
    stubFetch(async () => Response.json({ apiBaseUrl: "javascript:alert(1)" }));
    await expect(loadConfig()).rejects.toThrow(/apiBaseUrl/);
  });

  it("rejects a failed response", async () => {
    stubFetch(async () => new Response("nope", { status: 404 }));
    await expect(loadConfig()).rejects.toThrow(/config/);
  });
});

describe("getReadiness", () => {
  it("returns UP on 200", async () => {
    const fn = stubFetch(async () => Response.json({ status: "UP" }));
    await expect(
      getReadiness({ apiBaseUrl: "http://localhost:8080/" }),
    ).resolves.toBe("UP");
    expect(fn.mock.calls[0][0]).toBe(
      "http://localhost:8080/actuator/health/readiness",
    );
  });

  it("returns DOWN on 503", async () => {
    stubFetch(async () => Response.json({ status: "DOWN" }, { status: 503 }));
    await expect(
      getReadiness({ apiBaseUrl: "http://localhost:8080" }),
    ).resolves.toBe("DOWN");
  });

  it("throws when the API is unreachable", async () => {
    stubFetch(async () => {
      throw new TypeError("Failed to fetch");
    });
    await expect(
      getReadiness({ apiBaseUrl: "http://localhost:8080" }),
    ).rejects.toThrow();
  });
});
