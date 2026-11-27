import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it, mock } from "node:test";
import type { NextFunction, Request, Response } from "express";

import { auth } from "../../firebase";
import { requireAuth } from "./requireAuth";

let revocationChecked = false;

/** Signs in [uid] with a GET request and returns whether the token was checked for revocation. */
async function signIn(uid: string): Promise<boolean> {
  revocationChecked = false;
  const req = { method: "GET", header: () => `Bearer token-of-${uid}` } as unknown as Request;
  let passed: unknown = "not called";
  await requireAuth(req, { locals: {} } as unknown as Response, ((error?: unknown) => { passed = error; }) as NextFunction);
  assert.equal(passed, undefined);
  return revocationChecked;
}

/**
 * The kept revocation checks hold up to 1000 accounts. A full list drops the checks that are over first, and only
 * when all of them still hold does it start again.
 */
describe("requireAuth revocation checks", () => {
  beforeEach(() => {
    mock.method(auth, "verifyIdToken", async (token: string, checkRevoked?: boolean) => {
      if (checkRevoked) revocationChecked = true;
      return { uid: token.replace("token-of-", "") };
    });
  });
  afterEach(() => {
    mock.restoreAll();
    mock.timers.reset();
  });

  it("drops the checks that are over when the list is full, and keeps the others", async () => {
    let now = Date.UTC(2027, 1, 6, 10, 0, 0);
    mock.timers.enable({ apis: ["Date"], now });
    for (let index = 0; index < 999; index++) await signIn(`old-${index}`);
    now += 20_000;
    mock.timers.setTime(now);
    assert.equal(await signIn("recent"), true);
    // The old checks are over and leave; the recent one holds.
    now += 15_000;
    mock.timers.setTime(now);
    assert.equal(await signIn("new"), true);
    assert.equal(await signIn("recent"), false, "the check of 15 seconds ago holds");
    assert.equal(await signIn("old-1"), true, "a check that was over is made again");
  });

  it("starts again when every kept check still holds", async () => {
    // Far after the test before, so none of its checks holds.
    const now = Date.UTC(2027, 1, 7, 10, 0, 0);
    mock.timers.enable({ apis: ["Date"], now });
    for (let index = 0; index < 1000; index++) await signIn(`fresh-${index}`);
    assert.equal(await signIn("fresh-0"), false);
    assert.equal(await signIn("one-more"), true);
    assert.equal(await signIn("fresh-0"), true, "the list started again");
  });
});
