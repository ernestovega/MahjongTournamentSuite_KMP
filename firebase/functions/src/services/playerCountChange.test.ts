import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { planPlayerCountChange, slotNameFor, teamNameFor, type CurrentSlot, type PlayerCountPlan, type PlanProblem } from "./playerCountChange";

function slots(count: number, isTeams = false, emaOf: (id: number) => string | null = (id) => `E${id}`): CurrentSlot[] {
  return Array.from({ length: count }, (_, index) => ({ id: index + 1, team: isTeams ? Math.floor(index / 4) + 1 : 0, emaId: emaOf(index + 1) }));
}

function newPlayers(count: number, isTeams = false) {
  return Array.from({ length: count }, (_, index) => ({ id: index + 1, team: isTeams ? Math.floor(index / 4) + 1 : 0 }));
}

function plan(result: PlayerCountPlan | PlanProblem): PlayerCountPlan {
  if ("problem" in result) assert.fail(`Unexpected problem ${result.code}: ${result.problem}`);
  return result;
}

function code(result: PlayerCountPlan | PlanProblem): string | null {
  return "problem" in result ? result.code : null;
}

const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, index) => from + index);

describe("plan of a change of the number of players, from a draw of an older app on 1..N", () => {
  it("removes a table: the people who stay take the lowest slots in their order", () => {
    const result = plan(planPlayerCountChange({
      current: slots(20), isTeams: false, newPlayers: newPlayers(16), removedSlotIds: [5, 6, 7, 8], waitingEmaIds: [],
    }));
    assert.deepEqual(result.removedSlotIds, [5, 6, 7, 8]);
    assert.deepEqual([...result.sourceOf.entries()], [
      [1, 1], [2, 2], [3, 3], [4, 4], [5, 9], [6, 10], [7, 11], [8, 12],
      [9, 13], [10, 14], [11, 15], [12, 16], [13, 17], [14, 18], [15, 19], [16, 20],
    ]);
    assert.equal(result.fills.size, 0);
    assert.equal(result.teamSourceOf.size, 0);
  });

  it("removes slots that are not together, and in any order", () => {
    const result = plan(planPlayerCountChange({
      current: slots(20), isTeams: false, newPlayers: newPlayers(16), removedSlotIds: [20, 1, 13, 7], waitingEmaIds: [],
    }));
    assert.deepEqual(result.removedSlotIds, [1, 7, 13, 20]);
    const sources = [...result.sourceOf.values()];
    assert.deepEqual(sources, [2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19]);
  });

  it("removes two tables at once", () => {
    const result = plan(planPlayerCountChange({
      current: slots(24), isTeams: false, newPlayers: newPlayers(16), removedSlotIds: range(17, 24), waitingEmaIds: [],
    }));
    assert.deepEqual([...result.sourceOf.values()], range(1, 16));
  });

  it("adds a table: the new slots come last and empty", () => {
    const result = plan(planPlayerCountChange({
      current: slots(16), isTeams: false, newPlayers: newPlayers(20), removedSlotIds: [], waitingEmaIds: [],
    }));
    assert.deepEqual([...result.sourceOf.entries()].slice(15), [[16, 16], [17, null], [18, null], [19, null], [20, null]]);
    assert.equal(result.fills.size, 0);
  });

  it("adds tables and fills new slots with people of the waiting list, in their order", () => {
    const result = plan(planPlayerCountChange({
      current: slots(16), isTeams: false, newPlayers: newPlayers(24), removedSlotIds: [], waitingEmaIds: ["W1", "W2", "W3", "W4", "W5"],
    }));
    assert.deepEqual([...result.fills.entries()], [[17, "W1"], [18, "W2"], [19, "W3"], [20, "W4"], [21, "W5"]]);
  });

  it("keeps the empty slots that stay as slots without a person", () => {
    const result = plan(planPlayerCountChange({
      current: slots(20, false, (id) => (id % 2 === 0 ? null : `E${id}`)), isTeams: false, newPlayers: newPlayers(16),
      removedSlotIds: [1, 2, 3, 4], waitingEmaIds: [],
    }));
    assert.equal(result.sourceOf.get(1), 5);
  });

  it("refuses an invalid new number of players", () => {
    const base = { current: slots(20), isTeams: false, removedSlotIds: [], waitingEmaIds: [] };
    assert.equal(code(planPlayerCountChange({ ...base, newPlayers: newPlayers(12), removedSlotIds: range(13, 20) })), "BAD_PLAYER_COUNT");
    assert.equal(code(planPlayerCountChange({ ...base, newPlayers: newPlayers(22) })), "BAD_PLAYER_COUNT");
    assert.equal(code(planPlayerCountChange({ ...base, newPlayers: newPlayers(0) })), "BAD_PLAYER_COUNT");
    assert.equal(code(planPlayerCountChange({ ...base, newPlayers: newPlayers(20) })), "SAME_PLAYER_COUNT");
  });

  it("refuses new slots with the same id", () => {
    const players = newPlayers(20);
    players[19] = { id: 1, team: 0 };
    assert.equal(code(planPlayerCountChange({ current: slots(16), isTeams: false, newPlayers: players, removedSlotIds: [], waitingEmaIds: [] })), "BAD_DRAW");
  });

  it("refuses removed slots that are too few, too many, unknown or twice", () => {
    const base = { current: slots(20), isTeams: false, newPlayers: newPlayers(16), waitingEmaIds: [] };
    assert.equal(code(planPlayerCountChange({ ...base, removedSlotIds: [1, 2, 3] })), "BAD_REMOVED_SLOTS");
    assert.equal(code(planPlayerCountChange({ ...base, removedSlotIds: [1, 2, 3, 4, 5] })), "BAD_REMOVED_SLOTS");
    assert.equal(code(planPlayerCountChange({ ...base, removedSlotIds: [1, 2, 3, 21] })), "BAD_REMOVED_SLOTS");
    assert.equal(code(planPlayerCountChange({ ...base, removedSlotIds: [1, 2, 3, 3] })), "BAD_REMOVED_SLOTS");
    assert.equal(code(planPlayerCountChange({ ...base, removedSlotIds: [] })), "BAD_REMOVED_SLOTS");
  });

  it("refuses removed slots when the number grows", () => {
    assert.equal(code(planPlayerCountChange({
      current: slots(16), isTeams: false, newPlayers: newPlayers(20), removedSlotIds: [1], waitingEmaIds: [],
    })), "BAD_REMOVED_SLOTS");
  });

  it("refuses the waiting list when the number goes down, more people than new slots, twice or with a slot", () => {
    assert.equal(code(planPlayerCountChange({
      current: slots(20), isTeams: false, newPlayers: newPlayers(16), removedSlotIds: [1, 2, 3, 4], waitingEmaIds: ["W1"],
    })), "BAD_WAITING");
    const grow = { current: slots(16), isTeams: false, newPlayers: newPlayers(20), removedSlotIds: [] };
    assert.equal(code(planPlayerCountChange({ ...grow, waitingEmaIds: ["W1", "W2", "W3", "W4", "W5"] })), "BAD_WAITING");
    assert.equal(code(planPlayerCountChange({ ...grow, waitingEmaIds: ["W1", "W1"] })), "BAD_WAITING");
    assert.equal(code(planPlayerCountChange({ ...grow, waitingEmaIds: ["E3"] })), "BAD_WAITING");
  });

  it("does not depend on the order of the current slots", () => {
    const shuffled = slots(20).reverse();
    const result = plan(planPlayerCountChange({ current: shuffled, isTeams: false, newPlayers: newPlayers(16).reverse(), removedSlotIds: [5, 6, 7, 8], waitingEmaIds: [] }));
    assert.equal(result.sourceOf.get(5), 9);
  });
});

