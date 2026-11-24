// node --test scripts/smoke/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { checks, expectedVersions, ownScripts, smoke } from "../smoke.mjs";

const expected = { web: "1.0.10", backend: "1.0.5" };

/** A site like Firebase Hosting: unknown paths fall back to index.html, as the single page app rewrite does. */
function site(overrides = {}) {
  const index = '<html><script src="loading.js"></script><script src="https://www.googletagmanager.com/gtag.js"></script><script src="playerApp.js"></script></html>';
  const files = {
    "/": ["text/html", index],
    "/admin/": ["text/html", '<html><script src="adminApp.js"></script></html>'],
    "/loading.js": ["text/javascript", "x"],
    "/playerApp.js": ["text/javascript", "x"],
    "/admin/adminApp.js": ["text/javascript", "x"],
    "/version.json": ["application/json", JSON.stringify({ web: "1.0.10" })],
    "/api/health": ["application/json", JSON.stringify({ ok: true, version: "1.0.5" })],
    "/api/public/tournaments": ["application/json", JSON.stringify({ tournaments: [{ id: "t1" }] })],
    "/.well-known/assetlinks.json": ["application/json", JSON.stringify([{ relation: [] }])],
    ...overrides,
  };
  return http.createServer((request, response) => {
    const file = files[request.url];
    if (file === null) {
      response.writeHead(500).end();
      return;
    }
    const [type, body] = file ?? ["text/html", index];
    response.writeHead(200, { "content-type": type }).end(body);
  });
}

async function run(server, options = {}) {
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const url = `http://127.0.0.1:${server.address().port}`;
    return await smoke({ site: url, expected, tries: 2, waitMs: 1, log: () => {}, ...options });
  } finally {
    server.close();
  }
}

const failures = (results) => results.filter((r) => !r.ok).map((r) => `${r.name}: ${r.error}`);

test("a healthy site passes every check", async () => {
  const results = await run(site());
  assert.deepEqual(failures(results), []);
  assert.equal(results.length, checks("x", expected).length);
});

test("an old web version fails", async () => {
  const results = await run(site({ "/version.json": ["application/json", JSON.stringify({ web: "1.0.9" })] }));
  assert.deepEqual(failures(results), ["Web version is 1.0.10: the site serves 1.0.9"]);
});

test("an old backend fails", async () => {
  const results = await run(site({ "/api/health": ["application/json", JSON.stringify({ ok: true, version: "1.0.4" })] }));
  assert.deepEqual(failures(results), ["API answers with backend 1.0.5: the API is 1.0.4"]);
});

test("a missing script that the rewrite answers with the page fails", async () => {
  const results = await run(site({ "/playerApp.js": undefined }));
  assert.match(failures(results)[0], /^Player web loads: its script .*playerApp\.js is an HTML page/);
});

test("an API error and an empty list fail", async () => {
  const broken = await run(site({ "/api/health": null }));
  assert.deepEqual(failures(broken), ["API answers with backend 1.0.5: HTTP 500"]);
  const empty = await run(site({ "/api/public/tournaments": ["application/json", JSON.stringify({ tournaments: [] })] }));
  assert.deepEqual(failures(empty), ["Public tournaments list has data: no tournaments"]);
});

test("a check that fails first and then passes is tried again", async () => {
  let calls = 0;
  const server = site();
  const realFetch = fetch;
  const results = await run(server, {
    tries: 3,
    fetchImpl: (url, init) => {
      if (String(url).endsWith("/version.json") && calls++ === 0) return Promise.resolve(new Response("{}", { headers: { "content-type": "application/json" } }));
      return realFetch(url, init);
    },
  });
  assert.deepEqual(failures(results), []);
});

test("only the scripts of the site itself are loaded", () => {
  const html = '<script src="a.js"></script><script type="module" src="/b.js"></script><script src="https://cdn.example/c.js"></script>';
  assert.deepEqual(ownScripts(html, "https://mts.example/admin/"), ["https://mts.example/admin/a.js", "https://mts.example/b.js"]);
});

test("the expected versions come from the version catalog", async () => {
  const versions = await expectedVersions();
  assert.match(versions.web, /^\d+\.\d+\.\d+$/);
  assert.match(versions.backend, /^\d+\.\d+\.\d+$/);
});
