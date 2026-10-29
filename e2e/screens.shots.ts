import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { expect, type Page, test } from "@playwright/test";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "specs/2026-10-27-ui-design-retrofit/screenshots");
const MOCKS = join(ROOT, "docs/design/mocks");
mkdirSync(OUT, { recursive: true });

const WIDTHS = [360, 1280] as const;

const ME = "11111111-1111-4111-8111-111111111111";
const profile = {
  id: ME,
  email: "priya@example.com",
  emailVerified: false,
  displayName: "Priya Raman",
  defaultCurrency: "INR",
  upiId: "priya@okbank",
  directAdd: "ALLOW",
  phoneVerified: false,
  version: 0,
};
const group = (id: string, name: string, cover: string, extra = {}) => ({
  id: `22222222-2222-4222-8222-22222222222${id}`,
  name,
  type: "TRIP",
  currency: "INR",
  cover,
  version: 0,
  myRole: "OWNER",
  ...extra,
});
const groups = [
  group("1", "Goa Trip", "emoji:🏖"),
  group("2", "Flat 4B", "color:mango", { type: "HOME" }),
  group("3", "Meera's 30th", "color:pink", { myRole: "MEMBER" }),
  group("4", "Badminton", "emoji:🎉", { myRole: "MEMBER" }),
  group("5", "Diwali Trip", "color:teal", { archivedAt: "2026-10-01T00:00:00Z" }),
];
const member = (id: string, name: string, extra = {}) => ({
  id: `33333333-3333-4333-8333-33333333333${id}`,
  userId: null,
  displayName: name,
  role: "MEMBER",
  guest: false,
  joinedAt: "2026-10-12T10:00:00Z",
  balances: [],
  version: 0,
  ...extra,
});
const members = [
  member("1", "Priya Raman", {
    userId: ME,
    role: "OWNER",
    balances: [{ currency: "INR", amountMinor: 234000 }],
  }),
  member("2", "Arun", {
    userId: "44444444-4444-4444-8444-444444444444",
    balances: [{ currency: "INR", amountMinor: -124000 }],
  }),
  member("3", "Karthik", {
    guest: true,
    balances: [{ currency: "INR", amountMinor: -110000 }],
  }),
];
const suggestion = {
  id: "55555555-5555-4555-8555-555555555555",
  groupId: groups[0].id,
  groupName: "Goa Trip",
  guestMemberId: members[2].id,
  guestName: "Karthik",
  status: "PENDING",
  version: 0,
  createdAt: "2026-10-20T10:00:00Z",
};

type World = { signedIn: boolean; groups?: unknown[]; signInFails?: boolean };

/** Answers every /api/v1 call from the fixtures above; nothing leaves the machine. */
async function stubApi(page: Page, world: World) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const key = `${route.request().method()} ${url.pathname}`;
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: status >= 400 ? "application/problem+json" : "application/json",
        body: JSON.stringify(body),
      });
    if (key === "GET /api/v1/me")
      return world.signedIn
        ? json(profile)
        : json({ type: "urn:kanakku:problem:unauthenticated", title: "x", status: 401 }, 401);
    if (key === "GET /api/v1/auth/csrf")
      return json({ headerName: "X-XSRF-TOKEN", token: "t" });
    if (key === "POST /api/v1/auth/sign-in")
      return json({ type: "urn:kanakku:problem:invalid-credentials", title: "x", status: 401 }, 401);
    if (key === "GET /api/v1/auth/sign-up-options")
      return json({ phoneVerification: false });
    if (key === "GET /api/v1/groups") return json(world.groups ?? groups);
    if (key === "GET /api/v1/claim-suggestions")
      return json(world.groups?.length === 0 ? [] : [suggestion]);
    if (key === `GET /api/v1/groups/${groups[0].id}`) return json(groups[0]);
    if (key === `GET /api/v1/groups/${groups[0].id}/members`) return json(members);
    if (key === `GET /api/v1/groups/${groups[0].id}/invites`) return json([]);
    return json({ type: "urn:kanakku:problem:not-found", title: "x", status: 404 }, 404);
  });
}

/**
 * Grows the viewport to the page's height and takes a plain viewport shot. A full-page capture
 * would leave the fixed bottom bar and the sticky sidebar mid-page.
 */
async function shoot(page: Page, width: number, path: string) {
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width, height: Math.max(800, height) });
  await page.waitForTimeout(150);
  await page.screenshot({ path });
}

