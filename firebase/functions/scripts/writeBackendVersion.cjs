// Writes the backend version from gradle/libs.versions.toml into lib/backendVersion.json, so the deployed API can
// answer it on GET /health. The build runs it after tsc. See docs/versioning.md.
const { mkdirSync, readFileSync, writeFileSync } = require("node:fs");
const { dirname, resolve } = require("node:path");

const catalog = resolve(__dirname, "../../../gradle/libs.versions.toml");
const target = resolve(__dirname, "../lib/backendVersion.json");

const match = readFileSync(catalog, "utf8").match(/^backend-versionName\s*=\s*"([^"]+)"\s*$/m);
if (!match) {
  throw new Error(`backend-versionName is missing in ${catalog}`);
}
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, `${JSON.stringify({ version: match[1] })}\n`);
