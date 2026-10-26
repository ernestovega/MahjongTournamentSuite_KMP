// Tests for recovery.js of the web apps. Run with: node --test scripts/recovery.test.mjs
// The script runs in a vm with a small fake window and document, so no extra package is needed.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const PATHS = [
  new URL("../playerApp/src/wasmJsMain/resources/recovery.js", import.meta.url),
  new URL("../adminApp/src/webMain/resources/recovery.js", import.meta.url),
];

function load(path) {
  const listeners = { window: {}, document: {} };
  const adder = (target) => (type, handler) => ((listeners[target][type] ??= []).push(handler));
  const state = { reloads: 0, now: 1_000_000, appended: [] };
  const icon = { style: {} };
  const indicator = { style: {}, firstChild: icon, set innerHTML(_) {} };
  const document = {
    visibilityState: "visible",
    addEventListener: adder("document"),
    createElement: () => indicator,
    documentElement: { appendChild: (node) => state.appended.push(node) },
  };
  const window = {
    location: { reload: () => state.reloads++ },
    addEventListener: adder("window"),
  };
  const clock = { now: () => state.now };
  vm.runInNewContext(readFileSync(path, "utf8"), { window, document, Date: clock, Math, String });
  const fire = (target, type, event = {}) => {
    const full = { preventDefault() {}, touches: [], ...event };
    (listeners[target][type] ?? []).forEach((handler) => handler(full));
  };
  const hideTab = () => { document.visibilityState = "hidden"; fire("document", "visibilitychange"); };
  const showTab = () => { document.visibilityState = "visible"; fire("document", "visibilitychange"); };
  const touch = (y) => ({ touches: [{ clientY: y }] });
  return { state, indicator, icon, fire, hideTab, showTab, touch };
}

for (const path of PATHS) {
  const name = path.pathname.split("/").slice(-4).join("/");

  test(`${name}: reloads after a long time in the background`, () => {
    const page = load(path);
    page.hideTab();
    page.state.now += 31 * 60 * 1000;
    page.showTab();
    assert.equal(page.state.reloads, 1);
  });

  test(`${name}: does not reload after a short time in the background`, () => {
    const page = load(path);
    page.hideTab();
    page.state.now += 5 * 60 * 1000;
    page.showTab();
    assert.equal(page.state.reloads, 0);
  });

  test(`${name}: does not reload when the tab was never hidden`, () => {
    const page = load(path);
    page.showTab();
    assert.equal(page.state.reloads, 0);
  });

  test(`${name}: reloads when the page comes back from the browser cache`, () => {
    const page = load(path);
    page.fire("window", "pageshow", { persisted: false });
    assert.equal(page.state.reloads, 0);
    page.fire("window", "pageshow", { persisted: true });
    assert.equal(page.state.reloads, 1);
  });

  test(`${name}: reloads when the graphics context is lost`, () => {
    const page = load(path);
    let prevented = false;
    page.fire("document", "webglcontextlost", { preventDefault: () => (prevented = true) });
    assert.equal(page.state.reloads, 1);
    assert.ok(prevented);
  });

  test(`${name}: pull to refresh reloads after a long pull from the top`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(20));
    page.fire("window", "touchmove", page.touch(100));
    assert.equal(page.indicator.style.display, "flex");
    page.fire("window", "touchmove", page.touch(180));
    assert.equal(page.indicator.style.background, "#2e7d32");
    page.fire("window", "touchend");
    assert.equal(page.state.reloads, 1);
    assert.equal(page.indicator.style.display, "flex");
  });

  test(`${name}: pull to refresh ignores a short pull`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(20));
    page.fire("window", "touchmove", page.touch(100));
    page.fire("window", "touchend");
    assert.equal(page.state.reloads, 0);
    assert.equal(page.indicator.style.display, "none");
  });

  test(`${name}: pull to refresh ignores a gesture that starts below the top zone`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(300));
    page.fire("window", "touchmove", page.touch(600));
    page.fire("window", "touchend");
    assert.equal(page.state.reloads, 0);
  });

  test(`${name}: pull to refresh ignores a gesture with two fingers`, () => {
    const page = load(path);
    page.fire("window", "touchstart", { touches: [{ clientY: 20 }, { clientY: 30 }] });
    page.fire("window", "touchmove", page.touch(300));
    page.fire("window", "touchend");
    assert.equal(page.state.reloads, 0);
  });

  test(`${name}: pull to refresh stops when the touch is cancelled`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(20));
    page.fire("window", "touchmove", page.touch(200));
    page.fire("window", "touchcancel");
    page.fire("window", "touchend");
    assert.equal(page.state.reloads, 0);
  });

  test(`${name}: pull to refresh ignores an upward move`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(80));
    page.fire("window", "touchmove", page.touch(10));
    page.fire("window", "touchend");
    assert.equal(page.state.reloads, 0);
  });

  test(`${name}: the icon turns inside a fixed circle`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(20));
    page.fire("window", "touchmove", page.touch(100));
    assert.match(page.icon.style.transform, /^rotate\(/);
    assert.doesNotMatch(page.indicator.style.transform, /rotate/);
  });

  test(`${name}: the indicator turns green when the pull is long enough`, () => {
    const page = load(path);
    page.fire("window", "touchstart", page.touch(20));
    page.fire("window", "touchmove", page.touch(100));
    assert.equal(page.indicator.style.background, "#fff");
    page.fire("window", "touchmove", page.touch(180));
    assert.equal(page.indicator.style.background, "#2e7d32");
  });

  test(`${name}: adds the indicator to the page`, () => {
    const page = load(path);
    assert.equal(page.state.appended.length, 1);
  });
}

test("both copies of recovery.js are equal", () => {
  assert.equal(readFileSync(PATHS[0], "utf8"), readFileSync(PATHS[1], "utf8"));
});

test("both index.html files load recovery.js before the app", () => {
  for (const html of ["../playerApp/src/wasmJsMain/resources/index.html", "../adminApp/src/webMain/resources/index.html"]) {
    const text = readFileSync(new URL(html, import.meta.url), "utf8");
    const recovery = text.indexOf('src="recovery.js"');
    assert.ok(recovery > 0, html);
    assert.ok(recovery < text.search(/src="(playerApp|adminApp)\.js"/), html);
  }
});
