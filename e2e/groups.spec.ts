import { type Browser, expect, type Page, test } from "@playwright/test";

async function signUp(page: Page, name: string, email: string, password: string) {
  await page.goto("/sign-up");
  await page.getByLabel("Display name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/welcome$/);
  await page.getByRole("button", { name: "Skip for now" }).click();
  await expect(page).toHaveURL(/\/me$/);
}

async function newSession(browser: Browser) {
  const context = await browser.newContext();
  return { context, page: await context.newPage() };
}

test("an Owner creates a group, adds a user and a guest, shares an invite and toggles who can add them", async ({
  browser,
}) => {
  const unique = `${Date.now()}${Math.floor(Math.random() * 1e6)}`;
  const letters = unique.replace(/\d/g, (d) => "abcdefghij"[Number(d)]);
  const password = `a long e2e passphrase ${unique}`;
  const meera = `Meera${letters}`;

  // Meera signs up first so the Owner can find her by name.
  const second = await newSession(browser);
  await signUp(second.page, meera, `meera-${unique}@example.com`, password);
  await second.context.close();

  const { context, page } = await newSession(browser);
  await signUp(page, "Asha Rao", `asha-${unique}@example.com`, password);

  // Create a group.
  await page.goto("/groups");
  await expect(page.getByText("No groups yet")).toBeVisible();
  await page.getByRole("button", { name: "New group" }).click();
  await page.getByLabel("Name").fill("Goa Trip");
  await page.getByRole("button", { name: "Create group" }).click();
  await expect(page).toHaveURL(/\/groups\/[0-9a-f-]{36}$/);
  await expect(page.getByRole("heading", { name: "Goa Trip" })).toBeVisible();

  // Rename it from the edit dialog.
  await page.getByRole("button", { name: "Edit group" }).click();
  const edit = page.getByRole("dialog", { name: "Edit group" });
  await edit.getByLabel("Name").fill("Goa 2027");
  await edit.getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByRole("heading", { name: "Goa 2027" })).toBeVisible();

  // Search for a registered user and add her directly.
  await page.getByLabel("Find people").fill(meera);
  await page.getByRole("button", { name: `Add ${meera}` }).click();
  const members = page.getByRole("region", { name: "Members" });
  await expect(members.getByText(meera)).toBeVisible();

  // Add a guest and share an invite link for them.
  await page.getByLabel("Guest name").fill("Karthik");
  await page.getByRole("button", { name: "Add guest" }).click();
  await expect(members.getByText("Karthik")).toBeVisible();
  await expect(members.getByText("Guest")).toBeVisible();
  await page.getByRole("button", { name: "Create invite link" }).click();
  await expect(page.getByLabel("Invite link", { exact: true })).toHaveValue(/\/invite#.+/);
  await expect(page.getByRole("list", { name: "Invites" })).toContainText(
    "Karthik",
  );

  // The member list shows everyone, with the Owner marked.
  await expect(members.getByText("Asha Rao (you)")).toBeVisible();
  await expect(members.getByText("Owner")).toBeVisible();
  await expect(page.getByText("3 people")).toBeVisible();

  // Turn off "who can add me directly" on the account and see it stick.
  await page.goto("/me/profile");
  const toggle = page.getByLabel("Let group Owners add me directly");
  await expect(toggle).toBeChecked();
  await toggle.uncheck();
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  await page.reload();
  await expect(page.getByLabel("Let group Owners add me directly")).not.toBeChecked();

  await context.close();
});