describe("plan of a change of the number of players with teams, from a draw of an older app on 1..N", () => {
  it("removes a whole team: the teams that stay take the lowest numbers, the people keep their order", () => {
    const result = plan(planPlayerCountChange({
      current: slots(20, true), isTeams: true, newPlayers: newPlayers(16, true), removedSlotIds: [5, 6, 7, 8], waitingEmaIds: [],
    }));
    assert.deepEqual([...result.teamSourceOf.entries()], [[1, 1], [2, 3], [3, 4], [4, 5]]);
    assert.equal(result.sourceOf.get(5), 9);
    assert.equal(result.sourceOf.get(16), 20);
  });

  it("follows the teams of the new draw, whatever its slot numbers", () => {
    // The new draw puts the teams in other slots: team 1 = 13..16, team 2 = 9..12, and so on.
    const players = newPlayers(16).map((player) => ({ id: player.id, team: 4 - Math.floor((player.id - 1) / 4) }));
    const result = plan(planPlayerCountChange({ current: slots(20, true), isTeams: true, newPlayers: players, removedSlotIds: [1, 2, 3, 4], waitingEmaIds: [] }));
    assert.deepEqual([...result.teamSourceOf.entries()], [[1, 2], [2, 3], [3, 4], [4, 5]]);
    assert.deepEqual([13, 14, 15, 16].map((id) => result.sourceOf.get(id)), [5, 6, 7, 8]);
  });

  it("removes two whole teams", () => {
    const result = plan(planPlayerCountChange({
      current: slots(24, true), isTeams: true, newPlayers: newPlayers(16, true), removedSlotIds: [1, 2, 3, 4, 21, 22, 23, 24], waitingEmaIds: [],
    }));
    assert.deepEqual([...result.teamSourceOf.values()], [2, 3, 4, 5]);
  });

  it("refuses to remove part of a team", () => {
    assert.equal(code(planPlayerCountChange({
      current: slots(20, true), isTeams: true, newPlayers: newPlayers(16, true), removedSlotIds: [4, 5, 6, 7], waitingEmaIds: [],
    })), "PARTIAL_TEAM");
  });

  it("adds a new empty team, and fills it from the waiting list", () => {
    const empty = plan(planPlayerCountChange({ current: slots(16, true), isTeams: true, newPlayers: newPlayers(20, true), removedSlotIds: [], waitingEmaIds: [] }));
    assert.equal(empty.teamSourceOf.get(5), null);
    assert.deepEqual([17, 18, 19, 20].map((id) => empty.sourceOf.get(id)), [null, null, null, null]);
    const filled = plan(planPlayerCountChange({ current: slots(16, true), isTeams: true, newPlayers: newPlayers(20, true), removedSlotIds: [], waitingEmaIds: ["W1", "W2"] }));
    assert.deepEqual([...filled.fills.entries()], [[17, "W1"], [18, "W2"]]);
  });

  it("refuses a new draw whose teams do not have 4 slots", () => {
    const players = newPlayers(20, true);
    players[19] = { id: 20, team: 1 };
    assert.equal(code(planPlayerCountChange({ current: slots(16, true), isTeams: true, newPlayers: players, removedSlotIds: [], waitingEmaIds: [] })), "BAD_DRAW");
    const noTeam = newPlayers(20, false);
    assert.equal(code(planPlayerCountChange({ current: slots(16, true), isTeams: true, newPlayers: noTeam, removedSlotIds: [], waitingEmaIds: [] })), "BAD_DRAW");
  });
});

