import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { shouldWarmUp, warmUpFirestore } from "./startupWarmup";

describe("warm-up of a new API instance", () => {
  it("runs only in the deployed API", () => {
    assert.equal(shouldWarmUp({ FUNCTION_TARGET: "api" }), true);
    assert.equal(shouldWarmUp({ FUNCTION_TARGET: "closeRoundTablesTask" }), false);
    assert.equal(shouldWarmUp({}), false);
    assert.equal(shouldWarmUp({ FUNCTION_TARGET: "api", FUNCTIONS_EMULATOR: "true" }), false);
    assert.equal(shouldWarmUp({ FUNCTION_TARGET: "api", FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }), false);
  });

  it("makes one read and never fails the start", async () => {
    let reads = 0;
    await warmUpFirestore(async () => { reads += 1; });
    await warmUpFirestore(async () => { throw new Error("offline"); });
    assert.equal(reads, 1);
  });
});
