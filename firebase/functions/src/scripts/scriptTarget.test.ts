import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { parseScriptTarget } from "./scriptTarget";

describe("script target", () => {
  it("refuses to run without --target", () => {
    assert.throws(() => parseScriptTarget([], {}), /--target=prod/);
    assert.throws(() => parseScriptTarget(["--target=production"], {}), /--target=prod/);
  });

  it("names the project of prod and staging", () => {
    assert.deepEqual(parseScriptTarget(["--target=prod"], {}), { target: "prod", projectId: "mahjong-tournament-suite" });
    assert.deepEqual(parseScriptTarget(["--target=staging"], { GCLOUD_PROJECT: "mts---staging" }), {
      target: "staging",
      projectId: "mts---staging",
    });
  });

  it("refuses a project variable that names another project", () => {
    assert.throws(() => parseScriptTarget(["--target=staging"], { EMA_FIREBASE_PROJECT: "mahjong-tournament-suite" }), /names mahjong/);
    assert.throws(() => parseScriptTarget(["--target=prod"], { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }), /EMULATOR/);
  });

  it("runs on the emulator only with a demo project", () => {
    const emulator = { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" };
    assert.deepEqual(parseScriptTarget(["--target=emulator"], { ...emulator, GCLOUD_PROJECT: "demo-mts-e2e" }), {
      target: "emulator",
      projectId: "demo-mts-e2e",
    });
    assert.throws(() => parseScriptTarget(["--target=emulator"], { GCLOUD_PROJECT: "demo-mts-e2e" }), /FIRESTORE_EMULATOR_HOST/);
    assert.throws(() => parseScriptTarget(["--target=emulator"], { ...emulator, GCLOUD_PROJECT: "mts---staging" }), /demo-/);
  });
});