describe("plan of a change that keeps the numbers of the people who stay", () => {
  const players = (ids: number[], teamOf: (id: number) => number = () => 0) => ids.map((id) => ({ id, team: teamOf(id) }));
  const teamOf = (id: number) => Math.floor((id - 1) / 4) + 1;

  it("removes slots: everybody who stays keeps their number", () => {
    const kept = range(1, 20).filter((id) => ![2, 7, 13, 19].includes(id));
    const result = plan(planPlayerCountChange({ current: slots(20), isTeams: false, newPlayers: players(kept), removedSlotIds: [19, 2, 13, 7], waitingEmaIds: [] }));
    assert.deepEqual([...result.sourceOf.entries()], kept.map((id) => [id, id]));
    assert.deepEqual(result.removedSlotIds, [2, 7, 13, 19]);
  });

  it("adds a table after a gap: the new slots take the numbers after the highest one", () => {
    const current = slots(20).filter((slot) => ![5, 6, 7, 8].includes(slot.id));
    const ids = [...current.map((slot) => slot.id), 21, 22, 23, 24];
    const result = plan(planPlayerCountChange({ current, isTeams: false, newPlayers: players(ids), removedSlotIds: [], waitingEmaIds: ["W1"] }));
    assert.deepEqual([21, 22, 23, 24].map((id) => result.sourceOf.get(id)), [null, null, null, null]);
    assert.equal(result.sourceOf.get(9), 9);
    assert.deepEqual([...result.fills.entries()], [[21, "W1"]]);
  });

  it("never gives a new person the number of someone who left", () => {
    // 16 of 20 stay, but the draw puts slot 5, which leaves, instead of 9: it is the old way, people move down.
    const ids = range(1, 20).filter((id) => ![9, 10, 11, 12].includes(id));
    const result = plan(planPlayerCountChange({ current: slots(20), isTeams: false, newPlayers: players(ids.slice(0, 16)), removedSlotIds: [5, 6, 7, 8], waitingEmaIds: [] }));
    assert.equal(result.sourceOf.get(5), 9, "a draw that reuses a number that left is read the old way");
  });

  it("removes whole teams: the teams that stay keep their numbers and their four slots", () => {
    const kept = range(1, 24).filter((id) => ![5, 6, 7, 8, 17, 18, 19, 20].includes(id));
    const result = plan(planPlayerCountChange({ current: slots(24, true), isTeams: true, newPlayers: players(kept, teamOf), removedSlotIds: [...range(5, 8), ...range(17, 20)], waitingEmaIds: [] }));
    assert.deepEqual([...result.teamSourceOf.entries()].sort((a, b) => a[0] - b[0]), [[1, 1], [3, 3], [4, 4], [6, 6]]);
    kept.forEach((id) => assert.equal(result.sourceOf.get(id), id));
  });

  it("adds a team after the highest team number", () => {
    const current = slots(24, true).filter((slot) => slot.team !== 2);
    const ids = [...current.map((slot) => slot.id), 25, 26, 27, 28];
    const result = plan(planPlayerCountChange({ current, isTeams: true, newPlayers: players(ids, teamOf), removedSlotIds: [], waitingEmaIds: [] }));
    assert.equal(result.teamSourceOf.get(7), null);
    assert.equal(result.teamSourceOf.get(6), 6);
    assert.equal(result.teamSourceOf.has(2), false);
  });

  it("removes people of a team: their teams stay with the others, incomplete, and keep their numbers", () => {
    const removed = [1, 2, 3, 5];
    const kept = range(1, 24).filter((id) => !removed.includes(id));
    const result = plan(planPlayerCountChange({ current: slots(24, true), isTeams: true, newPlayers: players(kept, teamOf), removedSlotIds: removed, waitingEmaIds: [] }));
    assert.deepEqual([...result.teamSourceOf.entries()].sort((a, b) => a[0] - b[0]), [[1, 1], [2, 2], [3, 3], [4, 4], [5, 5], [6, 6]]);
    kept.forEach((id) => assert.equal(result.sourceOf.get(id), id));
    assert.deepEqual(result.removedSlotIds, removed);
  });

  it("deletes a team whose people all leave, even when they are not one table", () => {
    const removed = [1, 2, 3, 4];
    const kept = range(1, 20).filter((id) => !removed.includes(id));
    const result = plan(planPlayerCountChange({ current: slots(20, true), isTeams: true, newPlayers: players(kept, teamOf), removedSlotIds: removed, waitingEmaIds: [] }));
    assert.equal(result.teamSourceOf.has(1), false);
    assert.equal(result.teamSourceOf.get(2), 2);
  });

  it("still refuses a new team that does not have 4 slots", () => {
    const current = slots(16, true);
    const ids = [...range(1, 16), 17, 18, 19, 20];
    const odd = ids.map((id) => ({ id, team: id === 20 ? 6 : teamOf(id) }));
    assert.equal(code(planPlayerCountChange({ current, isTeams: true, newPlayers: odd, removedSlotIds: [], waitingEmaIds: [] })), "BAD_DRAW");
  });

  it("refuses a team that stays with a slot that is not its own", () => {
    const current = slots(16, true);
    const wrong = [...range(1, 16), 17, 18, 19, 20].map((id) => ({ id, team: id === 17 ? 1 : teamOf(id) }));
    assert.equal(code(planPlayerCountChange({ current, isTeams: true, newPlayers: wrong, removedSlotIds: [], waitingEmaIds: [] })), "BAD_DRAW");
  });
});

describe("names of the moved slots and teams", () => {
  it("renames a default name to the new number and keeps a real name", () => {
    assert.equal(slotNameFor("Player 9", 5), "Player 5");
    assert.equal(slotNameFor("", 5), "Player 5");
    assert.equal(slotNameFor(undefined, 5), "Player 5");
    assert.equal(slotNameFor("  Ana López ", 5), "Ana López");
    assert.equal(slotNameFor("Player 9 B", 5), "Player 9 B");
    assert.equal(teamNameFor("Team 3", 2), "Team 2");
    assert.equal(teamNameFor(null, 2), "Team 2");
    assert.equal(teamNameFor("Dragons", 2), "Dragons");
  });
});
