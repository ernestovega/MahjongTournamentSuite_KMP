import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { parseErrorReport } from "../services/errorReports";
import { loadPage, settle, telemetrySource, type TelemetryApp } from "../testing/telemetryJs";

const APPS: TelemetryApp[] = ["admin", "player"];

/** The only item of a list. */
function only<T>(items: T[]): T {
  assert.equal(items.length, 1, `expected one item, got ${items.length}`);
  return items[0];
}

/** The script of both web apps must behave the same. Each test runs for the admin and for the player copy. */
for (const app of APPS) {
  describe(`telemetry.js of the ${app} app`, () => {
    it("does nothing while the settings of Firebase are empty", async () => {
      const page = loadPage(app, { configured: false, storedConsent: "1" });
      assert.equal(page.window.mtsTelemetryConfigured(), false);
      assert.equal(page.window.mtsTelemetryHasConsent(), false);

      page.send("consent", "", { value: true });
      page.send("event", "login");
      await settle();

      assert.equal(page.window.mtsTelemetryHasConsent(), false);
      assert.deepEqual(page.calls.loaded, []);
      assert.deepEqual(page.calls.events, []);
    });

    it("names the app that it belongs to", () => {
      assert.equal(loadPage(app).window.mtsApp, app);
    });

    it("sends nothing and loads nothing before the user accepts", async () => {
      const page = loadPage(app);
      assert.equal(page.window.mtsTelemetryConfigured(), true);
      assert.equal(page.window.mtsTelemetryHasConsent(), false);

      page.send("event", "login");
      page.send("screen", "Tournaments");
      await settle();

      assert.deepEqual(page.calls.loaded, []);
      assert.deepEqual(page.calls.events, []);
    });

    it("starts the SDK at load when the user accepted before", async () => {
      const page = loadPage(app, { storedConsent: "1", language: "es-ES" });
      await settle();

      assert.equal(page.window.mtsTelemetryHasConsent(), true);
      assert.deepEqual(page.calls.loaded, ["firebase-app.js", "firebase-analytics.js"]);
      assert.equal(page.calls.initialized.length, 1);
      // A named app, so it does not clash with the app of App Check.
      assert.equal(page.calls.initialized[0].name, "mts-telemetry");
      assert.equal(page.calls.initialized[0].config.measurementId, "G-TEST");
      assert.deepEqual(page.calls.properties, [{ app, platform: "web", env: "prod", language: "es" }]);
      assert.deepEqual(page.calls.collection, [true]);
    });

    it("marks a page on localhost as a development build", async () => {
      const page = loadPage(app, { storedConsent: "1", hostname: "localhost" });
      await settle();
      assert.equal(page.calls.properties[0].env, "dev");
    });

    it("sends events and screens to Analytics with the parameters of the app", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      page.send("event", "table_saved", { hands: 8 });
      page.send("screen", "Tournaments");
      page.send("property", "role", { value: "admin" });
      await settle();

      assert.deepEqual(page.calls.events, [
        { name: "table_saved", params: { hands: 8 } },
        { name: "screen_view", params: { firebase_screen: "Tournaments", firebase_screen_class: "Tournaments" } },
      ]);
      assert.deepEqual(page.calls.properties[page.calls.properties.length - 1], { role: "admin" });
    });

    it("starts the SDK when the user accepts and writes the answer to the storage", async () => {
      const page = loadPage(app);
      page.send("consent", "", { value: true });
      await settle();

      assert.equal(page.storage.get("mtsAnalyticsConsent"), "1");
      assert.equal(page.window.mtsTelemetryHasConsent(), true);
      assert.equal(page.calls.initialized.length, 1);

      page.send("event", "login");
      await settle();
      assert.deepEqual(page.calls.events, [{ name: "login", params: {} }]);
    });

    it("stops the data when the user withdraws the consent", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      await settle();

      page.send("consent", "", { value: false });
      page.send("event", "login");
      await settle();

      assert.equal(page.storage.get("mtsAnalyticsConsent"), "0");
      assert.equal(page.window.mtsTelemetryHasConsent(), false);
      assert.deepEqual(page.calls.collection, [true, false]);
      assert.deepEqual(page.calls.events, []);
    });

    it("starts the SDK only once when the user accepts again", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      await settle();
      page.send("consent", "", { value: true });
      await settle();
      assert.equal(page.calls.initialized.length, 1);
    });

    it("sends nothing when the browser does not support Analytics", async () => {
      const page = loadPage(app, { storedConsent: "1", supported: false });
      page.send("event", "login");
      await settle();

      assert.deepEqual(page.calls.initialized, []);
      assert.deepEqual(page.calls.events, []);
    });

    it("never throws, whatever the app sends", () => {
      const page = loadPage(app, { storedConsent: "1" });
      assert.doesNotThrow(() => page.sendRaw("event", "login", "{not json"));
      assert.doesNotThrow(() => page.sendRaw("unknown", "", "{}"));
      assert.doesNotThrow(() => page.sendRaw("event", "login", undefined as unknown as string));
    });

    it("works when the storage is blocked, as in a private window", async () => {
      const page = loadPage(app, { storageThrows: true });
      assert.equal(page.window.mtsTelemetryHasConsent(), false);
      assert.doesNotThrow(() => page.send("consent", "", { value: true }));
      await settle();
      // The consent holds for this visit even when it cannot be saved.
      assert.equal(page.window.mtsTelemetryHasConsent(), true);
    });
  });

  describe(`crash reports of the ${app} app`, () => {
    const crash = (message = "boom") => {
      const error = new Error(message);
      error.stack = "Error: boom\n    at run (app.js:1:1)";
      return error;
    };

    it("listens to errors and to rejected promises", () => {
      const page = loadPage(app);
      assert.equal(page.hasListener("error"), true);
      assert.equal(page.hasListener("unhandledrejection"), true);
    });

    it("sends nothing to the error log before the user accepts", async () => {
      const page = loadPage(app);
      page.fire("error", { error: crash(), message: "boom" });
      await settle();
      assert.deepEqual(page.calls.fetches, []);
    });

    it("sends the crash to the error log and counts it in Analytics with the same report id", async () => {
      const page = loadPage(app, { storedConsent: "1", appCheckToken: "token-1", language: "fr-FR" });
      page.send("context", "", { appVersion: "1.2.3", role: "editor" });
      page.fire("error", { error: crash(), message: "boom" });
      await settle();

      const fetch = only(page.calls.fetches);
      assert.equal(fetch.url, "/api/telemetry/errors");
      assert.equal(fetch.init.method, "POST");
      assert.equal(fetch.init.keepalive, true);
      assert.equal(fetch.init.headers["Content-Type"], "application/json");
      assert.equal(fetch.init.headers["X-Firebase-AppCheck"], "token-1");

      const body = JSON.parse(fetch.init.body);
      assert.equal(body.app, app);
      assert.equal(body.platform, "web");
      assert.equal(body.kind, "crash");
      assert.equal(body.errorType, "Error");
      assert.equal(body.message, "boom");
      assert.equal(body.appVersion, "1.2.3");
      assert.equal(body.role, "editor");
      assert.equal(body.language, "fr");
      assert.ok(body.reportId.length > 8);
      assert.ok(body.sessionId.length > 8);

      const event = page.calls.events.find((item) => item.name === "app_crash");
      assert.deepEqual(event?.params, { report_id: body.reportId, kind: "Error" });
    });

    it("builds a body that the server accepts", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      page.send("context", "", { appVersion: "1.2.3", role: "admin" });
      page.fire("error", { error: crash(), message: "boom" });
      await settle();

      const report = parseErrorReport(JSON.parse(only(page.calls.fetches).init.body));
      assert.equal(report.app, app);
      assert.equal(report.platform, "web");
      assert.equal(report.kind, "crash");
      assert.equal(report.env, "prod");
      assert.equal(report.role, "admin");
      assert.equal(report.appVersion, "1.2.3");
      assert.equal(report.stackTrace, "Error: boom\n    at run (app.js:1:1)");
    });

    it("sends no App Check header when the page has no token", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      page.fire("error", { error: crash(), message: "boom" });
      await settle();
      assert.equal("X-Firebase-AppCheck" in only(page.calls.fetches).init.headers, false);
    });

    it("cuts a long message and a long stack to the limits of the server", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      const error = crash("m".repeat(900));
      error.stack = "s".repeat(9000);
      page.fire("error", { error, message: "m".repeat(900) });
      await settle();

      const body = JSON.parse(only(page.calls.fetches).init.body);
      assert.equal(body.message.length, 500);
      assert.equal(body.stackTrace.length, 8000);
    });

    it("names a rejected promise with a plain value", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      page.fire("unhandledrejection", { reason: "plain text" });
      await settle();

      const body = JSON.parse(only(page.calls.fetches).init.body);
      assert.equal(body.errorType, "UnhandledRejection");
      assert.equal(body.stackTrace, "");
    });

    it("sends at most five crashes for each visit, so a loop cannot flood the log", async () => {
      const page = loadPage(app, { storedConsent: "1" });
      for (let count = 0; count < 8; count += 1) page.fire("error", { error: crash(), message: "boom" });
      await settle();
      assert.equal(page.calls.fetches.length, 5);
    });

    it("survives a log that cannot be reached", async () => {
      const page = loadPage(app, { storedConsent: "1", fetchFails: true });
      assert.doesNotThrow(() => page.fire("error", { error: crash(), message: "boom" }));
      await settle();
      assert.equal(page.calls.fetches.length, 1);
    });
  });
}

describe("the two copies of telemetry.js", () => {
  it("differ only in the name of the app", () => {
    const strip = (source: string) => source.replace(/const APP = "(admin|player)";/, 'const APP = "<app>";');
    assert.equal(strip(telemetrySource("admin")), strip(telemetrySource("player")));
  });
});
