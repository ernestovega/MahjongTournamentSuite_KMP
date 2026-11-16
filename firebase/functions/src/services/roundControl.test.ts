import assert from "node:assert/strict";
import test from "node:test";

import { manualHands, manualSeats, parseRoundControlChange, parseTablesToComplete, roundControlViews } from "./roundControl";

const tournament = {
  numRounds: 3,
  roundControls: {
    1: { hands: "open", seats: "closed", tables: { 2: "closed", x: "open", 3: "maybe" } },
    2: { hands: "nonsense" },
    9: { hands: "open" },
  },
};

test("a table control wins over its round, and damaged values count as automatic", () => {
  assert.equal(manualHands(tournament, 1, 1), "open");
  assert.equal(manualHands(tournament, 1, 2), "closed");
  assert.equal(manualHands(tournament, 2, 1), null);
  assert.equal(manualHands(tournament, 3, 1), null);
  assert.equal(manualSeats(tournament, 1), "closed");
  assert.equal(manualSeats(tournament, 2), null);
});

test("the apps get only the rounds with a control, inside the rounds of the tournament", () => {
  assert.deepEqual(roundControlViews(tournament), [{ roundId: 1, hands: "open", seats: "closed", tables: [{ tableId: 2, hands: "closed" }] }]);
});

test("the request body names what changes", () => {
  assert.deepEqual(parseRoundControlChange({ hands: "auto" }), { hands: "auto" });
  assert.deepEqual(parseRoundControlChange({ table: { tableId: 4, hands: "open" }, reopenTables: [4, 4, 5] }), {
    table: { tableId: 4, hands: "open" }, reopenTables: [4, 5],
  });
  assert.throws(() => parseRoundControlChange({}));
  assert.throws(() => parseRoundControlChange({ hands: "maybe" }));
  assert.throws(() => parseRoundControlChange({ table: { tableId: 0, hands: "open" } }));
  assert.throws(() => parseRoundControlChange({ reopenTables: [1, "x"] }));
  assert.deepEqual(parseRoundControlChange({ completeTables: [3, 3] }), { completeTables: [3] });
  assert.throws(() => parseRoundControlChange({ completeTables: 3 }));
  assert.throws(() => parseRoundControlChange({ reopenTables: [3], completeTables: [3] }));
});

test("the tables to close name their round, count once and come sorted", () => {
  assert.deepEqual(
    parseTablesToComplete({ tables: [{ roundId: 2, tableId: 3 }, { roundId: 1, tableId: 5 }, { roundId: "2", tableId: 3 }, { roundId: 1, tableId: 2 }] }),
    [{ roundId: 1, tableId: 2 }, { roundId: 1, tableId: 5 }, { roundId: 2, tableId: 3 }],
  );
  assert.throws(() => parseTablesToComplete({}), /tables must be/);
  assert.throws(() => parseTablesToComplete({ tables: [] }), /tables must be/);
  assert.throws(() => parseTablesToComplete({ tables: [{ roundId: 0, tableId: 1 }] }), /tables must be/);
  assert.throws(() => parseTablesToComplete({ tables: [{ roundId: 1 }] }), /tables must be/);
});
