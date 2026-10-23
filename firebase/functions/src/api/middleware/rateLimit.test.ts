import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { Request } from "express";

import { HttpError } from "../httpError";
import { rateLimit } from "./rateLimit";

function call(limit: ReturnType<typeof rateLimit>, ip: string): unknown {
  let result: unknown = "unset";
  limit({ ip } as Request, {} as never, (error?: unknown) => { result = error; });
  return result;
}

describe("rateLimit", () => {
  it("lets requests pass until the maximum and then answers 429", () => {
    const limit = rateLimit({ windowMs: 1000, max: 2, now: () => 0 });

    assert.equal(call(limit, "1.1.1.1"), undefined);
    assert.equal(call(limit, "1.1.1.1"), undefined);
    const blocked = call(limit, "1.1.1.1");
    assert.ok(blocked instanceof HttpError && blocked.status === 429);
  });

  it("counts each address on its own and starts a new window later", () => {
    let time = 0;
    const limit = rateLimit({ windowMs: 1000, max: 1, now: () => time });

    assert.equal(call(limit, "1.1.1.1"), undefined);
    assert.equal(call(limit, "2.2.2.2"), undefined);
    assert.ok(call(limit, "1.1.1.1") instanceof HttpError);
    time = 1001;
    assert.equal(call(limit, "1.1.1.1"), undefined);
  });
});
