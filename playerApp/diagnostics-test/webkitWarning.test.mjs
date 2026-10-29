// Tests of webkit-warning.js, the warning for the WebAssembly bug of WebKit 27.2.
// Run: node --test playerApp/diagnostics-test/webkitWarning.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const playerDir = join(here, "../src/wasmJsMain/resources");
const adminDir = join(here, "../../adminApp/src/webMain/resources");
const code = readFileSync(join(playerDir, "webkit-warning.js"), "utf8");

const SAFARI_MAC = (version) => `Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${version} Safari/605.1.15`;
const SAFARI_IPHONE = (version) => `Mozilla/5.0 (iPhone; CPU iPhone OS ${version.replace(".", "_")} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${version} Mobile/15E148 Safari/604.1`;
const CHROME_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 27_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/140.0.0.0 Mobile/15E148 Safari/604.1";
const CHROME_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const FIREFOX = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:140.0) Gecko/20100101 Firefox/140.0";

// Run the file with a fake page. It returns the bars that the file adds.
function load(userAgent, storage = new Map()) {
  const added = [];
  const element = () => ({
    style: {}, children: [], listeners: {},
    setAttribute() {}, append(...nodes) { this.children.push(...nodes); },
    addEventListener(name, fn) { this.listeners[name] = fn; },
    remove() { this.removed = true; },
  });
  const document = { createElement: element, documentElement: { appendChild: (node) => added.push(node) } };
  const sessionStorage = { getItem: (k) => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v) };
  new Function("navigator", "document", "sessionStorage", code)({ userAgent }, document, sessionStorage);
  return { added, storage };
}

test("Safari 27.2 on a Mac shows the warning", () => {
  assert.equal(load(SAFARI_MAC("27.2")).added.length, 1);
});

test("Safari 27.2.1 on a Mac shows the warning", () => {
  assert.equal(load(SAFARI_MAC("27.2.1")).added.length, 1);
});

test("Safari 27.2 and Chrome on an iPhone with iOS 27.2 show the warning", () => {
  assert.equal(load(SAFARI_IPHONE("27.2")).added.length, 1);
  assert.equal(load(CHROME_IPHONE).added.length, 1);
});

test("other versions and other engines show nothing", () => {
  for (const agent of [SAFARI_MAC("27.1"), SAFARI_MAC("27.3"), SAFARI_MAC("127.2"), SAFARI_IPHONE("27.3"), CHROME_MAC, FIREFOX]) {
    assert.equal(load(agent).added.length, 0, agent);
  }
});

test("the warning explains how to turn off the JIT, with the steps of the device", () => {
  const mac = load(SAFARI_MAC("27.2")).added[0].children[0].children[1].textContent;
  assert.match(mac, /Disables JIT/);
  assert.match(mac, /Develop > WebKit Feature Flags/);
  assert.match(mac, /Cmd\+Q/);
  const phone = load(SAFARI_IPHONE("27.2")).added[0].children[0].children[1].textContent;
  assert.match(phone, /Disables JIT/);
  assert.match(phone, /Settings > Apps > Safari > Advanced > Feature Flags/);
  assert.doesNotMatch(phone, /Cmd\+Q/);
});

test("the close button removes the warning and it stays closed in the tab", () => {
  const { added, storage } = load(SAFARI_MAC("27.2"));
  const button = added[0].children[1];
  button.listeners.click();
  assert.equal(added[0].removed, true);
  assert.equal(load(SAFARI_MAC("27.2"), storage).added.length, 0);
});

for (const [name, dir, app] of [["player", playerDir, "playerApp.js"], ["admin", adminDir, "adminApp.js"]]) {
  test(`the ${name} index.html loads the warning before the app`, () => {
    const html = readFileSync(join(dir, "index.html"), "utf8");
    const warning = html.indexOf('src="webkit-warning.js"');
    assert.ok(warning >= 0, "index.html does not load webkit-warning.js");
    assert.ok(warning < html.indexOf(`src="${app}"`), "webkit-warning.js must load before " + app);
    assert.equal(readFileSync(join(dir, "webkit-warning.js"), "utf8"), code, "webkit-warning.js differs from the player copy");
  });
}
