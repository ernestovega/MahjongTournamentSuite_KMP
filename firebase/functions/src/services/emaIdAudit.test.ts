import assert from "node:assert/strict";
import test from "node:test";

import { findEmaIdProblems, findPlayerClaimProblems } from "./emaIdAudit";

test("accounts and index that agree have no problem", () => {
  assert.deepEqual(findEmaIdProblems({
    profiles: [{ uid: "a", emaId: "1" }, { uid: "b", emaId: "2" }, { uid: "c", emaId: null }],
    index: [{ emaId: "1", uid: "a" }, { emaId: "2", uid: "b" }],
  }), []);
});

test("two accounts with the same EMA id are reported once", () => {
  assert.deepEqual(findEmaIdProblems({
    profiles: [{ uid: "a", emaId: "1" }, { uid: "b", emaId: "1" }],
    index: [{ emaId: "1", uid: "a" }],
  }), [{ code: "DUPLICATE_OWNER", emaId: "1", uids: ["a", "b"] }]);
});

test("a missing or different index document is a mismatch", () => {
  assert.deepEqual(findEmaIdProblems({
    profiles: [{ uid: "a", emaId: "1" }, { uid: "b", emaId: "2" }],
    index: [{ emaId: "2", uid: "a" }],
  }), [
    { code: "INDEX_MISMATCH", emaId: "1", profileUid: "a", indexUid: null },
    { code: "INDEX_MISMATCH", emaId: "2", profileUid: "b", indexUid: "a" },
  ]);
});

test("an index document of an EMA id that no profile holds is an orphan", () => {
  assert.deepEqual(findEmaIdProblems({ profiles: [{ uid: "a", emaId: null }], index: [{ emaId: "9", uid: "a" }] }), [
    { code: "ORPHAN_INDEX", emaId: "9", indexUid: "a" },
  ]);
});

test("the player capability and the EMA id must go together", () => {
  assert.deepEqual(findPlayerClaimProblems([
    { uid: "ok", hasPlayerClaim: true, emaId: "1" },
    { uid: "none", hasPlayerClaim: false, emaId: null },
    { uid: "editor", hasPlayerClaim: true, emaId: null },
    { uid: "late", hasPlayerClaim: false, emaId: "2" },
  ]), [
    { code: "PLAYER_WITHOUT_EMA_ID", uid: "editor" },
    { code: "EMA_ID_WITHOUT_PLAYER", uid: "late" },
  ]);
});
