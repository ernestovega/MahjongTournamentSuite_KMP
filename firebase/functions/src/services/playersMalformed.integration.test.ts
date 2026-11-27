import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db } from "../firebase";
import { listPlayersOfTournaments, storePhotoFile, toPlayer, updatePlayer } from "./playersService";

const base64 = (bytes: Buffer) => bytes.toString("base64");
const WEBP = Buffer.concat([Buffer.from("RIFF"), Buffer.alloc(4), Buffer.from("WEBPVP8 ")]);

/** EMA players with odd stored values, photos of every type and the changes that reach their tournaments. */
describe("EMA players with malformed documents", () => {
  it("reads the old European code as no country", () => {
    assert.equal(toPlayer("07710001", { country: " eu " }).country, "");
  });

  it("orders the people of a tournament by last name and then by number", async () => {
    const tournament = db.doc(`tournaments/players-malformed-${Date.now()}`);
    await db.doc("emaPlayerRegistry/07710012").set({ firstName: "Bea", lastName: "Same" });
    await db.doc("emaPlayerRegistry/07710011").set({ firstName: "Ana", lastName: "Same" });
    await tournament.collection("players").doc("1").set({ assignedEmaId: "07710012" });
    await tournament.collection("players").doc("2").set({ assignedEmaId: "07710011" });
    await tournament.collection("players").doc("3").set({ assignedEmaId: "07710019" });
    const players = await listPlayersOfTournaments([tournament.id]);
    assert.deepEqual(players.map((player) => player.emaId), ["07710011", "07710012"]);
  });

  it("checks each photo type, its size and its signature", async () => {
    const store = (contentType: string, dataBase64: string) => storePhotoFile({ folder: "playersMalformed", id: "07710020", contentType, dataBase64 });
    assert.match(await store("image/webp", base64(WEBP)), /playersMalformed%2F07710020\.webp/);
    await assert.rejects(store("image/webp", base64(Buffer.from("RIFF0000WAVE"))), /does not match/);
    await assert.rejects(store("image/bmp", base64(WEBP)), /JPEG, PNG, WebP, or GIF/);
    await assert.rejects(store("image/png", ""), /Photo is empty/);
    await assert.rejects(store("image/png", base64(Buffer.alloc(5 * 1024 * 1024 + 1))), /5 MB or smaller/);
  });

  it("loads the tournaments again when a person hides from the public, and moves a slot without an id field", async () => {
    const tournament = db.doc(`tournaments/players-malformed-hidden-${Date.now()}`);
    await tournament.set({ dataVersions: {} });
    await db.doc("emaPlayerRegistry/07710031").set({ firstName: "Hidden", lastName: "Person", country: "ES" });
    await tournament.collection("players").doc("4").set({ assignedEmaId: "07710031" });

    await updatePlayer({ previousEmaId: "07710031", emaId: "07710031", firstName: "Hidden", lastName: "Person", country: "ES", hiddenFromPublic: true });
    assert.ok((await tournament.get()).get("dataVersions.players.revision") >= 1);

    await updatePlayer({ previousEmaId: "07710031", emaId: "07710032", firstName: "Hidden", lastName: "Person", country: "ES" });
    assert.equal((await tournament.collection("emaPlayerAssignments").doc("07710032").get()).get("playerId"), 4);
    assert.equal((await db.doc("emaPlayerRegistry/07710032").get()).get("hiddenFromPublic"), true);
  });
});
