import assert from "node:assert/strict";
import test from "node:test";

import { readTableVersions, tableVersionKey, tableVersionUpdate } from "./dataVersionsService";

test("table version key joins round and table", () => {
  assert.equal(tableVersionKey(2, 14), "2_14");
});

test("table versions ignore missing or malformed data", () => {
  assert.deepEqual(readTableVersions(undefined), {});
  assert.deepEqual(readTableVersions(null), {});
  assert.deepEqual(readTableVersions("text"), {});
  assert.deepEqual(readTableVersions({}), {});
});

test("table versions keep only non-negative whole numbers", () => {
  assert.deepEqual(
    readTableVersions({ "1_1": 3, "1_2": -1, "1_3": 1.5, "1_4": "7", "1_5": 0, "1_6": null }),
    { "1_1": 3, "1_5": 0 },
  );
});

test("table version update bumps the coarse revision and sets the exact table version", () => {
  const update = tableVersionUpdate(3, 5, 12);

  assert.equal(update["dataVersions.tableVersions.3_5"], 12);
  assert.ok("dataVersions.tables.revision" in update);
  assert.ok("dataVersions.tables.changedAt" in update);
});
