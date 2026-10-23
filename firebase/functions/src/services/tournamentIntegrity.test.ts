import assert from "node:assert/strict";
import test from "node:test";

import { checkTournamentIntegrity, type IntegrityInput } from "./tournamentIntegrity";

const hand = (handId: number, extra: Record<string, unknown> = {}) => ({
  id: String(handId),
  data: {
    handId, playerWinnerId: "", playerLooserId: "", handScore: "", isChickenHand: false, isDone: false,
    playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", ...extra,
  },
});
const hands = (overrides: Array<ReturnType<typeof hand>> = []) => {
  const list = Array.from({ length: 16 }, (_, index) => hand(index + 1));
  overrides.forEach((item) => { list[Number(item.id) - 1] = item; });
  return list;
};

const ids = Array.from({ length: 16 }, (_, index) => index + 1);
const ema = (id: number) => String(id).padStart(8, "0");

function valid(): IntegrityInput {
  return {
    tournament: {
      name: "Test", shortName: "T", numPlayers: 16, numRounds: 1, eventStartDate: "2026-10-01", eventEndDate: "2026-10-02",
      hostCity: "Madrid", hostCountry: "ES", venueName: "Hall", venueAddress: "Street 1",
      roundSchedules: [{ roundId: 1, date: "2026-10-01", startTime: "09:00" }], agendaItems: [],
      isTeams: false, isCompleted: false, tablesWithProgress: 0,
      countBestHands: true, countChickenHands: true,
    },
    players: ids.map((id) => ({ id: String(id), data: { id, name: `P${id}`, team: 0, assignedEmaId: ema(id) } })),
    teams: [],
    rounds: [{ id: "1", data: { roundId: 1 } }],
    tables: [1, 2, 3, 4].map((tableId) => ({
      id: `1_${tableId}`,
      data: {
        roundId: 1, tableId, playerIds: ids.slice((tableId - 1) * 4, tableId * 4), version: 0, isCompleted: false, useTotalsOnly: false,
        playerEastId: "", playerSouthId: "", playerWestId: "", playerNorthId: "",
        playerEastScore: "", playerSouthScore: "", playerWestScore: "", playerNorthScore: "",
        playerEastPoints: "", playerSouthPoints: "", playerWestPoints: "", playerNorthPoints: "",
        manualPlayerEastScore: "", manualPlayerSouthScore: "", manualPlayerWestScore: "", manualPlayerNorthScore: "",
        completionStatus: "empty", hasProgress: false, chickenHandCount: 0, bestHandScores: [],
      },
      hands: hands(),
    })),
    registry: new Map(ids.map((id) => [ema(id), { country: "ES" }])),
  };
}

const codes = (input: IntegrityInput) => checkTournamentIntegrity("t", input).findings.map((finding) => finding.code);

test("a clean tournament has no errors or warnings", () => {
  const report = checkTournamentIntegrity("t", valid());
  assert.equal(report.counts.errors, 0);
  assert.equal(report.counts.warnings, 0);
});

test("an assigned player that is not in the registry is an error", () => {
  const input = valid();
  input.registry.set("00000002", null);
  assert.ok(codes(input).includes("players.notInRegistry"));
});

test("a duplicate EMA assignment and an unassigned slot are errors", () => {
  const input = valid();
  input.players[1].data.assignedEmaId = "00000001";
  input.players[2].data.assignedEmaId = "";
  const found = codes(input);
  assert.ok(found.includes("players.duplicateEma"));
  assert.ok(found.includes("players.unassigned"));
});

test("saved scores that differ from the hands are an error", () => {
  const input = valid();
  const table = input.tables[0];
  Object.assign(table.data, {
    playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
    playerEastScore: "10", playerSouthScore: "-10", playerWestScore: "0", playerNorthScore: "0",
    isCompleted: true, completionStatus: "completed", hasProgress: true,
  });
  table.hands = hands([hand(1, { playerWinnerId: "1", playerLooserId: "2", handScore: "10", isDone: true })]);
  input.tournament.isCompleted = true;
  input.tournament.tablesWithProgress = 1;
  assert.ok(codes(input).includes("tables.totalsMismatch"));
});

test("a player at two tables in one round is an error", () => {
  const input = valid();
  input.tables[1].data.playerIds = [1, 6, 7, 8];
  const found = codes(input);
  assert.ok(found.includes("tables.playerTwice"));
});

test("a wrong tournament completion flag is an error", () => {
  const input = valid();
  input.tournament.isCompleted = true;
  assert.ok(codes(input).includes("counters.isCompleted"));
});

test("a tournament with fewer than 16 players is an error", () => {
  for (const count of [0, 4, 8, 12]) {
    const input = valid();
    input.tournament.numPlayers = count;
    assert.ok(codes(input).includes("info.numPlayers"), `${count} players`);
  }
});

test("a player count that is not a multiple of 4 is an error", () => {
  const input = valid();
  input.tournament.numPlayers = 18;
  assert.ok(codes(input).includes("info.numPlayers"));
});

test("16 players is the smallest valid tournament", () => {
  assert.ok(!codes(valid()).includes("info.numPlayers"));
});

test("two players of one team at a table is an error in a teams tournament only", () => {
  const input = valid();
  input.players.forEach(({ data }) => { data.team = Math.ceil(Number(data.id) / 4); });
  assert.ok(!codes(input).includes("tables.teamTogether"));
  input.tournament.isTeams = true;
  input.teams = [1, 2, 3, 4].map((id) => ({
    id: String(id), data: { id, name: `Team ${id}`, playerIds: [4 * id - 3, 4 * id - 2, 4 * id - 1, 4 * id] },
  }));
  assert.ok(codes(input).includes("tables.teamTogether"));
});

test("a team number in a tournament without teams is an error", () => {
  const input = valid();
  assert.ok(!codes(input).includes("players.teamUnused"));
  input.players[0].data.team = 1;
  assert.ok(codes(input).includes("players.teamUnused"));
});
