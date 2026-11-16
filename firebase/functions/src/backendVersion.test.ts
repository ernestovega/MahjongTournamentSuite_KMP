import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import { backendVersion } from "./backendVersion";

test("the backend version is the one in the Gradle catalog", () => {
  const catalog = readFileSync(join(__dirname, "../../../gradle/libs.versions.toml"), "utf8");
  const expected = catalog.match(/^backend-versionName\s*=\s*"([^"]+)"/m)?.[1];
  assert.ok(expected);
  assert.equal(backendVersion(), expected);
});
