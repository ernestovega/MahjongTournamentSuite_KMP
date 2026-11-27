import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { GoogleAuth } from "google-auth-library";

import { renderVersionsPage, versionRows, versionsPage } from "./versionsPage";

const built = { version: "1.0.1", web: "1.0.201", android: "1.0.140", androidCode: "140" };

describe("versions page", () => {
  it("marks each published version that is the latest one", () => {
    const rows = versionRows(built, "1.0.201", {
      releases: [
        { track: "production", name: "1.0.130 (130)", versionCodes: ["130"], status: "completed" },
        { track: "internal", name: "1.0.140 (140)", versionCodes: ["140"], status: "completed" },
      ],
    });
    assert.deepEqual(rows.map((row) => row.latest), ["1.0.201", "1.0.1", "1.0.140 (140)"]);
    assert.equal(rows[0].published[0].upToDate, true);
    // Internal testing first, production last.
    assert.deepEqual(rows[2].published, [
      { text: "Internal testing: 1.0.140 (140)", upToDate: true },
      { text: "Production: 1.0.130 (130)", upToDate: false },
    ]);
  });

  it("shows a web behind main, a draft and why Google Play could not be read", () => {
    const rows = versionRows(built, "1.0.200", {
      releases: [{ track: "alpha", name: "1.0.140 (140)", versionCodes: ["140"], status: "draft" }],
    });
    assert.equal(rows[0].published[0].upToDate, false);
    assert.deepEqual(rows[2].published, [{ text: "Closed testing: 1.0.140 (140) [draft]", upToDate: false }]);
    const failed = versionRows(built, "1.0.201", { error: "no access to Google Play" });
    assert.equal(failed[2].published[0].text, "no access to Google Play");
  });

  it("escapes the texts of the page", () => {
    const html = renderVersionsPage(versionRows(built, "<b>x</b>", { releases: [] }));
    assert.ok(html.includes("&#60;b&#62;x&#60;/b&#62;"));
    assert.ok(html.includes("nothing on Google Play"));
    assert.ok(!html.includes("<b>x</b>"));
  });
});

describe("the page of the versions, with the site and Google Play faked", () => {
  it("reads the web version and the tracks of Google Play, and says why one of them could not be read", async () => {
    const webAnswers: Array<() => Response> = [
      () => new Response(JSON.stringify({ web: "1.0.9" }), { status: 200 }),
      () => new Response("down", { status: 503 }),
      () => new Response(JSON.stringify({}), { status: 200 }),
    ];
    const fetching = mock.method(globalThis, "fetch", async () => {
      const next = webAnswers.shift();
      if (next == null) throw new Error("offline");
      return next();
    });
    const requests: string[] = [];
    const playAnswers: Array<(url: string, method?: string) => unknown> = [];
    const client = {
      request: async ({ url, method }: { url: string; method?: string }) => {
        requests.push(`${method ?? "GET"} ${url.split("/applications/")[1]}`);
        if (method === "POST") return { data: { id: "edit-1" } };
        if (method === "DELETE") throw new Error("already gone");
        return playAnswers.shift()!(url, method);
      },
    };
    const credentials = mock.method(GoogleAuth.prototype, "getCredentials", async () => ({ client_email: "functions@example.test" }));
    const getClient = mock.method(GoogleAuth.prototype, "getClient", async () => client as never);
    mock.timers.enable({ apis: ["Date"], now: Date.UTC(2030, 0, 1) });
    const later = () => mock.timers.tick(6 * 60_000);
    try {
      playAnswers.push(() => ({ data: { tracks: [{ track: "production", releases: [{}] }, { track: "alpha" }] } }));
      const first = await versionsPage();
      assert.match(first, /1\.0\.9/);
      assert.match(first, /Production: +\[\]/);

      later();
      playAnswers.push(() => {
        throw Object.assign(new Error("forbidden"), { response: { status: 403 } });
      });
      const second = await versionsPage();
      assert.match(second, /unknown \(HTTP 503\)/);
      assert.match(second, /Invite functions@example\.test in Play Console/);

      later();
      playAnswers.push(() => ({ data: {} }));
      const third = await versionsPage();
      assert.match(third, /<td>[^<]*<\/td><td><div class="behind">unknown<\/div>/);
      assert.match(third, /nothing on Google Play/);

      later();
      playAnswers.push(() => {
        throw new Error("timeout");
      });
      const fourth = await versionsPage();
      assert.match(fourth, /unknown \(offline\)/);
      assert.match(fourth, /could not read Google Play \(timeout\)/);
    } finally {
      mock.timers.reset();
      fetching.mock.restore();
      credentials.mock.restore();
      getClient.mock.restore();
    }
    assert.ok(requests.includes("POST com.etologic.mahjongtournamentsuite/edits"));
  });
});

describe("the rows of the versions at the edges", () => {
  it("shows unknown builds, unknown tracks after the known ones, and a release without a name", () => {
    const rows = versionRows({ version: "1.0.1" }, "1.0.9", {
      releases: [
        { track: "beta-x", name: "", versionCodes: ["7"], status: "completed" },
        { track: "production", name: "1.0.5", versionCodes: ["5"], status: "completed" },
      ],
    });
    assert.equal(rows[0].latest, "?");
    assert.equal(rows[2].latest, "?");
    assert.deepEqual(rows[2].published.map((row) => row.text), ["Production: 1.0.5", "beta-x: 7"]);
    assert.ok(rows[2].published.every((row) => !row.upToDate));
  });

  it("names the default account when Google Play refuses and the credentials have no email", async () => {
    const fetching = mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({ web: "1.0.9" }), { status: 200 }));
    const credentials = mock.method(GoogleAuth.prototype, "getCredentials", async () => ({}));
    const getClient = mock.method(GoogleAuth.prototype, "getClient", async () => {
      throw Object.assign(new Error("unauthorized"), { response: { status: 401 } });
    });
    mock.timers.enable({ apis: ["Date"], now: Date.UTC(2031, 0, 1) });
    try {
      assert.match(await versionsPage(), /Invite the service account of the functions in Play Console/);
    } finally {
      mock.timers.reset();
      fetching.mock.restore();
      credentials.mock.restore();
      getClient.mock.restore();
    }
  });
});
