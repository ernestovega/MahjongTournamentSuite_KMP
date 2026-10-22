import assert from "node:assert/strict";
import test from "node:test";
import { drawProblem } from "./tournamentDraw";

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
