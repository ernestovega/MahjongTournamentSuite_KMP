"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const mapLinkResolver_1 = require("./mapLinkResolver");
const placeUrl = "https://www.google.com/maps/place/Eskalerria,+Calle+Cipriano+Sancho+36,+Madrid/@40.4,-3.6,17z";
function fetcherFor(steps) {
    const calls = [];
    const fetcher = async (url) => {
        calls.push(url);
        const step = steps[url];
        if (!step)
            throw new Error(`unexpected request to ${url}`);
        return step;
    };
    return Object.assign(fetcher, { calls });
}
(0, node_test_1.describe)("resolveMapLink", () => {
    (0, node_test_1.it)("returns a full Google Maps link without a request", async () => {
        const fetcher = fetcherFor({});
        strict_1.default.equal(await (0, mapLinkResolver_1.resolveMapLink)(placeUrl, fetcher), placeUrl);
        strict_1.default.deepEqual(fetcher.calls, []);
    });
    (0, node_test_1.it)("follows a short link to the full link", async () => {
        const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: placeUrl } });
        strict_1.default.equal(await (0, mapLinkResolver_1.resolveMapLink)("https://maps.app.goo.gl/abc", fetcher), placeUrl);
    });
    (0, node_test_1.it)("reads the real link from the consent page of Google", async () => {
        const consent = `https://consent.google.com/ml?continue=${encodeURIComponent(placeUrl)}&gl=ES`;
        const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: consent } });
        strict_1.default.equal(await (0, mapLinkResolver_1.resolveMapLink)("https://maps.app.goo.gl/abc", fetcher), placeUrl);
    });
    (0, node_test_1.it)("refuses a link to another host and never requests it", async () => {
        const fetcher = fetcherFor({});
        await strict_1.default.rejects((0, mapLinkResolver_1.resolveMapLink)("https://evil.example.test/maps/place/x", fetcher));
        strict_1.default.deepEqual(fetcher.calls, []);
    });
    (0, node_test_1.it)("refuses a short link that redirects to another host", async () => {
        const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: "http://169.254.169.254/latest" } });
        await strict_1.default.rejects((0, mapLinkResolver_1.resolveMapLink)("https://maps.app.goo.gl/abc", fetcher));
        strict_1.default.deepEqual(fetcher.calls, ["https://maps.app.goo.gl/abc"]);
    });
    (0, node_test_1.it)("stops when a short link does not redirect", async () => {
        const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 404, location: null } });
        await strict_1.default.rejects((0, mapLinkResolver_1.resolveMapLink)("https://maps.app.goo.gl/abc", fetcher));
    });
    (0, node_test_1.it)("stops after too many redirects", async () => {
        const fetcher = fetcherFor({ "https://maps.app.goo.gl/abc": { status: 302, location: "https://maps.app.goo.gl/abc" } });
        await strict_1.default.rejects((0, mapLinkResolver_1.resolveMapLink)("https://maps.app.goo.gl/abc", fetcher));
        strict_1.default.ok(fetcher.calls.length <= 5);
    });
    (0, node_test_1.it)("refuses an empty link", async () => {
        await strict_1.default.rejects((0, mapLinkResolver_1.resolveMapLink)("  ", fetcherFor({})));
    });
});
//# sourceMappingURL=mapLinkResolver.test.js.map