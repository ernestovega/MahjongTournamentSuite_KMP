import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { renderVersionsPage, versionRows } from "./versionsPage";

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
