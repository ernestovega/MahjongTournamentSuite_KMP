import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, requireAuthEmulator } from "../testing/authFixtures";
import { seedTournament } from "../testing/firestoreFixtures";
import { setMailTransportForTests, type OutgoingEmail } from "./mailService";
import { setPasswordResetSenderForTests } from "./firebaseAuthRest";
import { loadAccounts, setAccountEmaId } from "./playerAccountsService";
import { parsePlayerImportFile } from "./playerImport";
import {
  applyPlayerImport as applyStep,
  exportPlayers,
  IMPORT_BATCH_SIZE,
  previewPlayerImport,
  type PlayerImportResult,
} from "./playerImportService";

/** Runs the steps like the app: CLEAR, ASSIGN in batches, FINISH. */
async function applyPlayerImport(params: {
  tournamentId: string; fileName: string; buffer: Buffer; replaceExisting: boolean; uid: string;
}): Promise<PlayerImportResult> {
  const preview = await previewPlayerImport(params);
  const total: PlayerImportResult = { assigned: 0, emailsSaved: 0, emailsRefused: 0, teamsRenamed: 0, skipped: 0, failures: [] };
  const add = (part: PlayerImportResult) => {
    total.assigned += part.assigned;
    total.failures.push(...part.failures);
    total.emailsSaved += part.emailsSaved;
    total.emailsRefused += part.emailsRefused;
    total.teamsRenamed += part.teamsRenamed;
    total.skipped += part.skipped;
  };
  add(await applyStep({ ...params, phase: "CLEAR" }));
  const lines = preview.rows.filter((row) => row.status === "NEW" || row.status === "REPLACE").map((row) => row.row);
  for (let start = 0; start < lines.length; start += IMPORT_BATCH_SIZE) {
    add(await applyStep({ ...params, phase: "ASSIGN", rows: lines.slice(start, start + IMPORT_BATCH_SIZE) }));
  }
  add(await applyStep({ ...params, phase: "FINISH" }));
  return total;
}

/** Every test gets its own EMA numbers, because an account keeps its EMA number for all tournaments. */
function newEmaIds(): string[] {
  const prefix = String(Math.floor(Math.random() * 9_000_000) + 1_000_000);
  return [1, 2, 3, 4].map((n) => `${prefix}${n}`);
}

async function seedImportTournament(isTeams: boolean): Promise<{ id: string; emas: string[] }> {
  const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  const emas = newEmaIds();
  const tournament = db.collection("tournaments").doc(id);
  await tournament.update({ isTeams });
  for (const emaId of emas) {
    await db.collection("emaPlayerRegistry").doc(emaId).set({ firstName: "Name", lastName: emaId, country: "ESP" });
  }
  for (let slot = 1; slot <= 4; slot++) {
    await tournament.collection("players").doc(String(slot)).set({
      id: slot, team: slot <= 2 ? 1 : 2, assignedEmaId: null, assignedCountry: "",
    });
  }
  return { id, emas };
}

const file = (text: string) => ({ fileName: "players.csv", buffer: Buffer.from(text) });
const slotOf = async (tournamentId: string, slot: number) =>
  (await db.doc(`tournaments/${tournamentId}/players/${slot}`).get());
const uniqueEmail = () => `${randomUUID()}@example.test`;

