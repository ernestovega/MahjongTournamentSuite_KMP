import assert from "node:assert/strict";
import test from "node:test";

import { actorFromToken, buildResetChangeEntry, buildSaveChangeEntry } from "./changeLog";

const actor = { uid: "u1", name: "Ana" };

test("actor comes from the verified token and falls back to the e-mail", () => {
  assert.deepEqual(actorFromToken({ uid: "u1", name: " Ana " }), { uid: "u1", name: "Ana" });
  assert.deepEqual(actorFromToken({ uid: "u1", email: "ana@example.com" }), { uid: "u1", name: "ana@example.com" });
  assert.deepEqual(actorFromToken({ uid: "u1" }), { uid: "u1", name: null });
  assert.equal(actorFromToken(undefined), null);
  assert.equal(actorFromToken({ name: "No uid" }), null);
});

test("save entry lists only the values that changed", () => {
  const entry = buildSaveChangeEntry({
    roundId: 2,
    tableId: 5,
    version: 7,
    actor,
    tableBefore: { playerEastId: "1", isCompleted: false },
    tablePatch: { playerEastId: "1", isCompleted: true, manualPlayerEastScore: "10" },
    handsBefore: new Map([["3", { handScore: "8", isDone: false }]]),
    handPatches: [{ handId: 3, patch: { handScore: "8", isDone: true } }],
  });

  assert.deepEqual(entry.tableChanges, [
    { field: "isCompleted", from: false, to: true },
    { field: "manualPlayerEastScore", from: null, to: "10" },
  ]);
  assert.deepEqual(entry.handChanges, [{ handId: 3, field: "isDone", from: false, to: true }]);
  assert.equal(entry.version, 7);
  assert.equal(entry.kind, "save");
  assert.equal(entry.uid, "u1");
});

test("an empty value and a missing value count as the same", () => {
  const entry = buildSaveChangeEntry({
    roundId: 1, tableId: 1, version: 1, actor,
    tableBefore: {}, tablePatch: { playerEastId: "" },
    handsBefore: new Map(), handPatches: [],
  });

  assert.deepEqual(entry.tableChanges, []);
});

test("derived totals are not logged", () => {
  const entry = buildSaveChangeEntry({
    roundId: 1, tableId: 1, version: 1, actor,
    tableBefore: {}, tablePatch: { playerEastScore: "32", playerNorthPoints: "1" },
    handsBefore: new Map(), handPatches: [],
  });

  assert.deepEqual(entry.tableChanges, []);
});

test("hand changes are ordered by hand and keep the field order", () => {
  const entry = buildSaveChangeEntry({
    roundId: 1, tableId: 1, version: 1, actor,
    tableBefore: {}, tablePatch: {},
    handsBefore: new Map(),
    handPatches: [
      { handId: 9, patch: { handScore: "8" } },
      { handId: 2, patch: { handScore: "12", isDone: true } },
    ],
  });

  assert.deepEqual(entry.handChanges.map((change) => `${change.handId}:${change.field}`), ["2:handScore", "2:isDone", "9:handScore"]);
});

test("entry without a user has null uid and name", () => {
  const entry = buildSaveChangeEntry({
    roundId: 1, tableId: 1, version: 1, actor: null,
    tableBefore: {}, tablePatch: { playerEastId: "1" }, handsBefore: new Map(), handPatches: [],
  });

  assert.equal(entry.uid, null);
  assert.equal(entry.name, null);
});

test("reset entry has no field lists", () => {
  const entry = buildResetChangeEntry({ roundId: 3, tableId: 4, version: 9, actor });

  assert.deepEqual(entry, {
    kind: "reset", roundId: 3, tableId: 4, version: 9, uid: "u1", name: "Ana", tableChanges: [], handChanges: [],
  });
});
