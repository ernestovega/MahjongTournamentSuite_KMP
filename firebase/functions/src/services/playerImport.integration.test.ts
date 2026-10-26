import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
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
  const total: PlayerImportResult = { assigned: 0, emailsSaved: 0, teamsRenamed: 0, skipped: 0, failures: [] };
  const add = (part: PlayerImportResult) => {
    total.assigned += part.assigned;
    total.failures.push(...part.failures);
    total.emailsSaved += part.emailsSaved;
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

const EMA_IDS = ["00000001", "00000002", "00000003", "00000004"];

async function seedImportTournament(isTeams: boolean): Promise<string> {
  const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  const tournament = db.collection("tournaments").doc(id);
  await tournament.update({ isTeams });
  for (const emaId of EMA_IDS) {
    await db.collection("emaPlayerRegistry").doc(emaId).set({ firstName: "Name", lastName: emaId, country: "ESP" });
  }
  for (let slot = 1; slot <= 4; slot++) {
    await tournament.collection("players").doc(String(slot)).set({
      id: slot, team: slot <= 2 ? 1 : 2, assignedEmaId: null, nonMember: null, assignedCountry: "",
    });
  }
  return id;
}

const file = (text: string) => ({ fileName: "players.csv", buffer: Buffer.from(text) });
const slotOf = async (tournamentId: string, slot: number) =>
  (await db.doc(`tournaments/${tournamentId}/players/${slot}`).get());

describe("player import", () => {
  before(requireEmulator);

  it("assigns players, saves emails and names the teams", async () => {
    const id = await seedImportTournament(true);
    const csv = "id;ema id;email;team\n1;1;a@b.es;Reds\n2;2;;Reds\n3;3;c@b.es;Blues\n";

    const preview = await previewPlayerImport({ tournamentId: id, ...file(csv) });
    assert.deepEqual(preview.rows.map((row) => row.status), ["NEW", "NEW", "NEW"]);
    assert.deepEqual(preview.untouchedSlotIds, [4]);
    // The preview changes nothing.
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), null);

    const result = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });

    assert.deepEqual(result, { assigned: 3, emailsSaved: 2, teamsRenamed: 2, skipped: 0, failures: [] });
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), "00000001");
    assert.equal((await slotOf(id, 1)).get("invitedEmail"), "a@b.es");
    assert.equal((await slotOf(id, 3)).get("assignedEmaId"), "00000003");
    assert.equal((await db.doc(`tournaments/${id}/teams/1`).get()).get("name"), "Reds");
    assert.equal((await db.doc(`tournaments/${id}/teams/2`).get()).get("name"), "Blues");
  });

  it("keeps players that are in a slot unless the editor accepts the replacement", async () => {
    const id = await seedImportTournament(false);
    await applyPlayerImport({ tournamentId: id, ...file("id,ema id\n1,1\n"), replaceExisting: false, uid: "editor" });
    const csv = "id,ema id\n1,2\n2,3\n";

    const kept = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: false, uid: "editor" });
    assert.equal(kept.assigned, 1);
    assert.equal(kept.skipped, 1);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), "00000001");
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), "00000003");

    const replaced = await applyPlayerImport({ tournamentId: id, ...file(csv), replaceExisting: true, uid: "editor" });
    assert.equal(replaced.assigned, 1);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), "00000002");
  });

  it("swaps two players between slots", async () => {
    const id = await seedImportTournament(false);
    await applyPlayerImport({ tournamentId: id, ...file("id,ema id\n1,1\n2,2\n"), replaceExisting: false, uid: "editor" });

    const result = await applyPlayerImport({
      tournamentId: id, ...file("id,ema id\n1,2\n2,1\n"), replaceExisting: true, uid: "editor",
    });

    assert.deepEqual(result.failures, []);
    assert.equal((await slotOf(id, 1)).get("assignedEmaId"), "00000002");
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), "00000001");
  });

  it("skips rows with errors and applies the others", async () => {
    const id = await seedImportTournament(false);

    const result = await applyPlayerImport({
      tournamentId: id, ...file("id,ema id\n1,1\n2,99999999\n"), replaceExisting: false, uid: "editor",
    });

    assert.equal(result.assigned, 1);
    assert.equal(result.skipped, 1);
    assert.equal((await slotOf(id, 2)).get("assignedEmaId"), null);
  });
});

describe("player export", () => {
  before(requireEmulator);

  const parse = (exported: { fileName: string; buffer: Buffer }) => parsePlayerImportFile(exported.fileName, exported.buffer);

  it("writes every slot with the current EMA number, email and team name", async () => {
    const id = await seedImportTournament(true);
    await applyPlayerImport({
      tournamentId: id, ...file("id,ema id,email,team\n1,1,a@b.es,Reds\n3,3,,Blues\n"), replaceExisting: false, uid: "editor",
    });

    const exported = await exportPlayers(id);
    const parsed = await parse(exported);

    assert.equal(exported.fileName, "players.xlsx");
    assert.equal(parsed.hasTeamColumn, true);
    assert.deepEqual(parsed.rows.map((row) => [row.id, row.emaId, row.email, row.team]), [
      ["1", "00000001", "a@b.es", "Reds"],
      ["2", "", "", "Reds"],
      ["3", "00000003", "", "Blues"],
      ["4", "", "", "Blues"],
    ]);
  });

  it("has no team column when the tournament has no teams", async () => {
    const id = await seedImportTournament(false);

    const parsed = await parse(await exportPlayers(id));

    assert.equal(parsed.hasTeamColumn, false);
    assert.equal(parsed.rows.length, 4);
  });

  it("works as a template: importing the exported file changes nothing", async () => {
    const id = await seedImportTournament(true);
    await applyPlayerImport({
      tournamentId: id, ...file("id,ema id,email,team\n1,1,a@b.es,Reds\n2,2,,Reds\n"), replaceExisting: false, uid: "editor",
    });
    const exported = await exportPlayers(id);

    const preview = await previewPlayerImport({ tournamentId: id, ...exported });

    // The two assigned slots are unchanged. The two empty slots have no EMA number yet.
    assert.deepEqual(preview.rows.map((row) => row.status), ["UNCHANGED", "UNCHANGED", "ERROR", "ERROR"]);
    assert.deepEqual(preview.teamRenames, []);
  });

  it("writes the EMA number of a person with a linked account and shows no email for the slot", async () => {
    const id = await seedImportTournament(false);
    await db.doc(`tournaments/${id}/players/2`).update({ assignedEmaId: "00000002", accountUid: "uid-2" });

    const parsed = await parse(await exportPlayers(id));

    assert.deepEqual(parsed.rows[1], { row: 3, id: "2", emaId: "00000002", email: "", team: "" });
  });

  it("refuses a tournament that does not exist", async () => {
    await assert.rejects(() => exportPlayers("does-not-exist"), /not found/i);
  });
});
