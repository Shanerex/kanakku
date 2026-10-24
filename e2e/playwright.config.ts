import { defineConfig, devices } from "@playwright/test";

/**
 * Runs against the built app, not the dev server: `make up-dev` (compose with
 * docker-compose.dev.yml), then `npm test` here. The web app is on :3000 and calls the API on
 * :8080. Override with E2E_BASE_URL if the stack lives elsewhere.
 * The stack serves plain HTTP, so docker-compose.dev.yml sets KANAKKU_AUTH_SECURE_COOKIES=false
 * (cookies are Secure by default); keep that if you start the app another way.
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
