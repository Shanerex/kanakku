import { defineConfig, devices } from "@playwright/test";

/**
 * Runs against the built app, not the dev server: `docker compose up -d --build`, then
 * `npm test` here. The web app is on :3000 and calls the API on :8080 (see docker-compose.yml).
 * Override with E2E_BASE_URL if the stack lives elsewhere.
 */
export default defineConfig({
  testDir: ".",
  testMatch: "*.spec.ts",
  fullyParallel: false,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