async function noSideScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(overflow, "no horizontal scroll").toBeLessThanOrEqual(0);
}

/** The shell: sidebar from 768 px, bottom bar below it. */
async function expectShell(page: Page, width: number, world: World) {
  const side = page.getByRole("navigation", { name: "Main", exact: true });
  const bottom = page.getByRole("navigation", { name: "Main tabs", exact: true });
  if (width >= 768) {
    await expect(side).toBeVisible();
    await expect(bottom).toBeHidden();
    if (world.signedIn && world.groups?.length !== 0) {
      const list = side.getByRole("list", { name: "Active groups" });
      await expect(list.getByRole("link")).toHaveCount(4);
    }
  } else {
    await expect(side).toBeHidden();
    await expect(bottom).toBeVisible();
  }
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  const main = await page.getByRole("main").boundingBox();
  expect(main?.width ?? 0).toBeLessThanOrEqual(960);
}

type Shot = {
  name: string;
  path: string;
  world: World;
  /** The design mock this screen is compared against, when one exists. */
  mock?: string;
  shell: boolean;
  ready: (page: Page) => Promise<void>;
  after?: (page: Page) => Promise<void>;
};

const shots: Shot[] = [
  {
    name: "auth-sign-in",
    path: "/sign-in",
    world: { signedIn: false },
    mock: "web-sign-in.html",
    shell: false,
    ready: (p) => p.getByRole("heading", { name: "Welcome back" }).waitFor(),
    after: async (p) => {
      await p.getByLabel("Email").fill("priya@example.com");
      await p.getByLabel("Password", { exact: true }).fill("hunter2hunter2");
      await p.getByRole("button", { name: "Sign in" }).click();
      await p.getByRole("alert").waitFor();
    },
  },
  {
    name: "auth-sign-up",
    path: "/sign-up",
    world: { signedIn: false },
    shell: false,
    ready: (p) => p.getByRole("heading", { name: "Create your account" }).waitFor(),
  },
  {
    name: "auth-welcome",
    path: "/welcome",
    world: { signedIn: true },
    shell: false,
    ready: (p) => p.getByRole("heading", { name: "Welcome to Kanakku" }).waitFor(),
  },
  {
    name: "me",
    path: "/me",
    world: { signedIn: true },
    shell: true,
    ready: (p) => p.getByRole("heading", { name: "Priya Raman" }).waitFor(),
  },
  {
    name: "me-profile",
    path: "/me/profile",
    world: { signedIn: true },
    shell: true,
    ready: (p) => p.getByRole("heading", { name: "Mobile number" }).waitFor(),
  },
  {
    name: "groups-list",
    path: "/groups",
    world: { signedIn: true },
    mock: "web-home.html",
    shell: true,
    ready: (p) => p.getByRole("list", { name: "Your groups" }).waitFor(),
  },
  {
    name: "groups-empty",
    path: "/groups",
    world: { signedIn: true, groups: [] },
    shell: true,
    ready: (p) => p.getByRole("heading", { name: "Start your first group" }).waitFor(),
  },
  {
    name: "groups-new",
    path: "/groups/new",
    world: { signedIn: true },
    shell: true,
    ready: (p) => p.getByRole("form", { name: "New group" }).waitFor(),
  },
  {
    name: "groups-detail",
    path: `/groups/${groups[0].id}`,
    world: { signedIn: true },
    mock: "web-group.html",
    shell: true,
    ready: (p) => p.getByRole("region", { name: "Members" }).waitFor(),
  },
];

for (const shot of shots) {
  for (const width of WIDTHS) {
    test(`${shot.name} at ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await stubApi(page, shot.world);
      await page.goto(shot.path);
      await shot.ready(page);
      await shot.after?.(page);
      await page.evaluate(() => document.fonts.ready);
      if (shot.shell) await expectShell(page, width, shot.world);
      await noSideScroll(page);
      await shoot(page, width, join(OUT, `${shot.name}-${width}.png`));
    });

    if (shot.mock) {
      test(`${shot.name} mock at ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 800 });
        await page.goto(pathToFileURL(join(MOCKS, shot.mock as string)).href, {
          waitUntil: "load",
        });
        await page.evaluate(() => document.fonts.ready);
        await shoot(page, width, join(OUT, `${shot.name}-mock-${width}.png`));
      });
    }
  }
}
