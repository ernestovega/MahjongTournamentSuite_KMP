import assert from "node:assert/strict";
import test from "node:test";

import { buildActivityEntry, sanitizeDetails } from "./activityLog";

test("details drop e-mail addresses, image data and tokens", () => {
  assert.deepEqual(
    sanitizeDetails({ name: "Open", contactEmail: "a@b.c", associationLogoDataBase64: "AAAA", token: "x", rounds: 5 }),
    { name: "Open", rounds: 5 },
  );
});

test("details cut long text and nested data", () => {
  const clean = sanitizeDetails({ note: "x".repeat(300), list: [{ email: "a@b.c", id: 1 }], deep: { a: { b: { c: { d: 1 } } } } });
  assert.equal((clean.note as string).length, 201);
  assert.deepEqual(clean.list, [{ id: 1 }]);
  assert.deepEqual(clean.deep, { a: { b: { c: null } } });
});

test("an entry keeps who, what and where", () => {
  const entry = buildActivityEntry({
    action: "player.assign", actor: { uid: "u1", name: "Ana" }, tournamentId: "t1", target: "player 5", details: { emaId: "10990039" },
  });
  assert.deepEqual(entry, {
    action: "player.assign", uid: "u1", name: "Ana", tournamentId: "t1", target: "player 5", details: { emaId: "10990039" },
  });
  assert.equal(buildActivityEntry({ action: "user.create", actor: null }).uid, null);
});
