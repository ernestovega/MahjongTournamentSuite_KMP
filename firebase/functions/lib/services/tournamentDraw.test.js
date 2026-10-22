"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentDraw_1 = require("./tournamentDraw");
// 16 players. Teams are the pairs (1,2), (3,4) and so on.
const players = Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: Math.floor(index / 2) + 1 }));
const rows = () => [0, 1, 2, 3].map((row) => ({ roundId: 1, tableId: row + 1, playerIds: [1, 2, 3, 4].map((c) => row * 4 + c) }));
const columns = () => [1, 2, 3, 4].map((col) => ({ roundId: 2, tableId: col, playerIds: [0, 1, 2, 3].map((r) => r * 4 + col) }));
const validTables = () => [...rows(), ...columns()];
(0, node_test_1.default)("a valid draw has no problem", () => {
    strict_1.default.equal((0, tournamentDraw_1.drawProblem)(players, validTables(), 2, 4), null);
    strict_1.default.equal((0, tournamentDraw_1.drawProblem)(players, columns().map((t) => ({ ...t, roundId: 1 })), 1, 4, true), null);
});
(0, node_test_1.default)("a player in two tables of one round is refused", () => {
    const tables = validTables();
    tables[1].playerIds = [1, 6, 7, 8];
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, tables, 2, 4) ?? "", /plays two tables/);
});
(0, node_test_1.default)("a table with a repeated player is refused", () => {
    const tables = validTables();
    tables[0].playerIds = [1, 1, 3, 4];
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, tables, 2, 4) ?? "", /four different players/);
});
(0, node_test_1.default)("an unknown player, a duplicate table and a bad round are refused", () => {
    const unknown = validTables();
    unknown[0].playerIds = [1, 2, 3, 99];
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, unknown, 2, 4) ?? "", /unknown player/);
    const duplicate = validTables();
    duplicate[1].tableId = 1;
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, duplicate, 2, 4) ?? "", /twice/);
    const badRound = validTables();
    badRound[2].roundId = 3;
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, badRound, 2, 4) ?? "", /out of range/);
});
(0, node_test_1.default)("two players of one team at a table are refused only in a team tournament", () => {
    // Players 1 and 2 share team 1 and sit together in round 1.
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, validTables(), 2, 4, true) ?? "", /same team/);
    strict_1.default.equal((0, tournamentDraw_1.drawProblem)(players, validTables(), 2, 4, false), null);
});
(0, node_test_1.default)("two players who meet in two rounds are refused", () => {
    const tables = validTables();
    tables[4].playerIds = [1, 2, 9, 13];
    tables[5].playerIds = [5, 6, 10, 14];
    tables[6].playerIds = [3, 4, 11, 15];
    tables[7].playerIds = [7, 8, 12, 16];
    strict_1.default.match((0, tournamentDraw_1.drawProblem)(players, tables, 2, 4) ?? "", /more than one round/);
});
//# sourceMappingURL=tournamentDraw.test.js.map