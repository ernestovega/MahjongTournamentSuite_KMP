import assert from "node:assert/strict";
import test from "node:test";

import { checkTournamentIntegrity, type IntegrityInput } from "./tournamentIntegrity";
import { handsCopyUpdate } from "./tableManagerService";

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

test("slot numbers with gaps, after tables left, are not a finding", () => {
  // Slot 3 left and slot 17 came: a change of the number of players keeps the numbers of the people who stay.
  const input = valid();
  const renumber = (id: number) => (id === 3 ? 17 : id);
  input.players = input.players.map(({ id, data }) => (Number(id) === 3 ? { id: "17", data: { ...data, id: 17 } } : { id, data }));
  input.tables.forEach((table) => { table.data.playerIds = (table.data.playerIds as number[]).map(renumber); });
  input.registry.set(ema(3), { country: "ES" });
  assert.deepEqual(codes(input).filter((code) => code.startsWith("players.") || code.startsWith("tables.")), []);
  input.players[0] = { id: "0", data: { ...input.players[0].data, id: 0 } };
  assert.ok(codes(input).includes("players.idRange"));
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
    id: String(id), data: { id, name: `Team ${id}` },
  }));
  assert.ok(codes(input).includes("tables.teamTogether"));
});

test("a team number in a tournament without teams is an error", () => {
  const input = valid();
  assert.ok(!codes(input).includes("players.teamUnused"));
  input.players[0].data.team = 1;
  assert.ok(codes(input).includes("players.teamUnused"));
});

test("a round without a date or a start time is a warning", () => {
  const schedules = (rows: unknown[]) => {
    const input = valid();
    input.tournament.roundSchedules = rows;
    return checkTournamentIntegrity("t", input).findings.filter((finding) => finding.code.startsWith("schedule."));
  };

  assert.deepEqual(schedules([{ roundId: 1, date: null, startTime: null }]).map((f) => [f.severity, f.code]), [["warning", "schedule.empty"]]);
  assert.deepEqual(schedules([{ roundId: 1, date: "2026-10-01", startTime: null }]).map((f) => [f.severity, f.code]), [["warning", "schedule.incomplete"]]);
  assert.deepEqual(schedules([{ roundId: 1, date: null, startTime: "09:00" }]).map((f) => [f.severity, f.code]), [["warning", "schedule.incomplete"]]);
  assert.deepEqual(schedules([{ roundId: 1, date: "2026-10-01", startTime: "09:00" }]), []);
});

test("a copy of the hands that differs from the hand documents is an error, and an old copy is ignored", () => {
  const input = valid();
  const table = input.tables[0];
  Object.assign(table.data, handsCopyUpdate(0, table.hands.map((item) => item.data)));
  assert.ok(!codes(input).includes("counters.handsCopy"), "an equal copy is fine");

  table.hands[0].data.handScore = "8";
  assert.ok(codes(input).includes("counters.handsCopy"));

  table.data.version = 1;
  assert.ok(!codes(input).includes("counters.handsCopy"), "a copy of an older version is not used, so it is not checked");
});

test("the data of the tournament, its schedule and its agenda are checked", () => {
  const input = valid();
  Object.assign(input.tournament, {
    hostCountry: "Spain",
    venueMapUrl: "http://maps.test/place",
    roundSchedules: [
      { roundId: 1, date: "2026-13-45", startTime: "9h" },
      { roundId: 2, date: "2026-12-01", startTime: "10:00" },
      { roundId: 3, date: "2026-10-01", startTime: "08:00" },
    ],
    agendaItems: [
      { title: "", date: "2026-02-31", startTime: "25:99" },
      { title: "Lunch", date: "2026-12-24", startTime: "" },
    ],
  });
  const found = codes(input);
  for (const code of ["info.hostCountry", "info.venueMapUrl", "schedule.date", "schedule.time", "schedule.range", "agenda.title", "agenda.date", "agenda.time", "agenda.range"]) {
    assert.ok(found.includes(code), `${code} in ${found.join(", ")}`);
  }

  Object.assign(input.tournament, { venueMapUrl: "not a link" });
  assert.ok(checkTournamentIntegrity("t", input).findings.some((f) => f.code === "info.venueMapUrl" && f.message.includes("not a valid")));
});

