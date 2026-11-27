import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import * as remoteConfig from "firebase-admin/remote-config";

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

  it("reads the flag from the server template, and keeps the last flags when a later read fails", async () => {
    const before = process.env.FIREBASE_AUTH_EMULATOR_HOST;
    delete process.env.FIREBASE_AUTH_EMULATOR_HOST;
    const original = console.error;
    console.error = () => {};
    // Past the minute that keeps the failed read of the test before.
    let now = Date.now() + 2 * 60_000;
    mock.timers.enable({ apis: ["Date"], now });
    let fails = false;
    const template = {
      load: async () => {
        if (fails) throw new Error("Remote Config is unreachable");
      },
      evaluate: () => ({ getBoolean: (key: string) => key === "levels_enabled" }),
    };
    const fake = mock.method(remoteConfig, "getRemoteConfig", () => ({ getServerTemplate: async () => template }));
    try {
      assert.deepEqual(await getFeatureFlags(), { levelsEnabled: true });
      fails = true;
      now += 61_000;
      mock.timers.setTime(now);
      assert.deepEqual(await getFeatureFlags(), { levelsEnabled: true }, "the flags of the last good read");
    } finally {
      fake.mock.restore();
      mock.timers.reset();
      console.error = original;
      if (before != null) process.env.FIREBASE_AUTH_EMULATOR_HOST = before;
    }
  });
});
