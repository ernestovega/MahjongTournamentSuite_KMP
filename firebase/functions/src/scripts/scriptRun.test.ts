import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { buildScriptRunEntry } from "./scriptRun";

describe("script run record", () => {
  it("names the script, the machine user, the project and how the run ended", () => {
    const entry = buildScriptRunEntry({
      script: "syncPlayers",
      projectId: "demo",
      outcome: "ok",
      summary: { additions: 3, updates: 1 },
      user: "ernesto",
    });
    assert.equal(entry.action, "script.run");
    assert.equal(entry.uid, "script");
    assert.equal(entry.name, "ernesto (script)");
    assert.equal(entry.target, "syncPlayers");
    assert.equal(entry.tournamentId, null);
    assert.equal(entry.details.additions, 3);
    assert.equal(entry.details.outcome, "ok");
    assert.equal(entry.details.project, "demo");
    assert.equal(entry.details.error, undefined);
  });

  it("keeps a short error message and hides e-mail addresses in the arguments", () => {
    const argv = process.argv;
    process.argv = ["node", "script.js", "--apply", "--admin-email=someone@example.com"];
    try {
      const entry = buildScriptRunEntry({ script: "resetAccess", projectId: "demo", outcome: "failed", error: new Error("x".repeat(500)), user: "u" });
      assert.equal(entry.details.args, "--apply --admin-email=…");
      assert.equal((entry.details.error as string).length, 201);
    } finally {
      process.argv = argv;
    }
  });
});
