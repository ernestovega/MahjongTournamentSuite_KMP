import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import test from "node:test";

/**
 * `npm test` names each unit test file. A new test file that is not in that list never runs, here or in CI. The
 * integration tests (*.integration.test.ts) run by folder with `npm run test:integration`.
 */
function testFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return testFiles(path);
    return name.endsWith(".test.ts") && !name.endsWith(".integration.test.ts") ? [path] : [];
  });
}

test("npm test runs every unit test file", () => {
  const root = join(__dirname, "..");
  const script: string = JSON.parse(readFileSync(join(root, "package.json"), "utf8")).scripts.test;
  const listed = new Set(script.split(/\s+/).filter((part) => part.endsWith(".test.js")));
  const missing = testFiles(join(root, "src"))
    .map((file) => "lib/" + relative(join(root, "src"), file).split("\\").join("/").replace(/\.ts$/, ".js"))
    .filter((file) => !listed.has(file));
  assert.deepEqual(missing, [], "add these files to the script test of package.json");
});

test("every integration test is in a folder that npm run test:integration reads", () => {
  const root = join(__dirname, "..");
  const outside = integrationFiles(join(root, "src"))
    .map((file) => relative(join(root, "src"), file).split("\\").join("/"))
    .filter((file) => !/^(services|api)\/[^/]+\.integration\.test\.ts$/.test(file));
  assert.deepEqual(outside, []);
});

function integrationFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return integrationFiles(path);
    return name.endsWith(".integration.test.ts") ? [path] : [];
  });
}
