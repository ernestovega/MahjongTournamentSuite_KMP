import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { requireEmulator } from "../testing/firestoreFixtures";
import { listCountries } from "./countriesService";
import { getTournamentManifest } from "./dataVersionsService";

/** Versions and countries read from documents that miss fields. */
describe("data versions and countries with missing fields", () => {
  before(requireEmulator);

  it("reads a tournament without versions as all at revision zero", async () => {
    const tournamentId = `versions-bare-${randomUUID()}`;
    await db.doc(`tournaments/${tournamentId}`).set({ name: "Bare" });
    const manifest = await getTournamentManifest(tournamentId);
    assert.ok(Object.values(manifest.resources).every((stamp) => stamp.revision === 0), JSON.stringify(manifest.resources));
  });

  it("leaves out countries without a usable code or without a name", async () => {
    await db.doc("countries/not-a-code").set({ name: "Nowhere" });
    await db.doc("countries/zy-nameless").set({ code: "ZY" });
    const countries = await listCountries();
    assert.equal(countries.some((country) => country.name === "Nowhere" || country.code === "ZY"), false);
  });
});
