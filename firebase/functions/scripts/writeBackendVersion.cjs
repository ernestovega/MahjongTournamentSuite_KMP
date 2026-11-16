// Writes the versions from gradle/libs.versions.toml into lib/backendVersion.json: the backend version, which the
// deployed API answers on GET /health and shows on /version, and the web and Android versions on main when the
// backend was built, which /version shows as the latest ones. The build runs it after tsc. See docs/versioning.md.
const { mkdirSync, readFileSync, writeFileSync } = require("node:fs");
const { dirname, resolve } = require("node:path");

const catalog = resolve(__dirname, "../../../gradle/libs.versions.toml");
const target = resolve(__dirname, "../lib/backendVersion.json");

const text = readFileSync(catalog, "utf8");
function read(key) {
  const match = text.match(new RegExp(`^${key}\\s*=\\s*"([^"]+)"\\s*$`, "m"));
  if (!match) {
    throw new Error(`${key} is missing in ${catalog}`);
  }
  return match[1];
}
const versions = {
  version: read("backend-versionName"),
  web: read("web-versionName"),
  android: read("admin-versionName"),
  androidCode: read("admin-versionCode"),
};
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, `${JSON.stringify(versions)}\n`);
