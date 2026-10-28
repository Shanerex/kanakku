import { defineConfig, devices } from "@playwright/test";

/**
 * Screenshots for design review (`npm run screens`). The app runs on the Vite dev server with the
 * API stubbed in the test, so no stack is needed. Output: specs/<feature>/screenshots/.
 */
export default defineConfig({
  testDir: ".",
  testMatch: "*.shots.ts",
  fullyParallel: false,
  retries: 0,
  reporter: [["list"]],
  use: { baseURL: "http://localhost:5173" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm --prefix ../web run dev -- --port 5173 --strictPort",
    url: "http://localhost:5173",
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
