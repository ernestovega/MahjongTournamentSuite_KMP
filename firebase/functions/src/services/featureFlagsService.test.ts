import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { getFeatureFlags } from "./featureFlagsService";

describe("feature flags", () => {
  it("are off on the emulators, which cannot read Remote Config", async () => {
    const before = process.env.FIREBASE_AUTH_EMULATOR_HOST;
    process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
    try {
      assert.deepEqual(await getFeatureFlags(), { levelsEnabled: false });
    } finally {
      if (before == null) delete process.env.FIREBASE_AUTH_EMULATOR_HOST;
      else process.env.FIREBASE_AUTH_EMULATOR_HOST = before;
    }
  });

  it("a failed read gives the defaults and keeps them for a minute", async () => {
    const before = process.env.FIREBASE_AUTH_EMULATOR_HOST;
    delete process.env.FIREBASE_AUTH_EMULATOR_HOST;
    const errors: unknown[] = [];
    const original = console.error;
    console.error = (...args: unknown[]) => { errors.push(args); };
    try {
      // No Firebase app runs in this test, so Remote Config fails.
      assert.deepEqual(await getFeatureFlags(), { levelsEnabled: false });
      assert.equal(errors.length, 1);
      assert.deepEqual(await getFeatureFlags(), { levelsEnabled: false });
      assert.equal(errors.length, 1, "the second call uses the kept answer");
    } finally {
      console.error = original;
      if (before != null) process.env.FIREBASE_AUTH_EMULATOR_HOST = before;
    }
  });
});