describe("player import", () => {
  const sent: OutgoingEmail[] = [];
  const resets: string[] = [];
  before(() => {
    requireAuthEmulator();
    setMailTransportForTests(async (email) => { sent.push(email); });
    setPasswordResetSenderForTests(async (email) => { resets.push(email); });
  });
  after(() => {
    setMailTransportForTests(null);
    setPasswordResetSenderForTests(null);
  });

  it("assigns players, creates the accounts of the emails and names the teams", async () => {
    const { id, emas } = await seedImportTournament(true);
    const [a, c] = [uniqueEmail(), uniqueEmail()];
    const csv = `id;ema id;email;team\n1;${emas[0]};${a};Reds\n2;${emas[1]};;Reds\n3;${emas[2]};${c.toUpperCase()};Blues\n`;

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });
    assert.deepEqual(preview.rows.map((row) => row.status), ["NEW", "NEW", "NEW"]);
    assert.deepEqual(preview.rows.map((row) => row.createsAccount), [true, false, true]);
    assert.deepEqual(preview.untouchedSlotIds, [4]);
    // The preview changes nothing: no slot, no account.
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), null);
    await assert.rejects(auth.getUserByEmail(a));

    const before = { mails: sent.length, resets: resets.length };
    const result = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });

    assert.deepEqual(result, { assigned: 3, emailsSaved: 2, emailsRefused: 0, teamsRenamed: 2, skipped: 0, failures: [] });
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[0]);
    assert.equal((await slotOf(id, 3)).get("assignedEmaId"), emas[2]);
    assert.equal((await db.doc(`tournaments/${id}/teams/1`).get()).get("name"), "Reds");
    assert.equal((await db.doc(`tournaments/${id}/teams/2`).get()).get("name"), "Blues");

    // The email lives in the account. The slot holds no email, and the account is linked to the slot.
    const account = await auth.getUserByEmail(a);
    assert.equal((await slotOf(id, 1)).get("accountUid"), account.uid);
    assert.equal((await slotOf(id, 1)).get("invitedEmail"), undefined);
    assert.equal((await db.doc(`users/${account.uid}`).get()).get("emaId"), emas[0]);
    assert.equal((await db.doc(`emaIdIndex/${emas[0]}`).get()).get("uid"), account.uid);
    assert.deepEqual(account.customClaims, { player: true });
    assert.equal((await auth.getUserByEmail(c.toLowerCase())).uid, (await slotOf(id, 3)).get("accountUid"));
    assert.equal((await slotOf(id, 2)).get("accountUid"), undefined);
    // The account has no password and the import sends no email.
    assert.ok(!account.passwordHash);
    assert.equal((await loadAccounts([account.uid])).get(account.uid)?.hasPassword, false);
    assert.deepEqual({ mails: sent.length, resets: resets.length }, before);
  });

  it("keeps the email of an account that has one, warns, and applies the rest of the row", async () => {
    const { id, emas } = await seedImportTournament(true);
    const owner = await createAccount({ role: "PLAYER" });
    await setAccountEmaId({ uid: owner.uid, emaId: emas[0] });
    const csv = `id,ema id,email,team\n1,${emas[0]},${uniqueEmail()},Reds\n`;

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });
    assert.deepEqual(preview.rows[0].issues.map((issue) => [issue.code, issue.severity, issue.args[0]]), [
      ["EMAIL_KEPT_ACCOUNT", "WARNING", owner.email],
    ]);
    assert.equal(preview.rows[0].status, "NEW");
    assert.equal(preview.rows[0].createsAccount, false);

    const result = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });

    assert.deepEqual(result, { assigned: 1, emailsSaved: 0, emailsRefused: 1, teamsRenamed: 1, skipped: 0, failures: [] });
    assert.equal((await auth.getUser(owner.uid)).email, owner.email);
    assert.equal((await slotOf(id, 1)).get("accountUid"), owner.uid);
    assert.equal((await db.doc(`tournaments/${id}/teams/1`).get()).get("name"), "Reds");
  });

  it("never links an account to an EMA number because of an email: an email of another player is refused", async () => {
    const { id, emas } = await seedImportTournament(false);
    const owner = await createAccount({ role: "PLAYER" });
    await setAccountEmaId({ uid: owner.uid, emaId: emas[3] });
    const csv = `id,ema id,email\n1,${emas[0]},${owner.email}\n`;

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });
    assert.deepEqual(preview.rows[0].issues.map((issue) => [issue.code, issue.args[0]]), [["EMAIL_IN_USE", emas[3]]]);

    const result = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });

    assert.equal(result.assigned, 1);
    assert.equal(result.emailsSaved, 0);
    assert.equal(result.emailsRefused, 1);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[0]);
    assert.equal((await slotOf(id, 1)).get("accountUid"), undefined);
    assert.equal((await db.doc(`users/${owner.uid}`).get()).get("emaId"), emas[3]);
    assert.equal((await db.doc(`emaIdIndex/${emas[0]}`).get()).exists, false);
  });

  it("refuses the email of an account that has no EMA number and does not give it one", async () => {
    const { id, emas } = await seedImportTournament(false);
    const editor = await createAccount({ role: "EDITOR" });

    const result = await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${editor.email}\n`), replaceExisting: false, uid: "editor",
    });

    assert.equal(result.emailsRefused, 1);
    assert.equal((await db.doc(`users/${editor.uid}`).get()).get("emaId"), undefined);
    assert.equal((await slotOf(id, 1)).get("accountUid"), undefined);
  });

  it("gives a shared email to the first player of the file only", async () => {
    const { id, emas } = await seedImportTournament(false);
    const shared = uniqueEmail();

    const result = await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${shared}\n2,${emas[1]},${shared}\n`),
      replaceExisting: false, uid: "editor",
    });

    assert.deepEqual(result, { assigned: 2, emailsSaved: 1, emailsRefused: 1, teamsRenamed: 0, skipped: 0, failures: [] });
    const account = await auth.getUserByEmail(shared);
    assert.equal((await slotOf(id, 1)).get("accountUid"), account.uid);
    assert.equal((await slotOf(id, 2)).get("accountUid"), undefined);
    assert.equal((await db.doc(`users/${account.uid}`).get()).get("emaId"), emas[0]);
  });

  it("links the account to every tournament that holds the EMA number", async () => {
    const { id, emas } = await seedImportTournament(false);
    const other = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${other}/players/7`).set({ id: 7, team: 0, assignedEmaId: emas[0], assignedCountry: "ESP" });
    await db.doc(`tournaments/${other}/emaPlayerAssignments/${emas[0]}`).set({ playerId: 7 });
    const email = uniqueEmail();

    await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${email}\n`), replaceExisting: false, uid: "editor",
    });

    const account = await auth.getUserByEmail(email);
    assert.equal((await slotOf(id, 1)).get("accountUid"), account.uid);
    assert.equal((await db.doc(`tournaments/${other}/players/7`).get()).get("accountUid"), account.uid);
    assert.ok((await db.doc(`users/${account.uid}/playerTournaments/${other}`).get()).exists);
  });

  it("a second import of the same file changes nothing and creates no second account", async () => {
    const { id, emas } = await seedImportTournament(true);
    const email = uniqueEmail();
    const csv = `id,ema id,email,team\n1,${emas[0]},${email},Reds\n`;
    await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });
    const again = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });

    assert.deepEqual(preview.rows.map((row) => row.status), ["UNCHANGED"]);
    assert.deepEqual(preview.rows[0].issues, []);
    assert.deepEqual(again, { assigned: 0, emailsSaved: 0, emailsRefused: 0, teamsRenamed: 0, skipped: 0, failures: [] });
  });

  it("a row without an email leaves the account and its email as they are", async () => {
    const { id, emas } = await seedImportTournament(false);
    const owner = await createAccount({ role: "PLAYER" });
    await setAccountEmaId({ uid: owner.uid, emaId: emas[0] });

    const result = await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id\n1,${emas[0]}\n`), replaceExisting: false, uid: "editor",
    });

    assert.equal(result.assigned, 1);
    assert.equal((await auth.getUser(owner.uid)).email, owner.email);
    assert.equal((await slotOf(id, 1)).get("accountUid"), owner.uid);
  });

  it("keeps players that are in a slot unless the editor accepts the replacement", async () => {
    const { id, emas } = await seedImportTournament(false);
    await applyPlayerImport({ tournamentId: id, ...file(`id,ema id\n1,${emas[0]}\n`), replaceExisting: false, uid: "editor" });
    const csv = `id,ema id\n1,${emas[1]}\n2,${emas[2]}\n`;

    const kept = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });
    assert.equal(kept.assigned, 1);
    assert.equal(kept.skipped, 1);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[0]);
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), emas[2]);

    const replaced = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: true, uid: "editor" });
    assert.equal(replaced.assigned, 1);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[1]);
  });

  it("a replaced player takes the account link with it and the new player gets the account of the new EMA number", async () => {
    const { id, emas } = await seedImportTournament(false);
    const first = await createAccount({ role: "PLAYER" });
    const second = await createAccount({ role: "PLAYER" });
    await setAccountEmaId({ uid: first.uid, emaId: emas[0] });
    await setAccountEmaId({ uid: second.uid, emaId: emas[1] });
    await applyPlayerImport({ tournamentId: id, ...file(`id,ema id\n1,${emas[0]}\n`), replaceExisting: false, uid: "editor" });
    assert.equal((await slotOf(id, 1)).get("accountUid"), first.uid);

    await applyPlayerImport({ tournamentId: id, ...file(`id,ema id\n1,${emas[1]}\n`), replaceExisting: true, uid: "editor" });

    assert.equal((await slotOf(id, 1)).get("accountUid"), second.uid);
    assert.equal((await db.doc(`users/${first.uid}/playerTournaments/${id}`).get()).exists, false);
  });

  it("swaps two players between slots", async () => {
    const { id, emas } = await seedImportTournament(false);
    await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id\n1,${emas[0]}\n2,${emas[1]}\n`), replaceExisting: false, uid: "editor",
    });

    const result = await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id\n1,${emas[1]}\n2,${emas[0]}\n`), replaceExisting: true, uid: "editor",
    });

    assert.deepEqual(result.failures, []);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), emas[1]);
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), emas[0]);
  });

  it("skips rows with errors and applies the others", async () => {
    const { id, emas } = await seedImportTournament(false);

    const result = await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id\n1,${emas[0]}\n2,99999999\n`), replaceExisting: false, uid: "editor",
    });

    assert.equal(result.assigned, 1);
    assert.equal(result.skipped, 1);
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), null);
  });

  it("an error in the email of one row does not stop the others", async () => {
    const { id, emas } = await seedImportTournament(false);
    const email = uniqueEmail();

    const result = await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},not-an-email\n2,${emas[1]},${email}\n`),
      replaceExisting: false, uid: "editor",
    });

    assert.equal(result.assigned, 1);
    assert.equal(result.emailsSaved, 1);
    assert.equal(result.skipped, 1);
    assert.equal((await slotOf(id, 2)).get("accountUid"), (await auth.getUserByEmail(email)).uid);
  });

  it("the CLEAR and ASSIGN steps read no account data", async () => {
    const { id, emas } = await seedImportTournament(false);
    const owner = await createAccount({ role: "PLAYER" });

    const result = await applyStep({
      tournamentId: id, ...file(`id,ema id,email\n1,${emas[0]},${owner.email}\n`), replaceExisting: false, uid: "editor",
      phase: "ASSIGN", rows: [2],
    });

    assert.equal(result.assigned, 1);
    assert.equal(result.emailsSaved, 0);
    assert.equal(result.emailsRefused, 0);
  });
});

describe("player export", () => {
  before(requireAuthEmulator);

  const parse = (exported: { fileName: string; buffer: Buffer }) => parsePlayerImportFile(exported.fileName, exported.buffer);

  it("writes every slot with the current EMA number, the email of the account and the team name", async () => {
    const { id, emas } = await seedImportTournament(true);
    const email = uniqueEmail();
    await applyPlayerImport({
      tournamentId: id, ...file(`id,ema id,email,team\n1,${emas[0]},${email},Reds\n3,${emas[2]},,Blues\n`),
      replaceExisting: false, uid: "editor",
    });

    const exported = await exportPlayers(id);
    const parsed = await parse(exported);

    assert.equal(exported.fileName, "players.xlsx");
    assert.equal(parsed.hasTeamColumn, true);
    assert.deepEqual(parsed.rows.map((row) => [row.id, row.emaId, row.email, row.team]), [
      ["1", emas[0], email, "Reds"],
      ["2", "", "", "Reds"],
      ["3", emas[2], "", "Blues"],
      ["4", "", "", "Blues"],
    ]);
  });

  it("writes the email of an account that was linked outside the import (the case of 2026-10-26)", async () => {
    const { id, emas } = await seedImportTournament(false);
    const owner = await createAccount({ role: "PLAYER" });
    await setAccountEmaId({ uid: owner.uid, emaId: emas[1] });
    await applyPlayerImport({ tournamentId: id, ...file(`id,ema id\n2,${emas[1]}\n`), replaceExisting: false, uid: "editor" });

    const parsed = await parse(await exportPlayers(id));

    assert.deepEqual(parsed.rows[1], { row: 3, id: "2", emaId: emas[1], email: owner.email, team: "" });
  });

  it("has no team column when the tournament has no teams", async () => {
    const { id } = await seedImportTournament(false);

    const parsed = await parse(await exportPlayers(id));

    assert.equal(parsed.hasTeamColumn, false);
    assert.equal(parsed.rows.length, 4);
  });

  it("works as a template: importing the exported file changes nothing and warns about nothing", async () => {
    const { id, emas } = await seedImportTournament(true);
    await applyPlayerImport({
      tournamentId: id,
      ...file(`id,ema id,email,team\n1,${emas[0]},${uniqueEmail()},Reds\n2,${emas[1]},${uniqueEmail()},Reds\n`),
      replaceExisting: false, uid: "editor",
    });
    const exported = await exportPlayers(id);

    const preview = await previewPlayerImport({ tournamentId: id, ...exported });

    // The two assigned slots are unchanged. The two empty slots have no EMA number yet.
    assert.deepEqual(preview.rows.map((row) => row.status), ["UNCHANGED", "UNCHANGED", "ERROR", "ERROR"]);
    assert.deepEqual(preview.rows.slice(0, 2).flatMap((row) => row.issues), []);
    assert.deepEqual(preview.teamRenames, []);
  });

  it("refuses a tournament that does not exist", async () => {
    await assert.rejects(() => exportPlayers("does-not-exist"), /not found/i);
  });
});
