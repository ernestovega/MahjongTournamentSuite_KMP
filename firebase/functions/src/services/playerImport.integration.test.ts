import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, requireAuthEmulator } from "../testing/authFixtures";
import { seedTournament } from "../testing/firestoreFixtures";
import { setPasswordResetSenderForTests } from "./firebaseAuthRest";
import { setAccountEmaId } from "./playerAccountsService";
import { parsePlayerImportFile } from "./playerImport";
import { applyPlayerImport, exportPlayers, previewPlayerImport } from "./playerImportService";

/** Every test gets its own EMA numbers, because an account keeps its EMA number for all tournaments. */
function newEmaIds(count = 4): string[] {
  const prefix = String(Math.floor(Math.random() * 90_000) + 10_000);
  return Array.from({ length: count }, (_, index) => `${prefix}${String(index + 1).padStart(3, "0")}`);
}

async function registerEmaIds(emaIds: string[]): Promise<void> {
  const writer = db.bulkWriter();
  emaIds.forEach((emaId) => { void writer.set(db.doc(`emaPlayerRegistry/${emaId}`), { firstName: "Name", lastName: emaId, country: "ESP" }); });
  await writer.close();
}

async function seedImportTournament(options: { isTeams: boolean; slots?: number } = { isTeams: false }): Promise<{ id: string; emas: string[] }> {
  const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  const slots = options.slots ?? 4;
  const emas = newEmaIds(Math.max(4, slots));
  await registerEmaIds(emas);
  const tournament = db.collection("tournaments").doc(id);
  await tournament.update({ isTeams: options.isTeams });
  const writer = db.bulkWriter();
  for (let slot = 1; slot <= slots; slot++) {
    void writer.set(tournament.collection("players").doc(String(slot)), {
      id: slot, team: Math.ceil(slot / 2), assignedEmaId: null, assignedCountry: "",
    });
  }
  await writer.close();
  return { id, emas };
}

const file = (text: string) => ({ fileName: "players.csv", buffer: Buffer.from(text) });
const slotOf = async (tournamentId: string, slot: number) => (await db.doc(`tournaments/${tournamentId}/players/${slot}`).get());
const uniqueEmail = () => `${randomUUID()}@example.test`;
const errorOf = async (promise: Promise<unknown>): Promise<{ status?: number; details?: { code?: string } }> => {
  try {
    await promise;
  } catch (error) {
    return error as { status?: number; details?: { code?: string } };
  }
  throw new Error("Expected the call to fail");
};

