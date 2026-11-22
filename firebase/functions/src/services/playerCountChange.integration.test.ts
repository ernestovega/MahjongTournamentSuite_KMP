import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { newTournamentId, requireEmulator } from "../testing/firestoreFixtures";
import { changePlayerCount } from "./playerCountChange";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { createTournament } from "./tournamentsService";

/**
 * A valid draw of one or two rounds for [count] players: in round 1 table j takes member k of team (j + k) mod T,
 * so no two people of one team meet; round 2 of 16 players takes the columns of round 1.
 */
function drawFor(count: number, isTeams: boolean, numRounds = 1) {
  const tablesPerRound = count / 4;
  const players = Array.from({ length: count }, (_, index) => ({ id: index + 1, team: isTeams ? Math.floor(index / 4) + 1 : 0 }));
  const tables = Array.from({ length: tablesPerRound }, (_, j) => ({
    roundId: 1, tableId: j + 1, playerIds: [0, 1, 2, 3].map((k) => ((j + k) % tablesPerRound) * 4 + k + 1),
  }));
  if (numRounds === 2) {
    if (count !== 16 || isTeams) throw new Error("Round 2 only for 16 players without teams");
    for (let j = 0; j < 4; j++) tables.push({ roundId: 2, tableId: j + 1, playerIds: [0, 1, 2, 3].map((k) => k * 4 + j + 1) });
  }
  return { players, tables };
}

async function create(count: number, options: { isTeams?: boolean; numRounds?: number; registration?: Record<string, unknown> } = {}) {
  const isTeams = options.isTeams ?? false;
  const numRounds = options.numRounds ?? 1;
  const draw = drawFor(count, isTeams, numRounds);
  const tournament = await createTournament({
    name: `Count ${newTournamentId()}`, shortName: "CNT", organizerIds: ["org-es"], eventStartDate: "2027-02-01", eventEndDate: "2027-02-02",
    hostCountry: "ES", hostCity: "Madrid", isTeams, numPlayers: count, numRounds, numTries: 1,
    players: draw.players, tables: draw.tables, createdByUid: "u-test",
    roundSchedules: Array.from({ length: numRounds }, (_, index) => ({ roundId: index + 1, date: "2027-02-01", startTime: `${10 + index}:00` })),
  });
  const ref = db.collection("tournaments").doc(tournament.id);
  if (options.registration != null) await ref.update({ registration: options.registration });
  return { id: tournament.id, ref };
}

async function assign(ref: FirebaseFirestore.DocumentReference, slotId: number, emaId: string, extra: Record<string, unknown> = {}) {
  await ref.collection("players").doc(String(slotId)).update({ assignedEmaId: emaId, name: `Person ${emaId}`, country: "ES", ...extra });
  await ref.collection("emaPlayerAssignments").doc(emaId).set({ playerId: slotId });
}

async function slotsOf(ref: FirebaseFirestore.DocumentReference) {
  const docs = await ref.collection("players").get();
  return new Map(docs.docs.map((doc) => [Number(doc.id), doc.data()]));
}

async function expectConflict(promise: Promise<unknown>, code: string) {
  await assert.rejects(promise, (error: unknown) => error instanceof HttpError && (error.details as { code?: string } | undefined)?.code === code);
}

const run = (id: string, count: number, options: { isTeams?: boolean; removed?: number[]; waiting?: string[]; numRounds?: number } = {}) => {
  const draw = drawFor(count, options.isTeams ?? false, options.numRounds ?? 1);
  return changePlayerCount({
    tournamentId: id, numTries: 7, players: draw.players, tables: draw.tables,
    removedSlotIds: options.removed ?? [], waitingEmaIds: options.waiting ?? [], uid: "u-admin",
  });
};

