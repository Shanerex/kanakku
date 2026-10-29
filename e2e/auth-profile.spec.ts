import { expect, test } from "@playwright/test";

test("sign up, sign out, sign in, edit the profile, reload and see the values", async ({
  page,
}) => {
  const unique = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const email = `e2e-${unique}@example.com`;
  const password = `a long e2e passphrase ${unique}`;

  // Sign up. The account is created, the visitor is signed in and lands on the welcome step.
  await page.goto("/sign-up");
  await page.getByLabel("Display name").fill("Asha Rao");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/welcome$/);
  await expect(page.getByLabel("Default currency")).toHaveValue("INR");
  await page.getByRole("button", { name: "Skip for now" }).click();
  await expect(page).toHaveURL(/\/me$/);
  await expect(page.getByRole("heading", { name: "Asha Rao" })).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();

  // Sign out.
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/sign-in$/);

  // A wrong password gives the one generic message.
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill("not the right password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("alert")).toHaveText(
    "That email and password don't match.",
  );

  // Sign in with the right one.
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/me$/);
  await expect(page.getByRole("heading", { name: "Asha Rao" })).toBeVisible();

  // Edit the profile.
  await page.getByRole("link", { name: "Edit profile" }).click();
  await expect(page).toHaveURL(/\/me\/profile$/);
  await page.getByLabel("Display name").fill("Asha K Rao");
  await page.getByLabel("Default currency").selectOption("USD");
  await page.getByLabel("UPI ID (optional)").fill("asha.rao@okbank");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");

  // Reload: the values come back from the server.
  await page.reload();
  await expect(page.getByLabel("Display name")).toHaveValue("Asha K Rao");
  await expect(page.getByLabel("Default currency")).toHaveValue("USD");
  await expect(page.getByLabel("UPI ID (optional)")).toHaveValue("asha.rao@okbank");
  await expect(page.getByLabel("Email")).toHaveValue(email);
});

test("a visitor who is not signed in is sent to sign-in and returns after", async ({
  page,
}) => {
  await page.goto("/me/profile");
  await expect(page).toHaveURL(/\/sign-in\?returnTo=%2Fme%2Fprofile$/);
});
