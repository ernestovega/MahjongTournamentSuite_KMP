import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { HttpError } from "../api/httpError";
import { db } from "../firebase";
import { requireEmulator } from "../testing/firestoreFixtures";
import { createTournament, updateTournamentSettings } from "./tournamentsService";

function createRequest(extra: Record<string, unknown> = {}) {
  return {
    name: "Match settings open",
    shortName: "MS",
    primaryColor: "#02B16B",
    eventStartDate: "2026-10-01",
    eventEndDate: "2026-10-02",
    hostCountry: "ES",
    hostCity: "Madrid",
    isTeams: false,
    numPlayers: 4,
    numRounds: 1,
    numTries: 0,
    players: [1, 2, 3, 4].map((id) => ({ id, team: 0, name: `Player ${id}`, country: "ES" })),
    tables: [{ roundId: 1, tableId: 1, playerIds: [1, 2, 3, 4] }],
    createdByUid: "creator",
    ...extra,
  };
}

function updateRequest(tournamentId: string, extra: Record<string, unknown> = {}) {
  return {
    tournamentId,
    name: "Match settings open",
    shortName: "MS",
    primaryColor: "#02B16B",
    eventStartDate: "2026-10-01",
    eventEndDate: "2026-10-02",
    hostCountry: "ES",
    hostCity: "Madrid",
    ...extra,
  };
}

async function badRequest(promise: Promise<unknown>): Promise<void> {
  await assert.rejects(promise, (error: unknown) => error instanceof HttpError && error.status === 400);
}

describe("tournament match settings", () => {
  before(requireEmulator);

  it("uses the defaults when the request has no match settings", async () => {
    const tournament = await createTournament(createRequest());

    assert.equal(tournament.matchDurationMinutes, 120);
    assert.equal(tournament.matchEndMode, "STOP");
    assert.equal(tournament.matchExtraMinutes, 15);
    assert.equal(tournament.timeZone, null);
  });

  it("stores and returns the match settings of a new tournament", async () => {
    const tournament = await createTournament(createRequest({
      matchDurationMinutes: 115,
      matchEndMode: "FINISH_HAND",
      matchExtraMinutes: 10,
      timeZone: " Europe/Madrid ",
    }));

    const stored = (await db.doc(`tournaments/${tournament.id}`).get()).data() ?? {};
    assert.equal(stored.matchDurationMinutes, 115);
    assert.equal(stored.matchEndMode, "FINISH_HAND");
    assert.equal(stored.matchExtraMinutes, 10);
    assert.equal(stored.timeZone, "Europe/Madrid");
    assert.equal(tournament.matchExtraMinutes, 10);
  });

  it("refuses invalid match settings when it creates a tournament", async () => {
    await badRequest(createTournament(createRequest({ matchDurationMinutes: 0 })));
    await badRequest(createTournament(createRequest({ matchEndMode: "LATER" })));
    await badRequest(createTournament(createRequest({ matchExtraMinutes: 500 })));
    await badRequest(createTournament(createRequest({ timeZone: "Mars/Base" })));
  });

  it("changes the match settings and keeps them when a later update has none", async () => {
    const created = await createTournament(createRequest());

    const changed = await updateTournamentSettings(updateRequest(created.id, {
      matchDurationMinutes: 100,
      matchEndMode: "FINISH_HAND",
      matchExtraMinutes: 12,
      timeZone: "Europe/Lisbon",
    }));
    assert.equal(changed.matchDurationMinutes, 100);
    assert.equal(changed.matchEndMode, "FINISH_HAND");
    assert.equal(changed.matchExtraMinutes, 12);
    assert.equal(changed.timeZone, "Europe/Lisbon");

    const kept = await updateTournamentSettings(updateRequest(created.id, { name: "Renamed" }));
    assert.equal(kept.name, "Renamed");
    assert.equal(kept.matchDurationMinutes, 100);
    assert.equal(kept.matchEndMode, "FINISH_HAND");
    assert.equal(kept.matchExtraMinutes, 12);
    assert.equal(kept.timeZone, "Europe/Lisbon");
  });

  it("clears the time zone with an empty value and refuses invalid values on update", async () => {
    const created = await createTournament(createRequest({ timeZone: "Europe/Madrid" }));

    const cleared = await updateTournamentSettings(updateRequest(created.id, { timeZone: "" }));
    assert.equal(cleared.timeZone, null);

    await badRequest(updateTournamentSettings(updateRequest(created.id, { matchDurationMinutes: 601 })));
    await badRequest(updateTournamentSettings(updateRequest(created.id, { timeZone: "Nowhere/Land" })));
  });

  it("reads an old tournament document without the new fields", async () => {
    const created = await createTournament(createRequest());
    await db.doc(`tournaments/${created.id}`).update({
      matchDurationMinutes: null, matchEndMode: null, matchExtraMinutes: null, timeZone: null,
    });

    const updated = await updateTournamentSettings(updateRequest(created.id));

    assert.equal(updated.matchDurationMinutes, 120);
    assert.equal(updated.matchEndMode, "STOP");
    assert.equal(updated.matchExtraMinutes, 15);
    assert.equal(updated.timeZone, null);
  });
});