describe("player import", () => {
  const resets: string[] = [];
  before(() => {
    requireAuthEmulator();
    setPasswordResetSenderForTests(async (email) => { resets.push(email); });
  });
  after(() => setPasswordResetSenderForTests(null));

  it("previews without changing anything", async () => {
    const { id, emas } = await seedImportTournament();
    const preview = await previewPlayerImport({ tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${uniqueEmail()}\n2,${emas[1]},\n`) });

    assert.equal(preview.valid, true);
    assert.equal(preview.importedPlayers, 2);
    assert.equal(preview.existingPlayers, 0);
    assert.equal(preview.tournamentStarted, false);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), null);
    assert.equal((await db.doc(`tournaments/${id}/emaPlayerAssignments/${emas[0]}`).get()).exists, false);
  });

  it("writes the players, the pending emails and the teams, and creates no account and sends no email", async () => {
    const { id, emas } = await seedImportTournament({ isTeams: true });
    const [a, c] = [uniqueEmail(), uniqueEmail()];
    const csv = `id;ema id;email;team\n1;${emas[0]};${a};Reds\n2;${emas[1]};;Reds\n3;${emas[2]};${c.toUpperCase()};Blues\n`;
    const sentBefore = resets.length;

    const result = await applyPlayerImport({ tournamentId: id, ...file(csv), uid: "editor" });

    assert.deepEqual(result, { players: 3, changedSlots: 3, emptiedSlots: 1 });
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[0]);
    assert.equal((await slotOf(id, 1)).get("assignedCountry"), "ESP");
    assert.equal((await slotOf(id, 1)).get("email"), a);
    assert.equal((await slotOf(id, 2)).get("email"), undefined);
    assert.equal((await slotOf(id, 3)).get("email"), c.toLowerCase());
    assert.equal((await slotOf(id, 4)).get("assignedEmaId"), null);
    assert.equal((await db.doc(`tournaments/${id}/emaPlayerAssignments/${emas[0]}`).get()).get("playerId"), 1);
    assert.equal((await db.doc(`tournaments/${id}/teams/1`).get()).get("name"), "Reds");
    assert.equal((await db.doc(`tournaments/${id}/teams/2`).get()).get("name"), "Blues");
    // The import creates no account and sends nothing.
    await assert.rejects(auth.getUserByEmail(a));
    assert.equal(resets.length, sentBefore);
    assert.equal((await db.doc(`emaIdIndex/${emas[0]}`).get()).exists, false);
  });

  it("overwrites every slot: moved players, omitted players, old emails and old substitutions", async () => {
    const { id, emas } = await seedImportTournament();
    const players = db.collection("tournaments").doc(id).collection("players");
    await players.doc("1").update({ assignedEmaId: emas[0], email: "old1@example.test", substitution: { reason: "LATE", fromRound: 1 } });
    await players.doc("2").update({ assignedEmaId: emas[1], email: "old2@example.test" });
    await players.doc("4").update({ assignedEmaId: emas[3], email: "old4@example.test" });
    for (const [slot, emaId] of [[1, emas[0]], [2, emas[1]], [4, emas[3]]] as const) {
      await db.doc(`tournaments/${id}/emaPlayerAssignments/${emaId}`).set({ playerId: slot });
    }

    // Slot 1 gets another player, slot 2 gets the player of slot 1, slot 4 is not in the file.
    const result = await applyPlayerImport({ tournamentId: id, ...file(`id,ema id\n1,${emas[2]}\n2,${emas[0]}\n`), uid: "editor" });

    assert.deepEqual(result, { players: 2, changedSlots: 3, emptiedSlots: 2 });
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[2]);
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), emas[0]);
    assert.equal((await slotOf(id, 4)).get("assignedEmaId"), null);
    for (const slot of [1, 2, 4]) assert.equal((await slotOf(id, slot)).get("email"), undefined);
    assert.equal((await slotOf(id, 1)).get("substitution"), undefined);
    assert.equal((await db.doc(`tournaments/${id}/emaPlayerAssignments/${emas[1]}`).get()).exists, false);
    assert.equal((await db.doc(`tournaments/${id}/emaPlayerAssignments/${emas[3]}`).get()).exists, false);
    assert.equal((await db.doc(`tournaments/${id}/emaPlayerAssignments/${emas[0]}`).get()).get("playerId"), 2);
    assert.equal((await db.doc(`tournaments/${id}/emaPlayerAssignments/${emas[2]}`).get()).get("playerId"), 1);
    const log = await db.collection(`tournaments/${id}/playerLog`).get();
    assert.equal(log.size, 3);
  });

  it("tells which data of each row changes and which players the file removes, without showing any saved email", async () => {
    const { id, emas } = await seedImportTournament({ isTeams: true });
    const players = db.collection("tournaments").doc(id).collection("players");
    await players.doc("1").update({ assignedEmaId: emas[0], email: "old@example.test" });
    await players.doc("3").update({ assignedEmaId: emas[2] });
    await players.doc("4").update({ assignedEmaId: emas[3], email: "gone@example.test" });
    await db.doc(`tournaments/${id}/teams/1`).set({ id: 1, name: "Reds" });
    const csv = `id,ema id,email,team\n1,${emas[0]},new@example.test,Reds\n2,${emas[1]},,Blues\n3,${emas[2]},,Team 2\n`;

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });

    assert.deepEqual(preview.rows.map((row) => [row.slotId, row.changes]), [[1, ["EMAIL"]], [2, ["PLAYER", "TEAM"]], [3, []]]);
    assert.deepEqual(preview.removed, [{ slotId: 4, emaId: emas[3] }]);
    assert.equal(JSON.stringify(preview).includes("old@example.test"), false, "the preview never holds a saved email");
    assert.equal(JSON.stringify(preview).includes("gone@example.test"), false);
  });

  it("marks every row of the first import as a change and removes nobody", async () => {
    const { id, emas } = await seedImportTournament();
    const preview = await previewPlayerImport({ tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},a@example.test\n2,${emas[1]},\n`) });

    assert.deepEqual(preview.rows.map((row) => row.changes), [["PLAYER", "EMAIL"], ["PLAYER"]]);
    assert.deepEqual(preview.removed, []);
  });

  it("changes nothing when the same file is applied again", async () => {
    const { id, emas } = await seedImportTournament();
    const csv = `id,ema id,email\n1,${emas[0]},${uniqueEmail()}\n2,${emas[1]},\n`;
    await applyPlayerImport({ tournamentId: id, ...file(csv), uid: "editor" });

    const again = await applyPlayerImport({ tournamentId: id, ...file(csv), uid: "editor" });

    assert.deepEqual(again, { players: 2, changedSlots: 0, emptiedSlots: 2 });
  });

  it("writes nothing when one row has an error, and the preview lists the error", async () => {
    const { id, emas } = await seedImportTournament();
    const csv = `id,ema id\n1,${emas[0]}\n2,99999999\n`;

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });
    assert.equal(preview.valid, false);
    assert.deepEqual(preview.rows[1].issues.map((issue) => issue.code), ["UNKNOWN_EMA_ID"]);
    const failure = await errorOf(applyPlayerImport({ tournamentId: id, ...file(csv), uid: "editor" }));

    assert.equal(failure.status, 409);
    assert.equal(failure.details?.code, "FILE_HAS_ERRORS");
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), null);
  });

  it("refuses a tournament with table data and says so in the preview", async () => {
    const { id, emas } = await seedImportTournament();
    await db.doc(`tournaments/${id}`).update({ tablesWithProgress: 1 });
    const csv = `id,ema id\n1,${emas[0]}\n`;

    assert.equal((await previewPlayerImport({ tournamentId: id, ...file(csv) })).tournamentStarted, true);
    const failure = await errorOf(applyPlayerImport({ tournamentId: id, ...file(csv), uid: "editor" }));

    assert.equal(failure.status, 409);
    assert.equal(failure.details?.code, "TOURNAMENT_STARTED");
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), null);
  });

  it("refuses an email that an account of another EMA number owns, and accepts the account of the same number", async () => {
    const { id, emas } = await seedImportTournament();
    const owner = await createAccount({ role: "PLAYER" });
    await setAccountEmaId({ uid: owner.uid, emaId: emas[1] });

    const other = await previewPlayerImport({ tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${owner.email}\n`) });
    assert.deepEqual(other.rows[0].issues.map((issue) => issue.code), ["EMAIL_OWNED_BY_OTHER_ACCOUNT"]);
    assert.equal(other.valid, false);

    const same = await previewPlayerImport({ tournamentId: id, ...file(`id,ema id,email\n1,${emas[1]},${owner.email}\n`) });
    assert.equal(same.valid, true);
  });

  it("refuses two rows with the same email", async () => {
    const { id, emas } = await seedImportTournament();
    const email = uniqueEmail();
    const preview = await previewPlayerImport({ tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${email}\n2,${emas[1]},${email.toUpperCase()}\n`) });
    assert.equal(preview.valid, false);
    assert.deepEqual(preview.rows.map((row) => row.issues.map((issue) => issue.code)), [["DUPLICATE_EMAIL"], ["DUPLICATE_EMAIL"]]);
  });

  it("keeps the team names when the file has no team column, and resets the unnamed teams when it has one", async () => {
    const { id, emas } = await seedImportTournament({ isTeams: true });
    await db.doc(`tournaments/${id}/teams/1`).set({ id: 1, name: "Old one" });
    await db.doc(`tournaments/${id}/teams/2`).set({ id: 2, name: "Old two" });

    await applyPlayerImport({ tournamentId: id, ...file(`id,ema id\n1,${emas[0]}\n`), uid: "editor" });
    assert.equal((await db.doc(`tournaments/${id}/teams/1`).get()).get("name"), "Old one");

    await applyPlayerImport({ tournamentId: id, ...file(`id,ema id,team\n1,${emas[0]},Reds\n`), uid: "editor" });
    assert.equal((await db.doc(`tournaments/${id}/teams/1`).get()).get("name"), "Reds");
    assert.equal((await db.doc(`tournaments/${id}/teams/2`).get()).get("name"), "Team 2");
  });

  it("writes a big file in several batches", async () => {
    const slots = 450;
    const { id, emas } = await seedImportTournament({ isTeams: false, slots });
    const csv = `id,ema id\n${emas.map((emaId, index) => `${index + 1},${emaId}`).join("\n")}\n`;

    const result = await applyPlayerImport({ tournamentId: id, ...file(csv), uid: "editor" });

    assert.deepEqual(result, { players: slots, changedSlots: slots, emptiedSlots: 0 });
    assert.equal((await db.collection(`tournaments/${id}/emaPlayerAssignments`).get()).size, slots);
    assert.equal((await slotOf(id, slots)).get("assignedEmaId"), emas[slots - 1]);
  });

  it("refuses an unknown tournament", async () => {
    const failure = await errorOf(previewPlayerImport({ tournamentId: "missing", ...file("id,ema id\n1,1\n") }));
    assert.equal(failure.status, 404);
  });

  describe("export", () => {
    it("writes the account email, or the pending email when the slot has no account, for admins", async () => {
      const { id, emas } = await seedImportTournament();
      const owner = await createAccount({ role: "PLAYER" });
      await setAccountEmaId({ uid: owner.uid, emaId: emas[0] });
      const pending = uniqueEmail();
      await db.doc(`tournaments/${id}/players/1`).update({ assignedEmaId: emas[0], email: `ignored-${pending}` });
      await db.doc(`tournaments/${id}/players/2`).update({ assignedEmaId: emas[1], email: pending });

      const exported = await exportPlayers(id, { includeEmails: true });
      const parsed = await parsePlayerImportFile(exported.fileName, exported.buffer);

      assert.deepEqual(parsed.rows.map((row) => [row.id, row.emaId, row.email]), [
        ["1", emas[0], owner.email.toLowerCase()],
        ["2", emas[1], pending],
        ["3", "", ""],
        ["4", "", ""],
      ]);
    });

    it("leaves the email column empty for the callers that are not admins", async () => {
      const { id, emas } = await seedImportTournament();
      await db.doc(`tournaments/${id}/players/1`).update({ assignedEmaId: emas[0], email: uniqueEmail() });

      const exported = await exportPlayers(id, { includeEmails: false });
      const parsed = await parsePlayerImportFile(exported.fileName, exported.buffer);

      assert.equal(parsed.rows[0].emaId, emas[0]);
      assert.equal(parsed.rows[0].email, "");
    });

    it("writes a file that the import accepts again", async () => {
      const { id, emas } = await seedImportTournament({ isTeams: true });
      await applyPlayerImport({ tournamentId: id, ...file(`id,ema id,email,team\n1,${emas[0]},${uniqueEmail()},Reds\n2,${emas[1]},,Reds\n3,${emas[2]},,Blues\n`), uid: "editor" });

      const exported = await exportPlayers(id, { includeEmails: true });
      const preview = await previewPlayerImport({ tournamentId: id, fileName: exported.fileName, buffer: exported.buffer });

      assert.equal(preview.valid, true);
      assert.equal(preview.importedPlayers, 3);
    });
  });
});
