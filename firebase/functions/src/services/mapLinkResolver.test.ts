import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { resolveMapLink, type RedirectFetcher } from "./mapLinkResolver";

const placeUrl = "https://www.google.com/maps/place/Eskalerria,+Calle+Cipriano+Sancho+36,+Madrid/@40.4,-3.6,17z";

function fetcherFor(steps: Record<string, { status: number; location: string | null }>): RedirectFetcher & { calls: string[] } {
  const calls: string[] = [];
  const fetcher = async (url: string) => {
    calls.push(url);
    const step = steps[url];
    if (!step) throw new Error(`unexpected request to ${url}`);
    return step;
  };
  return Object.assign(fetcher, { calls });
}

describe("resolveMapLink", () => {
  it("returns a full Google Maps link without a request", async () => {
    const fetcher = fetcherFor({});
    assert.equal(await resolveMapLink(placeUrl, fetcher), placeUrl);
    assert.deepEqual(fetcher.calls, []);
  });

  it("follows a short link to the full link", async () => {
    const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: placeUrl } });
    assert.equal(await resolveMapLink("https://maps.app.goo.gl/abc", fetcher), placeUrl);
  });

  it("reads the real link from the consent page of Google", async () => {
    const consent = `https://consent.google.com/ml?continue=${encodeURIComponent(placeUrl)}&gl=ES`;
    const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: consent } });
    assert.equal(await resolveMapLink("https://maps.app.goo.gl/abc", fetcher), placeUrl);
  });

  it("refuses a link to another host and never requests it", async () => {
    const fetcher = fetcherFor({});
    await assert.rejects(resolveMapLink("https://evil.example.test/maps/place/x", fetcher));
    assert.deepEqual(fetcher.calls, []);
  });

  it("refuses a short link that redirects to another host", async () => {
    const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: "http://169.254.169.254/latest" } });
    await assert.rejects(resolveMapLink("https://maps.app.goo.gl/abc", fetcher));
    assert.deepEqual(fetcher.calls, ["https://maps.app.goo.gl/abc"]);
  });

  it("stops when a short link does not redirect", async () => {
    const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 404, location: null } });
    await assert.rejects(resolveMapLink("https://maps.app.goo.gl/abc", fetcher));
  });

  it("stops after too many redirects", async () => {
    const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: "https://maps.app.goo.gl/abc" } });
    await assert.rejects(resolveMapLink("https://maps.app.goo.gl/abc", fetcher));
    assert.ok(fetcher.calls.length <= 5);
  });

  it("refuses an empty link", async () => {
    await assert.rejects(resolveMapLink("  ", fetcherFor({})));
  });

  it("asks Google itself without following the redirect, and refuses a link that cannot be opened", async () => {
    const asked: Array<{ url: string; redirect?: string }> = [];
    const answers = [
      () => new Response(null, { status: 302, headers: { location: "https://www.google.com/maps/place/Club" } }),
      () => {
        throw new Error("offline");
      },
    ];
    const fetching = mock.method(globalThis, "fetch", async (url: string, init?: RequestInit) => {
      asked.push({ url, redirect: init?.redirect });
      return answers.shift()!();
    });
    try {
      assert.equal(await resolveMapLink("https://maps.app.goo.gl/abc"), "https://www.google.com/maps/place/Club");
      await assert.rejects(resolveMapLink("https://maps.app.goo.gl/def"), /could not be opened/);
    } finally {
      fetching.mock.restore();
    }
    assert.deepEqual(asked.map((call) => call.redirect), ["manual", "manual"]);
  });
});
