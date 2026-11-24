import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { Request } from "express";

import { HttpError } from "../httpError";
import { rateLimit } from "./rateLimit";

/** A request as Google forwards it: the caller's address at the end of X-Forwarded-For. */
function request(forwardedFor: string): Request {
  return { header: (name: string) => (name.toLowerCase() === "x-forwarded-for" ? forwardedFor : undefined), socket: {} } as unknown as Request;
}

function call(limit: ReturnType<typeof rateLimit>, ip: string): unknown {
  let result: unknown = "unset";
  limit(request(ip), {} as never, (error?: unknown) => { result = error; });
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

  it("counts by the key function when one is given", () => {
    const limit = rateLimit({ windowMs: 1000, max: 1, now: () => 0, key: (req) => req.header("authorization") ?? "" });
    const withToken = (token: string) => {
      let result: unknown = "unset";
      limit({ ip: "1.1.1.1", header: () => token } as unknown as Request, {} as never, (error?: unknown) => { result = error; });
      return result;
    };

    assert.equal(withToken("a"), undefined);
    assert.equal(withToken("b"), undefined);
    assert.ok(withToken("a") instanceof HttpError);
  });

  it("ignores addresses that the client writes before the one Google adds", () => {
    const limit = rateLimit({ windowMs: 1000, max: 1, now: () => 0 });

    assert.equal(call(limit, "9.9.9.1, 5.5.5.5"), undefined);
    assert.ok(call(limit, "9.9.9.2, 5.5.5.5") instanceof HttpError);
  });

  it("keeps counting the clients it knows when a flood of new keys fills its memory", () => {
    const limit = rateLimit({ windowMs: 1000, max: 1, now: () => 0 });

    assert.equal(call(limit, "1.1.1.1"), undefined);
    for (let i = 0; i < 6000; i++) call(limit, `10.${Math.floor(i / 65536)}.${Math.floor(i / 256) % 256}.${i % 256}`);
    assert.ok(call(limit, "1.1.1.1") instanceof HttpError);
  });
});
