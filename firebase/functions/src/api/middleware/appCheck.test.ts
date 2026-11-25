import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { Request } from "express";

import { HttpError } from "../httpError";
import { appCheck, appCheckModeFromEnv, type AppCheckMode } from "./appCheck";

async function call(mode: AppCheckMode, token: string | undefined, valid: boolean): Promise<unknown> {
  const middleware = appCheck({
    mode: () => mode,
    verify: async () => { if (!valid) throw new Error("bad token"); },
  });
  let result: unknown = "unset";
  const req = { method: "GET", path: "/x", header: () => token } as unknown as Request;
  await middleware(req, {} as never, (error?: unknown) => { result = error; });
  return result;
}

describe("appCheck", () => {
  it("ignores the header when the mode is off", async () => {
    assert.equal(await call("off", undefined, false), undefined);
  });

  it("lets every request pass in monitor mode", async () => {
    assert.equal(await call("monitor", undefined, false), undefined);
    assert.equal(await call("monitor", "token", false), undefined);
  });

  it("refuses a missing or wrong token in enforce mode", async () => {
    for (const result of [await call("enforce", undefined, true), await call("enforce", "token", false)]) {
      assert.ok(result instanceof HttpError && result.status === 401 && result.code === "app_check_failed");
    }
  });

  it("accepts a valid token in enforce mode", async () => {
    assert.equal(await call("enforce", "token", true), undefined);
  });

  it("logs which app sent no token, its version and whether it was signed in", async () => {
    const middleware = appCheck({ mode: () => "monitor", verify: async () => undefined });
    const headers: Record<string, string> = { "x-mts-client": "android/1.0.341; appcheck=failed:IntegrityException", authorization: "Bearer abc" };
    const req = { method: "GET", path: "/me", header: (name: string) => headers[name.toLowerCase()] } as unknown as Request;
    const logged: unknown[] = [];
    const original = console.warn;
    console.warn = (...args: unknown[]) => { logged.push(args[1]); };
    try {
      await middleware(req, {} as never, () => undefined);
    } finally {
      console.warn = original;
    }
    assert.deepEqual(logged, [{
      reason: "missing", method: "GET", path: "/me", client: "android/1.0.341; appcheck=failed:IntegrityException", userAgent: null, signedIn: true,
    }]);
  });

  it("logs nothing for the web, the desktop app or a script, which send no token", async () => {
    const middleware = appCheck({ mode: () => "monitor", verify: async () => undefined });
    const logged: unknown[] = [];
    const original = console.warn;
    console.warn = (...args: unknown[]) => { logged.push(args); };
    try {
      for (const headers of [{}, { "user-agent": "mts-benchmark" }, { "x-mts-client": "desktop/1.0.370; appcheck=none" }] as Record<string, string>[]) {
        const req = { method: "GET", path: "/x", header: (name: string) => headers[name.toLowerCase()] } as unknown as Request;
        await middleware(req, {} as never, () => undefined);
      }
    } finally {
      console.warn = original;
    }
    assert.deepEqual(logged, []);
  });

  it("reads the mode from a text and falls back to off", () => {
    assert.equal(appCheckModeFromEnv("Enforce"), "enforce");
    assert.equal(appCheckModeFromEnv(" monitor "), "monitor");
    assert.equal(appCheckModeFromEnv("yes"), "off");
    assert.equal(appCheckModeFromEnv(undefined), "off");
  });
});
