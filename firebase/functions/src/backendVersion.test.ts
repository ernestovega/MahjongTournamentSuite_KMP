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

test("the version is unknown when the build wrote no version file", (t) => {
  // The module object itself, which the code under test calls.
  const fs = require("node:fs") as typeof import("node:fs");
  const real = fs.readFileSync;
  let written: string | null = null;
  t.mock.method(fs, "readFileSync", (...args: Parameters<typeof fs.readFileSync>) => {
    if (String(args[0]).endsWith("backendVersion.json")) {
      if (written == null) throw new Error("ENOENT");
      return written;
    }
    return real(...args);
  });
  // A fresh copy of the module: the loaded one keeps the version it read.
  const path = require.resolve("./backendVersion");
  const loaded = require.cache[path];
  delete require.cache[path];
  try {
    const fresh = require("./backendVersion") as typeof import("./backendVersion");
    assert.equal(fresh.backendVersion(), "unknown");
    assert.equal(fresh.backendVersion(), "unknown", "the answer is kept");
    // A file without the backend version, with the other versions.
    written = JSON.stringify({ web: "1.0.1" });
    delete require.cache[path];
    const partial = require("./backendVersion") as typeof import("./backendVersion");
    assert.deepEqual(partial.builtVersions(), { web: "1.0.1", version: "unknown" });
  } finally {
    if (loaded) require.cache[path] = loaded;
  }
});