test("the slots, the teams and the rounds are checked", () => {
  const input = valid();
  input.tournament.isTeams = true;
  input.players[0] = { id: "1", data: { id: 99, name: "", team: 1, assignedEmaId: "123", assignedCountry: "FR" } };
  input.players[1] = { id: "2", data: { id: 2, name: "P2", team: 0, assignedEmaId: ema(2), assignedCountry: "FR" } };
  input.players[2] = { id: "3", data: { id: 2, name: "P3", team: 2, assignedEmaId: ema(3) } };
  input.players.slice(3).forEach((slot) => { (slot.data as Record<string, unknown>).team = 1; });
  input.teams = [
    { id: "1", data: { id: 1, name: "", playerIds: [1, 2] } },
    { id: "3", data: { id: 3, name: "Empty" } },
  ];
  input.rounds = [{ id: "1", data: { roundId: 1 } }, { id: "1b", data: { roundId: 1 } }];
  const found = codes(input);
  for (const code of [
    "players.id", "players.duplicateId", "players.name", "players.emaFormat", "players.country", "players.team",
    "teams.missing", "teams.name", "teams.playerIds", "teams.empty", "teams.sizes", "rounds.count", "rounds.duplicate",
  ]) {
    assert.ok(found.includes(code), `${code} in ${found.join(", ")}`);
  }

  const noRounds = valid();
  noRounds.rounds = [];
  assert.ok(codes(noRounds).includes("rounds.missing"));

  const teamsWithoutMode = valid();
  teamsWithoutMode.teams = [{ id: "1", data: { id: 1, name: "Wind" } }];
  assert.ok(codes(teamsWithoutMode).includes("teams.unused"));
});

test("after 25 findings of one kind the rest are only counted", () => {
  const input = valid();
  input.players.forEach((slot) => { (slot.data as Record<string, unknown>).name = ""; });
  const many = { ...input, players: [...input.players, ...Array.from({ length: 20 }, (_, index) => ({ id: String(100 + index), data: { id: 100 + index, name: "", team: 0 } }))] };

  const report = checkTournamentIntegrity("t", many);
  const names = report.findings.filter((finding) => finding.code === "players.name");
  assert.equal(names.length, 26);
  assert.ok(names.some((finding) => finding.message.includes("more findings of this kind are not listed")));
});

test("documents without their numbers and tables with broken data are each reported", () => {
  const input = valid();
  delete input.tournament.tablesWithProgress;
  input.players = input.players.map(({ id, data }) => ({ id, data: { ...data, id: undefined } }));
  input.rounds = [{ id: "1", data: {} }];
  const seats = { playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4" };
  const [first, second, third, fourth] = input.tables;
  // A document whose id is not its round and table, in a round that does not exist, and a copy of it.
  first.id = "9_9";
  first.data = { ...first.data, roundId: 3, version: "abc", ...seats, chickenHandCount: 5 };
  first.hands = [
    hand(1, { isDone: true }),
    hand(2, { handId: 3, playerWinnerId: "9", playerLooserId: "8", handScore: "8" }),
    hand(4, { playerWinnerId: "1", playerLooserId: "-", handScore: "300", playerEastPenalty: "abc", playerSouthPenalty: "5" }),
    hand(5, { playerWinnerId: "2", playerLooserId: "2", handScore: "8" }),
    hand(6, { playerWinnerId: "1", playerLooserId: "3", handScore: "8", isDone: true }),
    hand(6),
  ];
  second.id = "9_9";
  // A completed table without seats or totals, and a table without players that uses manual totals.
  third.data = { ...third.data, isCompleted: true };
  fourth.data = { ...fourth.data, playerIds: undefined, version: undefined, useTotalsOnly: true };
  fourth.hands = hands([hand(1, { playerWinnerId: "13", playerLooserId: "14", handScore: "8" })]);

  const found = new Set(codes(input));
  for (const code of [
    "tables.id", "tables.duplicate", "tables.round", "tables.version", "tables.playerIds", "tables.completedWithoutSeats",
    "hands.count", "hands.id", "hands.duplicate", "hands.doneEmpty", "hands.winnerSeat", "hands.loserSeat", "hands.sameWinnerLoser",
    "hands.highScore", "hands.penaltyText", "hands.positivePenalty", "hands.gap", "tables.completedWithoutTotals", "tables.handsIgnored",
    "tables.chickenCount", "counters.tablesWithProgress",
  ]) assert.ok(found.has(code), `${code} in ${[...found].join(", ")}`);
});

test("two tables with the same four players are repeated opponents", () => {
  const input = valid();
  input.tournament.numRounds = 2;
  input.rounds.push({ id: "2", data: { roundId: 2 } });
  input.tournament.roundSchedules = [...(input.tournament.roundSchedules as unknown[]), { roundId: 2, date: "2026-10-02", startTime: "09:00" }];
  input.tables.push(...input.tables.map((table) => ({ ...table, id: `2_${table.data.tableId}`, data: { ...table.data, roundId: 2 } })));
  assert.ok(codes(input).includes("tables.repeatOpponents"));
});
