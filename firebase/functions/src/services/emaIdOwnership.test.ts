import assert from "node:assert/strict";
import test from "node:test";

import { ownershipAction } from "./emaIdOwnership";

const base = { uid: "u1", emaId: "1" };

test("an account that owns the EMA id already needs no write", () => {
  assert.equal(ownershipAction({ ...base, profileEmaId: "1", indexUid: "u1" }), "ALREADY");
});

test("an account and an EMA id that are both free can be claimed", () => {
  assert.equal(ownershipAction({ ...base, profileEmaId: null, indexUid: null }), "CLAIM");
});

test("a half written claim is completed", () => {
  assert.equal(ownershipAction({ ...base, profileEmaId: "1", indexUid: null }), "CLAIM");
  assert.equal(ownershipAction({ ...base, profileEmaId: null, indexUid: "u1" }), "CLAIM");
});

test("another owner of the EMA id, or another EMA id of the account, is a conflict", () => {
  assert.equal(ownershipAction({ ...base, profileEmaId: null, indexUid: "u2" }), "CONFLICT");
  assert.equal(ownershipAction({ ...base, profileEmaId: "2", indexUid: null }), "CONFLICT");
  assert.equal(ownershipAction({ ...base, profileEmaId: "2", indexUid: "u1" }), "CONFLICT");
});
