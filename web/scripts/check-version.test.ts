import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
// @ts-expect-error plain ESM script without types
import { checkVersion } from "./check-version.mjs";

const dirs: string[] = [];
afterEach(() => {
  for (const d of dirs.splice(0)) rmSync(d, { recursive: true, force: true });
});

type Opts = {
  version?: string;
  priv?: boolean;
  lockVersion?: string;
  lockRootVersion?: string;
};

function project(opts: Opts = {}): string {
  const version = opts.version ?? "1.2.3-dev";
  const dir = mkdtempSync(join(tmpdir(), "kanakku-web-"));
  dirs.push(dir);
  writeFileSync(
    join(dir, "package.json"),
    JSON.stringify({ name: "x", version, private: opts.priv ?? true }),
  );
  const lockVersion = opts.lockVersion ?? version;
  writeFileSync(
    join(dir, "package-lock.json"),
    JSON.stringify({
      name: "x",
      version: lockVersion,
      packages: {
        "": { name: "x", version: opts.lockRootVersion ?? lockVersion },
      },
    }),
  );
  mkdirSync(join(dir, "src"));
  writeFileSync(join(dir, "src", "a.ts"), "export const a = 1;\n");
  return dir;
}

describe("checkVersion", () => {
  it("accepts a valid development project", () => {
    expect(checkVersion(project(), {})).toEqual([]);
  });

  it("rejects a version that is not semver", () => {
    expect(checkVersion(project({ version: "1.2-dev" }), {}).join()).toMatch(
      /semver/,
    );
  });

  it("rejects a missing -dev suffix in development", () => {
    expect(checkVersion(project({ version: "1.2.3" }), {}).join()).toMatch(
      /-dev/,
    );
  });

  it("allows no suffix when RELEASE=1", () => {
    expect(
      checkVersion(project({ version: "1.2.3" }), { RELEASE: "1" }),
    ).toEqual([]);
  });

  it("rejects a package that is not private", () => {
    expect(checkVersion(project({ priv: false }), {}).join()).toMatch(
      /private/,
    );
  });

  it("rejects a lockfile version mismatch", () => {
    expect(
      checkVersion(project({ lockVersion: "9.9.9-dev" }), {}).join(),
    ).toMatch(/lock/);
    expect(
      checkVersion(project({ lockRootVersion: "9.9.9-dev" }), {}).join(),
    ).toMatch(/lock/);
  });

  it("rejects the version literal in a hand-edited file", () => {
    const dir = project();
    writeFileSync(join(dir, "src", "a.ts"), 'export const v = "1.2.3-dev";\n');
    expect(checkVersion(dir, {}).join()).toMatch(/src[\\/]a\.ts/);
  });

  it("ignores the literal in excluded files and directories", () => {
    const dir = project();
    for (const d of ["node_modules", "dist", "coverage"]) {
      mkdirSync(join(dir, d));
      writeFileSync(join(dir, d, "f.js"), "1.2.3-dev");
    }
    expect(checkVersion(dir, {})).toEqual([]);
  });
});
