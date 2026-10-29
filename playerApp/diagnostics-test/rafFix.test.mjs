// Tests of raf-fix.js, the workaround for the blank page in Safari 27.
// Run: node --test playerApp/diagnostics-test/rafFix.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const resources = join(dirname(fileURLToPath(import.meta.url)), "../src/wasmJsMain/resources");
const code = readFileSync(join(resources, "raf-fix.js"), "utf8");

// Run the file with a fake window. The fake browser keeps the functions it gets.
function load() {
  const queued = [];
  const fakeWindow = { requestAnimationFrame: (callback) => (queued.push(callback), 41 + queued.length) };
  new Function("window", code)(fakeWindow);
  return { fakeWindow, queued };
}

test("the browser never gets the function of the app", () => {
  const { fakeWindow, queued } = load();
  const callback = () => {};
  fakeWindow.requestAnimationFrame(callback);
  assert.equal(queued.length, 1);
  assert.notEqual(queued[0], callback);
});

test("the function of the app gets the time and runs once", () => {
  const { fakeWindow, queued } = load();
  const times = [];
  fakeWindow.requestAnimationFrame((time) => times.push(time));
  queued[0](123.5);
  assert.deepEqual(times, [123.5]);
});

test("the id of the browser stays, so the app can cancel the frame", () => {
  const { fakeWindow } = load();
  assert.equal(fakeWindow.requestAnimationFrame(() => {}), 42);
  assert.equal(fakeWindow.requestAnimationFrame(() => {}), 43);
});

const pages = {
  player: [join(resources, "index.html"), "playerApp.js", join(resources, "raf-fix.js")],
  admin: [join(resources, "../../../../adminApp/src/webMain/resources/index.html"), "adminApp.js",
    join(resources, "../../../../adminApp/src/webMain/resources/raf-fix.js")],
};

for (const [name, [page, app, fixFile]] of Object.entries(pages)) {
  test(`the ${name} index.html loads the fix before the app`, () => {
    const html = readFileSync(page, "utf8");
    const fix = html.indexOf('src="raf-fix.js"');
    assert.ok(fix >= 0, "index.html does not load raf-fix.js");
    assert.ok(fix < html.indexOf(`src="${app}"`), "raf-fix.js must load before " + app);
    assert.equal(readFileSync(fixFile, "utf8"), code, "raf-fix.js differs from the player copy");
  });
}