describe("change of the number of players", () => {
  before(requireEmulator);

  it("removes a table: the people who stay keep their data and move to the lowest slots", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(20);
    await assign(ref, 1, `1${tag}1`, { email: "ana@example.com" });
    await assign(ref, 6, `1${tag}6`);
    await assign(ref, 9, `1${tag}9`, { substitution: { reason: "ILLNESS", fromRound: 1 } });
    await assign(ref, 20, `1${tag}0`);

    const result = await run(id, 16, { removed: [5, 6, 7, 8] });

    assert.equal(result.tournament.numPlayers, 16);
    assert.equal(result.previousPlayers, 20);
    assert.deepEqual(result.removed.map((person) => person.slotId), [5, 6, 7, 8]);
    assert.equal(result.removed[1].emaId, `1${tag}6`);
    const slots = await slotsOf(ref);
    assert.deepEqual([...slots.keys()].sort((a, b) => a - b), Array.from({ length: 16 }, (_, index) => index + 1));
    assert.equal(slots.get(1)?.assignedEmaId, `1${tag}1`);
    assert.equal(slots.get(1)?.email, "ana@example.com");
    assert.equal(slots.get(5)?.assignedEmaId, `1${tag}9`);
    assert.equal(slots.get(5)?.name, `Person 1${tag}9`);
    assert.equal(slots.get(5)?.substitution, undefined);
    assert.equal(slots.get(16)?.assignedEmaId, `1${tag}0`);
    // A slot without a person keeps a default name that follows its number: old slot 10 is now slot 6.
    assert.equal(slots.get(6)?.name, "Player 6");
    assert.equal(slots.get(6)?.assignedEmaId ?? null, null);

    const assignments = await ref.collection("emaPlayerAssignments").get();
    assert.deepEqual(Object.fromEntries(assignments.docs.map((doc) => [doc.id, doc.get("playerId")])), {
      [`1${tag}1`]: 1, [`1${tag}9`]: 5, [`1${tag}0`]: 16,
    });

    const tables = await ref.collection("tables").get();
    assert.deepEqual(tables.docs.map((doc) => doc.id).sort(), ["1_1", "1_2", "1_3", "1_4"]);
    assert.equal((await ref.collection("tables").doc("1_5").collection("hands").get()).size, 0);
    assert.equal((await ref.collection("tables").doc("1_1").collection("hands").get()).size, 16);
    // A table that keeps its key raises its version.
    assert.equal(tables.docs.find((doc) => doc.id === "1_1")?.get("version"), 1);
    const tournament = await ref.get();
    assert.equal(tournament.get("numTries"), 7);
    assert.equal(tournament.get("dataVersions.tableVersions.1_1") ?? tournament.get("dataVersions")?.tableVersions?.["1_1"], 1);
    assert.ok(Number(tournament.get("dataVersions.players.revision")) >= 2);

    const log = await ref.collection("playerLog").get();
    const kinds = log.docs.map((doc) => doc.get("kind")).sort();
    assert.deepEqual(kinds, ["clear", "playerCount"]);
    assert.deepEqual(log.docs.find((doc) => doc.get("kind") === "playerCount")?.get("next"), { numPlayers: 16 });
  });

  it("adds a table with empty slots and keeps every person in its slot", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(16, { numRounds: 2 });
    await assign(ref, 3, `2${tag}3`);
    const before = await ref.get();

    const result = await changePlayerCount({ tournamentId: id, numTries: 3, ...drawFor(20, false), removedSlotIds: [], waitingEmaIds: [], uid: null })
      .catch((error) => error);
    // Two rounds need a draw of two rounds.
    assert.ok(result instanceof HttpError);
    await ref.update({ numRounds: 1 });
    await ref.collection("tables").doc("2_1").delete();

    const grown = await run(id, 20);
    assert.equal(grown.tournament.numPlayers, 20);
    const slots = await slotsOf(ref);
    assert.equal(slots.size, 20);
    assert.equal(slots.get(3)?.assignedEmaId, `2${tag}3`);
    assert.deepEqual([17, 18, 19, 20].map((slot) => slots.get(slot)?.name), ["Player 17", "Player 18", "Player 19", "Player 20"]);
    assert.equal(slots.get(17)?.assignedEmaId ?? null, null);
    assert.equal((await ref.collection("tables").get()).size, 5);
    assert.equal((await ref.collection("tables").doc("1_5").get()).get("version"), 0);
    assert.notDeepEqual(before.get("dataVersions.tables"), (await ref.get()).get("dataVersions.tables"));
  });

  it("refuses a tournament with table data, without changing anything", async () => {
    const { id, ref } = await create(20);
    await ref.update({ tablesWithProgress: 1 });
    await expectConflict(run(id, 16, { removed: [1, 2, 3, 4] }), "TOURNAMENT_STARTED");

    const second = await create(20);
    await second.ref.collection("tables").doc("1_3").update({ hasProgress: true });
    await expectConflict(run(second.id, 16, { removed: [1, 2, 3, 4] }), "TOURNAMENT_STARTED");
    assert.equal((await slotsOf(second.ref)).size, 20);
    assert.equal((await second.ref.get()).get("numPlayers"), 20);
  });

  it("refuses a finished tournament, one without a draw and one that does not exist", async () => {
    const finished = await create(20);
    await finished.ref.update({ isCompleted: true });
    await expectConflict(run(finished.id, 16, { removed: [1, 2, 3, 4] }), "TOURNAMENT_COMPLETED");
    const pending = await create(16);
    await pending.ref.update({ drawPending: true });
    await expectConflict(run(pending.id, 20), "DRAW_PENDING");
    await assert.rejects(run(newTournamentId(), 20), (error: unknown) => error instanceof HttpError && error.status === 404);
  });

  it("refuses a wrong draw or wrong removed slots", async () => {
    const { id, ref } = await create(20);
    const draw = drawFor(16, false);
    await assert.rejects(changePlayerCount({
      tournamentId: id, numTries: 1, players: draw.players, tables: draw.tables.slice(1), removedSlotIds: [1, 2, 3, 4], waitingEmaIds: [], uid: null,
    }), (error: unknown) => error instanceof HttpError && error.status === 400);
    const repeated = drawFor(16, false);
    repeated.tables[0].playerIds = [1, 1, 2, 3];
    await assert.rejects(changePlayerCount({
      tournamentId: id, numTries: 1, ...repeated, removedSlotIds: [1, 2, 3, 4], waitingEmaIds: [], uid: null,
    }), (error: unknown) => error instanceof HttpError && error.status === 400);
    await expectConflict(run(id, 16, { removed: [1, 2, 3] }), "BAD_REMOVED_SLOTS");
    await expectConflict(run(id, 20), "SAME_PLAYER_COUNT");
    await expectConflict(run(id, 24, { waiting: ["12345678"] }), "NO_REGISTRATIONS");
    assert.equal((await slotsOf(ref)).size, 20);
  });
});

