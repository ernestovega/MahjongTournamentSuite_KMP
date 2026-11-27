import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db, storage } from "../firebase";
import { buildIdCardsPdf, loadIdCardsDocument, readIdCardStaffNames } from "./idCardsService";

const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==", "base64");
const codeOf = (error: unknown) => (error as { details?: { code?: string } }).details?.code;

let counter = 0;
async function tournament(data: Record<string, unknown>, players: Record<string, Record<string, unknown>>) {
  counter++;
  const ref = db.doc(`tournaments/id-cards-malformed-${Date.now()}-${counter}`);
  await ref.set(data);
  for (const [id, player] of Object.entries(players)) await ref.collection("players").doc(id).set(player);
  return ref;
}

/** The ID cards of a tournament whose documents miss fields: each card still gets a name, a team and its tables. */
describe("ID cards of malformed documents", () => {
  it("fills the cards with defaults and the logo of the main organizer", async () => {
    await storage.file("organizationLogos/id-cards-club.png").save(PNG, { contentType: "image/png" });
    await db.doc("organizations/id-cards-club").set({ name: "Club", logoPath: "organizationLogos/id-cards-club.png", primaryColor: "#123456" });
    await db.doc("emaPlayerRegistry/07690001").set({});
    await db.doc("emaPlayerRegistry/07690002").set({ firstName: "Eva", lastName: "Gil", country: "esp" });
    const ref = await tournament({ isTeams: true, name: "A long tournament name", organizerIds: ["id-cards-club"] }, {
      1: { assignedEmaId: "07690001" },
      2: { id: 2, team: 1, assignedEmaId: "07690002" },
    });
    await ref.collection("teams").doc("1").set({});
    await ref.collection("tables").doc("1_1").set({ roundId: 1, tableId: 1 });

    const model = await loadIdCardsDocument(ref.id);
    assert.equal(model.tournamentShortName, "A long tou");
    assert.equal(model.primaryColor, "#123456");
    assert.ok(model.associationLogo != null && model.associationLogo.length > 0);
    assert.equal(model.numberOfRounds, 0);
    const [first, second] = model.players;
    assert.deepEqual([first.playerId, first.name, first.countryCode, first.teamName], [1, "", "", "Team 0"]);
    assert.deepEqual([second.name, second.countryCode, second.teamName], ["Eva Gil", "ESP", ""]);
    assert.ok((await buildIdCardsPdf(model)).length > 0);
  });

  it("goes on without a logo file, and refuses slots without a person or with a person gone", async () => {
    await db.doc("organizations/id-cards-lost-logo").set({ name: "Lost", logoPath: "organizationLogos/not-there.png" });
    await db.doc("emaPlayerRegistry/07690011").set({ firstName: "Ana" });
    const withLostLogo = await tournament({ organizerIds: ["id-cards-lost-logo"], numRounds: 1 }, { 1: { id: 1, assignedEmaId: "07690011" } });
    assert.equal((await loadIdCardsDocument(withLostLogo.id)).associationLogo, null);

    const unassigned = await tournament({}, { 1: { id: 1 } });
    await assert.rejects(loadIdCardsDocument(unassigned.id), (error) => codeOf(error) === "ASSIGNMENTS_MISSING");
    const gone = await tournament({}, { 1: { id: 1, assignedEmaId: "07690019" } });
    await assert.rejects(loadIdCardsDocument(gone.id), (error) => codeOf(error) === "EMA_PLAYERS_GONE");
  });

  it("reads saved staff names that are not a list as none", async () => {
    const ref = await tournament({}, {});
    await ref.collection("idCards").doc("staff").set({ names: "Ana" });
    assert.deepEqual(await readIdCardStaffNames(ref.id), []);
    await ref.collection("idCards").doc("staff").set({ names: ["Ana", 3, "Luis"] });
    assert.deepEqual(await readIdCardStaffNames(ref.id), ["Ana", "Luis"]);
  });

  it("refuses a sheet where no card fits", async () => {
    await assert.rejects(
      buildIdCardsPdf({ tournamentShortName: "T", year: "2026", primaryColor: "#000000", associationLogo: null, numberOfRounds: 1, roundSchedules: [], players: [] }, {
        cardsPerSheet: 1, scalePercent: 1000, landscape: false, doubleSided: false, cutMarks: false, backs: false, backSameAsFront: false,
        substituteSheets: 0, staffSheets: 0, staffNames: [],
      } as never),
      /does not fit/,
    );
  });
});
