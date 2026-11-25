// Helpers of the web E2E tests. The app draws on a canvas; with `mts.a11y` = `on` in localStorage it also writes its
// accessibility tree in the DOM (playerApp main.kt). The tests find texts and buttons there by role and name, and press
// them with a real mouse click at their place on the canvas (`force`: the canvas lies over the tree).
import assert from "node:assert/strict";
import { chromium } from "playwright";

export const PASSWORD = "Passw0rd!-e2e";
export const API = process.env.MTS_E2E_API ?? "http://127.0.0.1:5055";
export const ROOT = process.env.MTS_E2E_WEB_ROOT ?? new URL("../../playerApp/build/dist/wasmJs/productionExecutable", import.meta.url).pathname;
const TIMEOUT = 30_000;

const openPages = new Set();

/**
 * Saves a screenshot and the accessibility tree of every page still open, in `failures/`. A test closes its pages when
 * it passes, so after a test only the pages of a failure are left. GitHub keeps the folder of a red run.
 */
export async function saveOpenPages(label) {
  const { mkdir, writeFile } = await import("node:fs/promises");
  const folder = new URL("./failures/", import.meta.url).pathname;
  let index = 0;
  for (const page of openPages) {
    await mkdir(folder, { recursive: true });
    const name = `${label.replace(/[^a-z0-9]+/gi, "-").slice(0, 60)}-${index++}`;
    await page.screenshot({ path: `${folder}${name}.png` }).catch(() => {});
    await writeFile(`${folder}${name}.txt`, `${page.url()}\n${await screenText(page)}`).catch(() => {});
    await page.context().close().catch(() => {});
  }
}

/** Puts back the test tournaments that a test changes. */
export async function resetTournaments(...ids) {
  const response = await fetch(`${API}/__e2e/reset?only=${ids.join(",")}`, { method: "POST" });
  assert.equal(response.status, 200, `reset ${ids}: HTTP ${response.status}`);
}

export async function launch() {
  return chromium.launch(process.env.MTS_E2E_HEADED ? { headless: false } : {});
}

/**
 * A new page of the app at [path], with the analytics consent already answered (no) and the accessibility tree on.
 * [phone] gives a phone screen; otherwise a desktop one.
 */
export async function openApp(browser, siteUrl, path = "/", { phone = true } = {}) {
  const context = await browser.newContext(phone ? { viewport: { width: 412, height: 900 }, deviceScaleFactor: 1 } : { viewport: { width: 1280, height: 800 } });
  await context.addInitScript(() => {
    localStorage.setItem("mts.a11y", "on");
  });
  // Only the local site: no Analytics, no App Check and no other Google script. In GitHub those load (here a proxy
  // stops them), and the App Check of the Firebase scripts then holds back every call of the API.
  await context.route((url) => !["127.0.0.1", "localhost"].includes(url.hostname), (route) => route.abort());
  const page = await context.newPage();
  page.setDefaultTimeout(TIMEOUT);
  // A failed call of the API or an error of the page is printed, so a red run in GitHub says why.
  openPages.add(page);
  page.on("close", () => openPages.delete(page));
  page.on("response", (response) => {
    if (response.url().includes("/api/") && (response.status() >= 400 || process.env.MTS_E2E_VERBOSE)) console.log(`# API ${response.status()} ${response.request().method()} ${new URL(response.url()).pathname}`);
  });
  page.on("requestfailed", (request) => {
    // ERR_ABORTED is a request cut by a new page or a closed one; other sites are blocked on purpose (openApp).
    const local = ["127.0.0.1", "localhost"].includes(new URL(request.url()).hostname);
    if (local && request.failure()?.errorText !== "net::ERR_ABORTED") console.log(`# request failed ${request.url()}: ${request.failure()?.errorText}`);
  });
  page.on("pageerror", (error) => console.log(`# page error ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error") console.log(`# console ${message.text().slice(0, 300)}`);
  });
  await page.goto(siteUrl + path);
  await acceptAnalyticsIfAsked(page);
  return page;
}

async function acceptAnalyticsIfAsked(page) {
  const accept = page.getByRole("button", { name: "Accept and continue" });
  const ready = page.getByRole("button", { name: /^(Sign in|Menu|Profile|Back|Go to my table)$/ }).first();
  await Promise.race([accept.waitFor(), ready.waitFor()]);
  if (await accept.isVisible()) await tap(accept);
}

/** A real click at the place of [locator] on the canvas. */
export async function tap(locator) {
  await locator.waitFor();
  await locator.click({ force: true });
  // Every button ignores a second press within 500 ms; the next tap may be the same button.
  await locator.page().waitForTimeout(600);
}

export const button = (page, name) => page.getByRole("button", { name, exact: typeof name === "string" }).first();

export async function tapButton(page, name) {
  await tap(button(page, name));
}

/** Waits until [text] is somewhere in the accessibility tree. */
export async function seeText(page, text, timeout = TIMEOUT) {
  // The text nodes of the tree have no size, so Playwright never takes them as visible: read the tree instead.
  const end = Date.now() + timeout;
  let tree = "";
  while (Date.now() < end) {
    tree = await screenText(page);
    if (tree.includes(text)) return;
    await page.waitForTimeout(250);
  }
  assert.fail(`"${text}" is not on screen\n${tree}`);
}

export async function notSeeText(page, text) {
  assert.equal(await page.getByText(text).count(), 0, `"${text}" should not be on screen`);
}

/** Types [value] in the field at [index] of the screen (0 = first text field). */
export async function typeIn(page, index, value) {
  const field = page.getByRole("textbox").nth(index);
  await tap(field);
  await page.keyboard.type(value);
  // The app takes the text on the next frames; a button that depends on it is enabled a moment later.
  await page.waitForTimeout(500);
}

export async function signIn(page, email) {
  await tapButton(page, "Sign in");
  await typeIn(page, 0, email);
  await typeIn(page, 1, PASSWORD);
  await page.keyboard.press("Enter");
}

/**
 * Compose for the web leaves the accessibility tree of a closed dialog or menu in the DOM (Compose Multiplatform
 * 1.12): the screen behind it does not come back to the tree. A reload draws the screen again with a fresh tree; the
 * app keeps the screen of the address and the session.
 */
export async function freshTree(page, ready) {
  await page.reload();
  await acceptAnalyticsIfAsked(page);
  if (ready) await ready.waitFor();
}

/** The text of the accessibility tree, for a message when a test fails. */
export async function screenText(page) {
  return page.locator("body").ariaSnapshot().catch((error) => String(error));
}
