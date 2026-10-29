// Tests of the start diagnostics (diagnostics.js) in a real Chrome without a window.
// Run: node --test playerApp/diagnostics-test/diagnostics.test.mjs
// Set CHROME_PATH when Chrome is not in the default place. Set DIAGNOSTICS_JS to test another copy of the file.
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const diagnostics = resolve(process.env.DIAGNOSTICS_JS ?? join(here, "../src/wasmJsMain/resources/diagnostics.js"));
const chrome = process.env.CHROME_PATH ?? [
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
].find(existsSync);

const PANEL_TITLE = "The app did not start";

// Load a page with diagnostics.js and the given extra script. Return the DOM after 40 seconds of virtual time.
function run(extraScript, query = "", beforeScript = "") {
  const dir = mkdtempSync(join(tmpdir(), "mts-diagnostics-"));
  try {
    const page = join(dir, "index.html");
    const diagnosticsTag = `<script src="${pathToFileURL(diagnostics).href}"></script>`;
    writeFileSync(page, `<!DOCTYPE html><html><body><script>${beforeScript}</script>${diagnosticsTag}` +
      `<script>${extraScript}</script></body></html>`);
    return execFileSync(chrome, [
      "--headless=new", "--disable-gpu", "--no-first-run", `--user-data-dir=${join(dir, "profile")}`,
      "--virtual-time-budget=40000", "--dump-dom", pathToFileURL(page).href + query,
    ], { encoding: "utf8", timeout: 120000, stdio: ["ignore", "pipe", "ignore"] });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const opts = { skip: chrome ? false : "Chrome was not found. Set CHROME_PATH." };

// Compose puts its canvas inside a shadow root. This is the case that made the panel cover a working app.
test("the panel stays hidden when the canvas is inside a shadow root", opts, () => {
  const dom = run(`
    const host = document.createElement("div");
    document.body.appendChild(host);
    host.attachShadow({ mode: "open" }).appendChild(document.createElement("canvas"));
  `);
  assert.ok(!dom.includes(PANEL_TITLE), "The panel covers an app that runs.");
});

test("the panel stays hidden when the canvas is in a nested shadow root", opts, () => {
  const dom = run(`
    const outer = document.createElement("div");
    document.body.appendChild(outer);
    const inner = document.createElement("div");
    outer.attachShadow({ mode: "open" }).appendChild(inner);
    inner.attachShadow({ mode: "open" }).appendChild(document.createElement("canvas"));
  `);
  assert.ok(!dom.includes(PANEL_TITLE), "The panel covers an app that runs.");
});

test("the panel stays hidden when the canvas is in the page", opts, () => {
  const dom = run(`document.body.appendChild(document.createElement("canvas"));`);
  assert.ok(!dom.includes(PANEL_TITLE), "The panel covers an app that runs.");
});

test("the panel shows the timeout when no canvas exists", opts, () => {
  const dom = run(``);
  assert.ok(dom.includes(PANEL_TITLE));
  assert.ok(dom.includes("No screen after 20 seconds."));
  assert.ok(dom.includes("Copy text"));
});

test("the panel shows a script error", opts, () => {
  const dom = run(`setTimeout(() => { throw new Error("boom"); }, 100);`);
  assert.ok(dom.includes(PANEL_TITLE));
  assert.ok(dom.includes("Error: Uncaught Error: boom"));
});

test("the panel shows a rejected promise", opts, () => {
  const dom = run(`Promise.reject(new Error("rejected-boom"));`);
  assert.ok(dom.includes(PANEL_TITLE));
  assert.ok(dom.includes("rejected-boom"));
});

test("the panel shows a file that does not load", opts, () => {
  const dom = run(`
    const script = document.createElement("script");
    script.src = "missing-file.js";
    document.body.appendChild(script);
  `);
  assert.ok(dom.includes(PANEL_TITLE));
  assert.ok(dom.includes("File did not load: "));
  assert.ok(dom.includes("missing-file.js"));
});

test("the panel disappears when the app starts after the panel showed", opts, () => {
  // The panel shows at 20 seconds. The canvas appears at 30 seconds. The check runs every second.
  const dom = run(`
    setTimeout(() => {
      const host = document.createElement("div");
      document.body.appendChild(host);
      host.attachShadow({ mode: "open" }).appendChild(document.createElement("canvas"));
    }, 30000);
  `);
  assert.ok(!dom.includes(PANEL_TITLE), "The panel stays over an app that started late.");
});

test("a console warning alone does not show the panel", opts, () => {
  const dom = run(`
    console.warn("deprecated something");
    document.body.appendChild(document.createElement("canvas"));
  `);
  assert.ok(!dom.includes(PANEL_TITLE));
});

test("the panel keeps the console messages in the text", opts, () => {
  const dom = run(`console.error("kotlin says no");`);
  assert.ok(dom.includes(PANEL_TITLE));
  assert.ok(dom.includes("Console error: kotlin says no"));
});

// The real problem of the Safari tester: the app starts, the canvas exists, and the screen stays blank.
test("?diagnostics shows the panel with the canvas data when the app runs", opts, () => {
  const dom = run(`
    const host = document.createElement("div");
    document.body.appendChild(host);
    host.attachShadow({ mode: "open" }).appendChild(document.createElement("canvas"));
  `, "?tournament=x&diagnostics");
  assert.ok(dom.includes("Diagnostics of the app."));
  assert.ok(dom.includes("Canvas of the app:"));
  assert.ok(dom.includes("300x150 pixels"));
  assert.ok(dom.includes("Graphics:"));
  assert.ok(dom.includes("Close"));
});

test("a word that only starts like diagnostics does not force the panel", opts, () => {
  const dom = run(`document.body.appendChild(document.createElement("canvas"));`, "?diagnosticsX=1");
  assert.ok(!dom.includes("Diagnostics of the app."));
});

test("the panel shows a canvas of size zero", opts, () => {
  const dom = run(`
    const canvas = document.createElement("canvas");
    canvas.width = 0;
    document.body.appendChild(canvas);
  `);
  assert.ok(dom.includes("A canvas has size zero."));
  assert.ok(dom.includes("The app did not start."));
});

test("the panel shows a lost WebGL context", opts, () => {
  const dom = run(`
    document.body.appendChild(document.createElement("canvas"));
    setTimeout(() => document.dispatchEvent(new Event("webglcontextlost")), 100);
  `);
  assert.ok(dom.includes("WebGL context lost."));
});

// The panel once made a new WebGL context every second. The browser then dropped the context of the app.
test("?diagnostics does not make WebGL contexts again and again", opts, () => {
  const dom = run(`
    document.body.appendChild(document.createElement("canvas"));
    setTimeout(() => { document.title = "contexts=" + window.__contexts; }, 30000);
  `, "?diagnostics", `
    window.__contexts = 0;
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type) {
      if (type === "webgl" || type === "webgl2") window.__contexts++;
      return original.apply(this, arguments);
    };
  `);
  const count = Number(/contexts=(\d+)/.exec(dom)?.[1]);
  assert.ok(count >= 1 && count <= 2, "Contexts made: " + count);
});
