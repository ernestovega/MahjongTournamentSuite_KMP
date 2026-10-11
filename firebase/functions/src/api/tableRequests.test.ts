import assert from "node:assert/strict";
import test from "node:test";

import { HttpError } from "./httpError";
import { parseHandPatch, parseSaveTableRequest, parseTablePatch } from "./tableRequests";

const params = { roundId: "2", tableId: "5" };

function rejects(action: () => unknown, pattern: RegExp) {
  assert.throws(action, (error: unknown) => error instanceof HttpError && error.status === 400 && pattern.test(error.message));
}

test("accepts a complete save request", () => {
  const request = parseSaveTableRequest(params, {
    expectedVersion: 3,
    tablePatch: { playerEastId: "4", isCompleted: true },
    handPatches: [{ handId: 2, patch: { handScore: "12", isDone: true } }],
  });

  assert.deepEqual(request, {
    roundId: 2,
    tableId: 5,
    expectedVersion: 3,
    tablePatch: { playerEastId: "4", isCompleted: true },
    handPatches: [{ handId: 2, patch: { handScore: "12", isDone: true } }],
  });
});

test("a missing patch means no change", () => {
  const request = parseSaveTableRequest(params, { expectedVersion: 0 });

  assert.deepEqual(request.tablePatch, {});
  assert.deepEqual(request.handPatches, []);
});

test("version zero is valid", () => {
  assert.equal(parseSaveTableRequest(params, { expectedVersion: 0 }).expectedVersion, 0);
});

test("the version is required and must be a real non-negative integer", () => {
  for (const bad of [undefined, null, "", "3", false, true, [], {}, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
    rejects(() => parseSaveTableRequest(params, { expectedVersion: bad }), /expectedVersion/);
  }
  rejects(() => parseSaveTableRequest(params, undefined), /expectedVersion/);
  rejects(() => parseSaveTableRequest(params, "text"), /expectedVersion/);
});

test("round and table must be positive integers", () => {
  for (const bad of ["0", "-1", "1.5", "abc", undefined, ""]) {
    rejects(() => parseSaveTableRequest({ roundId: bad, tableId: "1" }, { expectedVersion: 0 }), /roundId and tableId/);
    rejects(() => parseSaveTableRequest({ roundId: "1", tableId: bad }, { expectedVersion: 0 }), /roundId and tableId/);
  }
});

test("hand ids go from 1 to 16 and must be numbers", () => {
  for (const bad of [0, 17, -1, 1.5, "2", null, undefined]) {
    rejects(
      () => parseSaveTableRequest(params, { expectedVersion: 0, handPatches: [{ handId: bad, patch: {} }] }),
      /handId/,
    );
  }
  assert.equal(parseSaveTableRequest(params, { expectedVersion: 0, handPatches: [{ handId: 16, patch: {} }] }).handPatches.length, 1);
});

test("a hand can occur only once", () => {
  rejects(
    () => parseSaveTableRequest(params, {
      expectedVersion: 0,
      handPatches: [{ handId: 3, patch: {} }, { handId: 3, patch: {} }],
    }),
    /only once/,
  );
});

test("table fields must have the right type", () => {
  rejects(() => parseTablePatch({ playerEastId: 4 }), /playerEastId must be a string/);
  rejects(() => parseTablePatch({ isCompleted: "yes" }), /isCompleted must be a boolean/);
  rejects(() => parseHandPatch({ handScore: 8 }), /handScore must be a string/);
  rejects(() => parseHandPatch({ isDone: 1 }), /isDone must be a boolean/);
});

test("unknown fields are dropped, so a client cannot write the version or the summary", () => {
  assert.deepEqual(
    parseTablePatch({ version: 99, hasProgress: true, completionStatus: "completed", playerEastId: "1" }),
    { playerEastId: "1" },
  );
  assert.deepEqual(parseHandPatch({ handId: 5, createdAt: "x", handScore: "8" }), { handScore: "8" });
});

test("a body that is not an object gives an empty patch", () => {
  assert.deepEqual(parseTablePatch(null), {});
  assert.deepEqual(parseTablePatch("text"), {});
  assert.deepEqual(parseHandPatch(undefined), {});
});
