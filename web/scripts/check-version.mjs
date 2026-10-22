// AC-12: web version lives only in web/package.json. No dependencies.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
const SEMVER =
  /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

const EXCLUDED_DIRS = new Set(["node_modules", "dist", "coverage"]);
const EXCLUDED_FILES = new Set(["package.json", "package-lock.json"]);

function* walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      if (!EXCLUDED_DIRS.has(name)) yield* walk(path);
    } else if (!EXCLUDED_FILES.has(name)) {
      yield path;
    }
  }
}

export function checkVersion(dir, env = process.env) {
  const errors = [];
  const pkg = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"));
  const version = pkg.version;
  if (typeof version !== "string" || !SEMVER.test(version)) {
    return [`package.json version '${version}' is not valid semver`];
  }
  if (pkg.private !== true)
    errors.push('package.json must have "private": true');
  if (env.RELEASE !== "1" && !version.endsWith("-dev")) {
    errors.push(
      `package.json version '${version}' must end in -dev (set RELEASE=1 to release)`,
    );
  }

  const lock = JSON.parse(readFileSync(join(dir, "package-lock.json"), "utf8"));
  if (lock.version !== version || lock.packages?.[""]?.version !== version) {
    errors.push(
      `package-lock.json version does not equal package.json version '${version}'`,
    );
  }

  for (const file of walk(dir)) {
    let text;
    try {
      text = readFileSync(file, "utf8");
    } catch {
      continue;
    }
    if (text.includes(version)) {
      errors.push(
        `version literal '${version}' found in ${relative(dir, file)}`,
      );
    }
  }
  return errors;
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const dir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  const errors = checkVersion(dir);
  for (const e of errors) console.error(`FAIL: ${e}`);
  process.exit(errors.length === 0 ? 0 : 1);
}
