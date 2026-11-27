import assert from "node:assert/strict";
import { before, describe, it, mock } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { newTournamentId, requireEmulator } from "../testing/firestoreFixtures";
import { setMailTransportForTests, type OutgoingEmail } from "./mailService";
import { getTournamentManifest } from "./dataVersionsService";
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
    // A new draw: the apps drop the tables they hold and read them all again.
    const manifest = await getTournamentManifest(id);
    assert.equal(manifest.resources.draw.revision, 1);
  });

  it("writes the old draw back when a write of the change fails", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(16);
    await assign(ref, 3, `3${tag}3`);
    // The batch that writes the log fails, after the new slots and the new table are written.
    const batch = db.batch.bind(db);
    const failing = mock.method(db, "batch", () => {
      const real = batch();
      let writesLog = false;
      const set = real.set.bind(real) as (...args: unknown[]) => FirebaseFirestore.WriteBatch;
      (real as { set: unknown }).set = (docRef: FirebaseFirestore.DocumentReference, ...rest: unknown[]) => {
        if (docRef.parent.id === "playerLog") writesLog = true;
        return set(docRef, ...rest);
      };
      const commit = real.commit.bind(real);
      real.commit = () => (writesLog ? Promise.reject(new Error("log unavailable")) : commit());
      return real;
    });
    try {
      await assert.rejects(run(id, 20), /log unavailable/);
    } finally {
      failing.mock.restore();
    }

    const slots = await slotsOf(ref);
    assert.equal(slots.size, 16);
    assert.equal(slots.get(3)?.assignedEmaId, `3${tag}3`);
    const tables = await ref.collection("tables").get();
    assert.deepEqual(tables.docs.map((doc) => doc.id).sort(), ["1_1", "1_2", "1_3", "1_4"]);
    assert.equal((await ref.collection("tables").doc("1_1").collection("hands").get()).size, 16);
    assert.equal((await ref.get()).get("numPlayers"), 16);
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

/** A valid one round draw on the slots [ids] (any numbers), as the admin app sends it since it keeps the numbers. */
function drawOn(ids: number[], teamOf: (id: number) => number = () => 0) {
  const sorted = [...ids].sort((a, b) => a - b);
  const players = sorted.map((id) => ({ id, team: teamOf(id) }));
  const teams = new Map<number, number[]>();
  players.forEach((player) => teams.set(player.team, [...(teams.get(player.team) ?? []), player.id]));
  const tablesPerRound = sorted.length / 4;
  const byTeam = [...teams.entries()].sort((a, b) => a[0] - b[0]).map(([, members]) => members);
  const tables = Array.from({ length: tablesPerRound }, (_, j) => ({
    roundId: 1,
    tableId: j + 1,
    // With teams, member k of team (j + k) mod T; without, four numbers in a row.
    playerIds: teamOf(sorted[0]) > 0 ? [0, 1, 2, 3].map((k) => byTeam[(j + k) % tablesPerRound][k]) : sorted.slice(j * 4, j * 4 + 4),
  }));
  return { players, tables };
}

describe("change of the number of players that keeps the numbers of the people who stay", () => {
  before(requireEmulator);
  const teamOf = (id: number) => Math.floor((id - 1) / 4) + 1;
  const change = (id: string, draw: ReturnType<typeof drawOn>, removed: number[] = [], waiting: string[] = []) => changePlayerCount({
    tournamentId: id, numTries: 3, players: draw.players, tables: draw.tables, removedSlotIds: removed, waitingEmaIds: waiting, uid: "u-admin",
  });

  it("removes slots from the middle: everybody who stays keeps their slot, and a later table comes after the highest", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(20);
    await assign(ref, 9, `5${tag}9`, { email: "nine@example.com" });
    await assign(ref, 6, `5${tag}6`);
    await assign(ref, 20, `5${tag}0`);
    const kept = Array.from({ length: 20 }, (_, index) => index + 1).filter((slot) => ![3, 6, 11, 14].includes(slot));

    const sent: OutgoingEmail[] = [];
    setMailTransportForTests(async (email) => { sent.push(email); });
    try {
      await change(id, drawOn(kept), [3, 6, 11, 14]);
    } finally {
      setMailTransportForTests(null);
    }

    const slots = await slotsOf(ref);
    assert.deepEqual([...slots.keys()].sort((a, b) => a - b), kept);
    assert.equal(slots.get(9)?.assignedEmaId, `5${tag}9`);
    assert.equal(slots.get(9)?.email, "nine@example.com");
    assert.equal(slots.get(20)?.assignedEmaId, `5${tag}0`);
    assert.equal(slots.get(10)?.name, "Player 10");
    const assignments = await ref.collection("emaPlayerAssignments").get();
    assert.deepEqual(Object.fromEntries(assignments.docs.map((doc) => [doc.id, doc.get("playerId")])), { [`5${tag}9`]: 9, [`5${tag}0`]: 20 });
    assert.equal((await ref.get()).get("numPlayers"), 16);
    // The email says the number stays.
    assert.match(sent.find((email) => email.to === "nine@example.com")?.text ?? "", /sigue siendo el 9\./);

    // A table more: slots 21 to 24, not the gaps.
    await change(id, drawOn([...kept, 21, 22, 23, 24]));
    const more = await slotsOf(ref);
    assert.deepEqual([...more.keys()].sort((a, b) => a - b), [...kept, 21, 22, 23, 24]);
    assert.equal(more.get(9)?.assignedEmaId, `5${tag}9`);
    assert.equal(more.get(21)?.name, "Player 21");
    assert.equal((await ref.get()).get("numPlayers"), 20);
  });

  it("refuses teams left incomplete, and keeps the old draw", async () => {
    const { id, ref } = await create(24, { isTeams: true });
    const removed = [1, 2, 3, 5];
    const kept = Array.from({ length: 24 }, (_, index) => index + 1).filter((slot) => !removed.includes(slot));
    // A valid draw otherwise: teammates never share a table, but team 1 keeps only the 4 and team 2 keeps 6, 7 and 8.
    const tables = [[4, 6, 9, 13], [7, 14, 17, 21], [8, 10, 18, 22], [11, 15, 19, 23], [12, 16, 20, 24]]
      .map((playerIds, index) => ({ roundId: 1, tableId: index + 1, playerIds }));

    await expectConflict(change(id, { players: kept.map((slot) => ({ id: slot, team: teamOf(slot) })), tables }, removed), "PARTIAL_TEAM");

    assert.equal((await slotsOf(ref)).size, 24);
    assert.equal((await ref.collection("teams").get()).size, 6);
  });

  it("moves a person who stays to another team to complete it: same slot, new team, the empty team goes", async () => {
    const { id, ref } = await create(24, { isTeams: true });
    const removed = [1, 2, 3, 5];
    const kept = Array.from({ length: 24 }, (_, index) => index + 1).filter((slot) => !removed.includes(slot));
    const moved = (slot: number) => (slot === 4 ? 2 : teamOf(slot));
    await change(id, drawOn(kept, moved), removed);
    const slots = await slotsOf(ref);
    assert.equal(slots.get(4)?.team, 2);
    assert.equal((await ref.collection("teams").doc("1").get()).exists, false);
    assert.equal((await ref.collection("teams").get()).size, 5);
  });

  it("removes a whole team: the others keep their team numbers, names and slots", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(24, { isTeams: true });
    await ref.collection("teams").doc("3").update({ name: "Tigers" });
    await assign(ref, 10, `6${tag}0`);
    const kept = Array.from({ length: 24 }, (_, index) => index + 1).filter((slot) => slot < 5 || slot > 8);

    await change(id, drawOn(kept, teamOf), [5, 6, 7, 8]);

    const teams = await ref.collection("teams").get();
    assert.deepEqual(Object.fromEntries(teams.docs.map((doc) => [doc.id, doc.get("name")])), {
      1: "Team 1", 3: "Tigers", 4: "Team 4", 5: "Team 5", 6: "Team 6",
    });
    const slots = await slotsOf(ref);
    assert.equal(slots.get(10)?.assignedEmaId, `6${tag}0`);
    assert.equal(slots.get(10)?.team, 3);
    assert.deepEqual([...slots.keys()].sort((a, b) => a - b), kept);

    // A new team after the highest: team 7 with slots 25 to 28.
    await change(id, drawOn([...kept, 25, 26, 27, 28], teamOf));
    assert.equal((await ref.collection("teams").doc("7").get()).get("name"), "Team 7");
    assert.equal((await ref.collection("teams").doc("2").get()).exists, false);
    const more = await slotsOf(ref);
    assert.deepEqual([25, 26, 27, 28].map((slot) => more.get(slot)?.team), [7, 7, 7, 7]);
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

describe("the emails of a change of the number of players", () => {
  before(requireEmulator);

  it("tells every person affected: who stays, with the new number, who leaves and who gets a place", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(20, { registration: { status: "CLOSED", maxPlayers: 20, closesOn: null, slug: "", confirmed: 2, waiting: 0 } });
    await ref.update({ contactEmail: "org@example.com" });
    const stays = `7${tag}9`;
    const moves = `7${tag}1`;
    const leaves = `7${tag}5`;
    const noAddress = `7${tag}6`;
    await assign(ref, 2, stays, { email: "stays@example.com" });
    await assign(ref, 9, moves, { email: "moves@example.com" });
    await assign(ref, 5, leaves, { email: "leaves@example.com" });
    await assign(ref, 6, noAddress);
    // The organization is in Spain: Spanish, except for a person who registered in another language.
    await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(moves).set({ firstName: "Reg", lastName: moves, country: "es" });
    await ref.collection("registrations").doc(moves).set({
      name: "Moves", country: "ES", email: "moves@example.com", status: "CONFIRMED", order: 1, paid: false, teamId: null, language: "en", source: "ADMIN", manageToken: "t",
    });
    const sent: OutgoingEmail[] = [];
    setMailTransportForTests(async (email) => {
      if (email.to === "fails@example.com") throw new Error("SMTP down");
      sent.push(email);
    });
    try {
      const result = await run(id, 16, { removed: [5, 6, 7, 8] });
      const to = (address: string) => sent.find((email) => email.to === address);

      // Slot 2 keeps its number; slot 9 is number 5 now (5 to 8 left).
      assert.match(to("stays@example.com")?.subject ?? "", /^Mesas nuevas en /);
      assert.match(to("stays@example.com")?.text ?? "", /de 20 a 16/);
      assert.match(to("stays@example.com")?.text ?? "", /sigue siendo el 2\./);
      assert.match(to("stays@example.com")?.text ?? "", /\/\?tournament=/);
      assert.match(to("moves@example.com")?.subject ?? "", /^New tables in /);
      assert.match(to("moves@example.com")?.text ?? "", /changes from 9 to 5/);
      assert.match(to("leaves@example.com")?.subject ?? "", /^Ya no juegas en /);
      assert.match(to("leaves@example.com")?.text ?? "", /org@example\.com/);
      assert.equal(sent.every((email) => email.replyTo === "org@example.com"), true);
      assert.equal(sent.length, 3);
      assert.deepEqual(result.emails, { sent: 3, noEmail: [noAddress], failed: [] });
    } finally {
      setMailTransportForTests(null);
    }
  });

  it("gives a place by email to the waiting list, and a failed email never undoes the change", async () => {
    const tag = String(Date.now()).slice(-6);
    const { id, ref } = await create(16, { registration: { status: "CLOSED", maxPlayers: 16, closesOn: null, slug: "", confirmed: 0, waiting: 1 } });
    const waiting = `8${tag}1`;
    const failing = `8${tag}2`;
    await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(waiting).set({ firstName: "Wait", lastName: waiting, country: "es" });
    await ref.collection("registrations").doc(waiting).set({
      name: "Wait", country: "ES", email: "waiting@example.com", status: "WAITING", order: 1, paid: false, teamId: null, language: "fr", source: "ADMIN", manageToken: "t",
    });
    await assign(ref, 3, failing, { email: "fails@example.com" });
    const sent: OutgoingEmail[] = [];
    setMailTransportForTests(async (email) => {
      if (email.to === "fails@example.com") throw new Error("SMTP down");
      sent.push(email);
    });
    try {
      const result = await run(id, 20, { waiting: [waiting] });
      assert.equal(result.tournament.numPlayers, 20);
      assert.equal(sent.length, 1);
      assert.match(sent[0].subject, /^Vous avez une place à /);
      assert.match(sent[0].text, /numéro de joueur est le 17/);
      assert.deepEqual(result.emails, { sent: 1, noEmail: [], failed: [failing] });
    } finally {
      setMailTransportForTests(null);
    }
  });
});
