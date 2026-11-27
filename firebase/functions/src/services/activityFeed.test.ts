import assert from "node:assert/strict";
import test from "node:test";

import { Timestamp } from "firebase-admin/firestore";

import {
  activityEntryFrom,
  decodeFeedCursor,
  encodeFeedCursor,
  entryHasText,
  searchKey,
  tableEntryFrom,
  valueText,
} from "./activityFeed";

test("a cursor goes back and forth, and any other text is refused", () => {
  const cursor = { a: { s: 1, n: 2, p: "activity/x" }, t: "end" as const };
  assert.deepEqual(decodeFeedCursor(encodeFeedCursor(cursor)), cursor);
  assert.deepEqual(decodeFeedCursor(undefined), { a: null, t: null });
  assert.deepEqual(decodeFeedCursor(""), { a: null, t: null });

  const bad = (text: unknown) => assert.throws(() => decodeFeedCursor(text), /cursor is not valid/);
  bad(42);
  bad("%%%");
  bad(Buffer.from("[1]").toString("base64url"));
  bad(encodeFeedCursor({ a: { s: 1, n: 2, p: "" }, t: null } as never));
  bad(Buffer.from(JSON.stringify({ a: 3, t: null })).toString("base64url"));
});

test("a value shows as short text", () => {
  assert.equal(valueText(null), null);
  assert.equal(valueText(undefined), null);
  assert.equal(valueText(""), null);
  assert.equal(valueText("Ana"), "Ana");
  assert.equal(valueText({ a: 1 }), '{"a":1}');
  const long = valueText("x".repeat(300));
  assert.equal(long?.length, 201);
  assert.ok(long?.endsWith("…"));
});

test("a record without its fields gives an empty entry", () => {
  const activity = activityEntryFrom("activity/1", {});
  assert.equal(activity.at, 0);
  assert.equal(activity.action, "");
  assert.equal(activity.uid, null);
  assert.deepEqual(activity.details, []);

  const withDetails = activityEntryFrom("activity/2", {
    at: Timestamp.fromMillis(5000), action: "players.update", uid: "u1", details: { name: "Ana", empty: "", age: 30 },
  });
  assert.equal(withDetails.at, 5000);
  assert.deepEqual(withDetails.details, [{ key: "age", value: "30" }, { key: "empty", value: "–" }, { key: "name", value: "Ana" }]);

  const table = tableEntryFrom("t/changes/1", "t", { tableChanges: "no list", handChanges: [{ from: "1", to: "2", handId: "x" }] });
  assert.equal(table.action, "table.save");
  assert.equal(table.roundId, null);
  assert.deepEqual(table.changes, [{ field: "", from: "1", to: "2", handId: 0 }]);

  const reset = tableEntryFrom("t/changes/2", "t", { kind: "reset", roundId: 1, tableId: 2, version: 3, tableChanges: [{ field: "score" }] });
  assert.equal(reset.action, "table.reset");
  assert.deepEqual(reset.changes, [{ field: "score", from: null, to: null }]);
});

test("the search finds a text in any part of an entry, without case or accents", () => {
  const entry = { ...tableEntryFrom("t/changes/1", "t", { roundId: 2, tableId: 3, name: "José" }), players: ["Lucía"] };
  assert.equal(searchKey("  JOSÉ "), "jose");
  assert.ok(entryHasText(entry, "jose"));
  assert.ok(entryHasText(entry, "round 2"));
  assert.ok(entryHasText(entry, "table 3"));
  assert.ok(entryHasText(entry, "lucia"));
  assert.equal(entryHasText(activityEntryFrom("a/1", {}), "jose"), false);
});
