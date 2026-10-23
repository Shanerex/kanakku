export type Config = { apiBaseUrl: string };
export type Readiness = "UP" | "DOWN";

function isHttpUrl(value: unknown): value is string {
  if (typeof value !== "string") return false;
  try {
    const { protocol } = new URL(value);
    return protocol === "http:" || protocol === "https:";
  } catch {
    return false;
  }
}

export async function loadConfig(): Promise<Config> {
  const response = await fetch("/config.json", { cache: "no-store" });
  if (!response.ok)
    throw new Error(`Could not load config (HTTP ${response.status})`);
  const body: unknown = await response.json();
  const apiBaseUrl = (body as { apiBaseUrl?: unknown } | null)?.apiBaseUrl;
  if (!isHttpUrl(apiBaseUrl))
    throw new Error("config.json: apiBaseUrl must be an http(s) URL");
  return { apiBaseUrl };
}

export async function getReadiness(config: Config): Promise<Readiness> {
  const base = config.apiBaseUrl.replace(/\/+$/, "");
  const response = await fetch(`${base}/actuator/health/readiness`);
  return response.ok ? "UP" : "DOWN";
}
