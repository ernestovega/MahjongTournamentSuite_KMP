import assert from "node:assert/strict";
import test from "node:test";

import {
  blocksAssignmentChangeAfterProgress,
  hasFourValidScores,
  hasValidTablePoints,
} from "./tournamentContentRules";

test("allows an EMA assignment to an empty slot after progress starts", () => {
  assert.equal(blocksAssignmentChangeAfterProgress(null, "12345678", true), false);
});

test("blocks replacement or removal of an existing assignment after progress starts", () => {
  assert.equal(blocksAssignmentChangeAfterProgress("12345678", "87654321", true), true);
  assert.equal(blocksAssignmentChangeAfterProgress("12345678", null, true), true);
});

test("allows assignment changes before progress starts", () => {
  assert.equal(blocksAssignmentChangeAfterProgress("12345678", "87654321", false), false);
});

test("allows an unchanged assignment after progress starts", () => {
  assert.equal(blocksAssignmentChangeAfterProgress("12345678", "12345678", true), false);
});

test("accepts four whole-number scores with a non-zero total", () => {
  assert.equal(hasFourValidScores([30000, 10000, -10000, -35000]), true);
});

test("rejects missing and non-whole-number scores", () => {
  assert.equal(hasFourValidScores([30000, 10000, -10000]), false);
  assert.equal(hasFourValidScores([30000, 10000, -10000, "1.5"]), false);
});

test("requires four numeric table-point values that total seven", () => {
  assert.equal(hasValidTablePoints([4, 2, 1, 0]), true);
  assert.equal(hasValidTablePoints([4, 2, 1, 1]), false);
  assert.equal(hasValidTablePoints([4, 2, 1]), false);
});