describe("change of the number of players with teams", () => {
  before(requireEmulator);

  it("removes a whole team: the other teams keep their names and people, default names follow the number", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(20, { isTeams: true });
    await ref.collection("teams").doc("1").update({ name: "Dragons" });
    await ref.collection("teams").doc("3").update({ name: "Tigers" });
    await assign(ref, 9, `3${tag}9`);
    await assign(ref, 6, `3${tag}6`);

    await run(id, 16, { isTeams: true, removed: [5, 6, 7, 8] });

    const teams = await ref.collection("teams").get();
    assert.deepEqual(Object.fromEntries(teams.docs.map((doc) => [doc.id, doc.get("name")])), {
      1: "Dragons", 2: "Tigers", 3: "Team 3", 4: "Team 4",
    });
    const slots = await slotsOf(ref);
    assert.equal(slots.get(5)?.assignedEmaId, `3${tag}9`);
    assert.equal(slots.get(5)?.team, 2);
    assert.ok([...slots.values()].every((slot) => slot.assignedEmaId !== `3${tag}6`));
    assert.equal((await ref.collection("emaPlayerAssignments").doc(`3${tag}6`).get()).exists, false);
  });

  it("refuses part of a team and adds a new empty team", async () => {
    const { id, ref } = await create(16, { isTeams: true });
    const big = await create(20, { isTeams: true });
    await expectConflict(run(big.id, 16, { isTeams: true, removed: [4, 5, 6, 7] }), "PARTIAL_TEAM");

    await run(id, 20, { isTeams: true });
    const teams = await ref.collection("teams").get();
    assert.equal(teams.size, 5);
    assert.equal((await ref.collection("teams").doc("5").get()).get("name"), "Team 5");
    const slots = await slotsOf(ref);
    assert.deepEqual([17, 18, 19, 20].map((slot) => slots.get(slot)?.team), [5, 5, 5, 5]);
  });
});

