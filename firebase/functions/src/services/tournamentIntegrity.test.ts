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

function valid(): IntegrityInput {
  return {
    tournament: {
      name: "Test", shortName: "T", numPlayers: 4, numRounds: 1, eventStartDate: "2026-10-01", eventEndDate: "2026-10-02",
      hostCity: "Madrid", hostCountry: "ES", venueName: "Hall", venueAddress: "Street 1",
      roundSchedules: [{ roundId: 1, date: "2026-10-01", startTime: "09:00" }], agendaItems: [],
      isTeams: false, isCompleted: false, tablesWithProgress: 0,
      countBestHands: true, countChickenHands: true,
    },
    players: [1, 2, 3, 4].map((id) => ({ id: String(id), data: { id, name: `P${id}`, team: 1, assignedEmaId: `0000000${id}` } })),
    teams: [],
    rounds: [{ id: "1", data: { roundId: 1 } }],
    tables: [{
      id: "1_1",
      data: {
        roundId: 1, tableId: 1, playerIds: [1, 2, 3, 4], version: 0, isCompleted: false, useTotalsOnly: false,
        playerEastId: "", playerSouthId: "", playerWestId: "", playerNorthId: "",
        playerEastScore: "", playerSouthScore: "", playerWestScore: "", playerNorthScore: "",
        playerEastPoints: "", playerSouthPoints: "", playerWestPoints: "", playerNorthPoints: "",
        manualPlayerEastScore: "", manualPlayerSouthScore: "", manualPlayerWestScore: "", manualPlayerNorthScore: "",
        completionStatus: "empty", hasProgress: false, chickenHandCount: 0, bestHandScores: [],
      },
      hands: hands(),
    }],
    registry: new Map([1, 2, 3, 4].map((id) => [`0000000${id}`, { country: "ES" }])),
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
  input.tables.push({ ...input.tables[0], id: "1_2", data: { ...input.tables[0].data, tableId: 2 } });
  input.tournament.numPlayers = 8;
  const found = codes(input);
  assert.ok(found.includes("tables.playerTwice"));
});

test("a wrong tournament completion flag is an error", () => {
  const input = valid();
  input.tournament.isCompleted = true;
  assert.ok(codes(input).includes("counters.isCompleted"));
});
