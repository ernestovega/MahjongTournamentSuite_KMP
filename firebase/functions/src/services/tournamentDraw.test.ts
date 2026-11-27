import assert from "node:assert/strict";
import test from "node:test";
import { drawProblem, meetingLimit, repeatFreeCeiling, repeatFreeMax, repeatFreeSure } from "./tournamentDraw";

// 16 players. Teams are the pairs (1,2), (3,4) and so on.
const players = Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: Math.floor(index / 2) + 1 }));
const rows = () => [0, 1, 2, 3].map((row) => ({ roundId: 1, tableId: row + 1, playerIds: [1, 2, 3, 4].map((c) => row * 4 + c) }));
const columns = () => [1, 2, 3, 4].map((col) => ({ roundId: 2, tableId: col, playerIds: [0, 1, 2, 3].map((r) => r * 4 + col) }));
const validTables = () => [...rows(), ...columns()];

test("a valid draw has no problem", () => {
  assert.equal(drawProblem(players, validTables(), 2, 4), null);
  assert.equal(drawProblem(players, columns().map((t) => ({ ...t, roundId: 1 })), 1, 4, true), null);
});

test("a player in two tables of one round is refused", () => {
  const tables = validTables();
  tables[1].playerIds = [1, 6, 7, 8];
  assert.match(drawProblem(players, tables, 2, 4) ?? "", /plays two tables/);
});

test("a table with a repeated player is refused", () => {
  const tables = validTables();
  tables[0].playerIds = [1, 1, 3, 4];
  assert.match(drawProblem(players, tables, 2, 4) ?? "", /four different players/);
});

test("an unknown player, a duplicate table and a bad round are refused", () => {
  const unknown = validTables();
  unknown[0].playerIds = [1, 2, 3, 99];
  assert.match(drawProblem(players, unknown, 2, 4) ?? "", /unknown player/);
  const duplicate = validTables();
  duplicate[1].tableId = 1;
  assert.match(drawProblem(players, duplicate, 2, 4) ?? "", /twice/);
  const badRound = validTables();
  badRound[2].roundId = 3;
  assert.match(drawProblem(players, badRound, 2, 4) ?? "", /out of range/);
});

test("two players of one team at a table are refused only in a team tournament", () => {
  // Players 1 and 2 share team 1 and sit together in round 1.
  assert.match(drawProblem(players, validTables(), 2, 4, true) ?? "", /same team/);
  assert.equal(drawProblem(players, validTables(), 2, 4, false), null);
});

test("two players who meet in two rounds are refused", () => {
  const tables = validTables();
  tables[4].playerIds = [1, 2, 9, 13];
  tables[5].playerIds = [5, 6, 10, 14];
  tables[6].playerIds = [3, 4, 11, 15];
  tables[7].playerIds = [7, 8, 12, 16];
  assert.match(drawProblem(players, tables, 2, 4) ?? "", /more than one round/);
});

// The same values as RepeatFreeRoundsTest and docs/draw.md, "What the draw finds for sure".
test("the rounds without repeats are the ones the app counts on", () => {
  const sizes: Array<[number, number, number, number, number]> = [
    // players, ceiling, sure, ceiling with teams, sure with teams
    [16, 5, 5, 4, 4],
    [20, 6, 5, 5, 4],
    [24, 7, 5, 6, 4],
    [28, 9, 9, 8, 8],
    [32, 10, 7, 9, 6],
    [36, 11, 8, 10, 7],
    [40, 13, 13, 12, 12],
    [44, 14, 10, 13, 9],
    [48, 15, 11, 14, 10],
  ];
  for (const [n, ceiling, sure, ceilingTeams, sureTeams] of sizes) {
    assert.equal(repeatFreeCeiling(n, false), ceiling, `ceiling ${n}`);
    assert.equal(repeatFreeSure(n, false), sure, `sure ${n}`);
    assert.equal(repeatFreeCeiling(n, true), ceilingTeams, `ceiling ${n} teams`);
    assert.equal(repeatFreeSure(n, true), sureTeams, `sure ${n} teams`);
  }
  assert.equal(repeatFreeMax(20, false), 5);
  assert.equal(repeatFreeMax(20, true), 4);
  assert.equal(repeatFreeMax(24, true), 6);
  // Others: 70 % of the ceiling. 100 players: ceiling 33, sure 23.
  assert.equal(repeatFreeSure(100, false), 23);
  assert.equal(repeatFreeSure(10, false), 0);
});

test("two players meet once while the rounds fit, and at most twice a block begun with more", () => {
  assert.equal(meetingLimit(16, 5, false), 1);
  assert.equal(meetingLimit(24, 4, true), 1);
  // 24 players with teams: blocks of 4 rounds, so 5 rounds are 2 blocks.
  assert.equal(meetingLimit(24, 5, true), 4);
  assert.equal(meetingLimit(16, 6, false), 4);
  assert.equal(meetingLimit(16, 11, false), 6);
});

test("a draw in blocks may repeat a pair, but not more than the blocks allow", () => {
  // 16 players without teams, 6 rounds: more than the 5 that fit. Rounds 1 to 6 alternate the rows and the columns.
  const tables = [1, 2, 3, 4, 5, 6].flatMap((round) => (round % 2 === 1 ? rows() : columns()).map((t) => ({ ...t, roundId: round })));
  // Every pair of a row meets 3 times, within the limit of 4.
  assert.equal(drawProblem(players, tables, 6, 4), null);
  // 6 rounds of the rows: a pair meets 6 times, over the limit of 4 (2 blocks begun).
  const rowsOnly = [1, 2, 3, 4, 5, 6].flatMap((round) => rows().map((t) => ({ ...t, roundId: round })));
  assert.match(drawProblem(players, rowsOnly, 6, 4) ?? "", /more than 4 rounds/);
});

test("a table number beyond the tables of a round is refused", () => {
  const tables = validTables();
  tables[0].tableId = 5;
  assert.match(drawProblem(players, tables, 2, 4) ?? "", /The table of round 1 table 5 is out of range/);
});

test("refuses repeated player ids, reads unknown team players as no team, and counts nothing for odd sizes", () => {
  const four = [1, 2, 3, 4].map((id) => ({ id, team: id }));
  assert.equal(drawProblem([...four, { id: 1, team: 9 }], [], 1, 1), "Two players have the same id.");
  assert.match(String(drawProblem(four, [{ roundId: 1, tableId: 1, playerIds: [1, 2, 3, 9] }], 1, 1, true)), /unknown player 9/);
  assert.equal(repeatFreeCeiling(6, false), 0);
  assert.equal(repeatFreeMax(2, true), 0);
  assert.equal(repeatFreeSure(10, false), 0);
});