describe("change of the number of players of a tournament with registrations", () => {
  before(requireEmulator);

  const registration = { status: "CLOSED", maxPlayers: 20, closesOn: null, slug: "", confirmed: 20, waiting: 0 };

  async function register(ref: FirebaseFirestore.DocumentReference, emaId: string, status: "CONFIRMED" | "WAITING", teamId: string | null = null) {
    await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).set({ firstName: "Reg", lastName: emaId, country: "pt" });
    await ref.collection("registrations").doc(emaId).set({
      name: `Reg ${emaId}`, country: "PT", email: "", status, order: Number(emaId), paid: false, teamId, language: "en", source: "ADMIN", manageToken: "t",
    });
  }

  it("removes the registrations of the people who leave, without an email, and lowers the places", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(20, { registration });
    const leaving = `4${tag}5`;
    const staying = `4${tag}1`;
    await register(ref, leaving, "CONFIRMED");
    await register(ref, staying, "CONFIRMED");
    await assign(ref, 5, leaving);
    await assign(ref, 1, staying);

    await run(id, 16, { removed: [5, 6, 7, 8] });

    assert.equal((await ref.collection("registrations").doc(leaving).get()).exists, false);
    assert.equal((await ref.collection("registrations").doc(staying).get()).get("status"), "CONFIRMED");
    const log = await ref.collection("registrationLog").get();
    assert.deepEqual(log.docs.map((doc) => [doc.get("kind"), doc.get("emaId")]), [["removed", leaving]]);
    const tournament = await ref.get();
    assert.equal(tournament.get("registration.maxPlayers"), 16);
    assert.equal(tournament.get("registration.confirmed"), 1);
  });

  it("gives a new slot to people of the waiting list", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(16, { registration: { ...registration, maxPlayers: 16 } });
    const first = `5${tag}1`;
    const second = `5${tag}2`;
    const notWaiting = `5${tag}3`;
    await register(ref, first, "WAITING");
    await register(ref, second, "WAITING");
    await register(ref, notWaiting, "CONFIRMED");

    await expectConflict(run(id, 20, { waiting: [notWaiting] }), "NOT_WAITING");
    const result = await run(id, 20, { waiting: [first, second] });

    assert.deepEqual(result.promoted, [first, second]);
    const slots = await slotsOf(ref);
    assert.equal(slots.get(17)?.assignedEmaId, first);
    assert.equal(slots.get(17)?.name, `Reg ${first}`);
    assert.equal(slots.get(17)?.assignedCountry, "PT");
    assert.equal(slots.get(18)?.assignedEmaId, second);
    assert.equal(slots.get(19)?.assignedEmaId ?? null, null);
    assert.equal((await ref.collection("emaPlayerAssignments").doc(first).get()).get("playerId"), 17);
    assert.equal((await ref.collection("registrations").doc(first).get()).get("status"), "CONFIRMED");
    const tournament = await ref.get();
    assert.equal(tournament.get("registration.maxPlayers"), 20);
    assert.equal(tournament.get("registration.waiting"), 0);
    const log = await ref.collection("playerLog").get();
    assert.equal(log.docs.filter((doc) => doc.get("kind") === "replace").length, 2);
  });

  it("names a new team after the registration team of its four people", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(16, { isTeams: true, registration: { ...registration, maxPlayers: null } });
    await ref.collection("registrationTeams").doc("rt1").set({ name: "Late Lions", captainEmaId: `6${tag}1` });
    const people = [1, 2, 3, 4].map((index) => `6${tag}${index}`);
    for (const emaId of people) await register(ref, emaId, "WAITING", "rt1");

    await run(id, 20, { isTeams: true, waiting: people });

    assert.equal((await ref.collection("teams").doc("5").get()).get("name"), "Late Lions");
    const slots = await slotsOf(ref);
    assert.deepEqual([17, 18, 19, 20].map((slot) => slots.get(slot)?.assignedEmaId), people);
    assert.equal((await ref.get()).get("registration.maxPlayers") ?? null, null);
  });
});
