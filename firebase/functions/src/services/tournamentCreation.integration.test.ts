import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { requireEmulator } from "../testing/firestoreFixtures";
import { createTournament } from "./tournamentsService";

function request(overrides: { numPlayers?: number; numRounds?: number; lastPlayerName?: string } = {}) {
  const numPlayers = overrides.numPlayers ?? 8;
  const numRounds = overrides.numRounds ?? 2;
  const players = Array.from({ length: numPlayers }, (_, index) => ({
    id: index + 1,
    team: 0,
    name: index === numPlayers - 1 && overrides.lastPlayerName ? overrides.lastPlayerName : `Player ${index + 1}`,
    country: "ES",
  }));
  const tables = Array.from({ length: numRounds * (numPlayers / 4) }, (_, index) => ({
    roundId: Math.floor(index / (numPlayers / 4)) + 1,
    tableId: (index % (numPlayers / 4)) + 1,
    playerIds: [1, 2, 3, 4],
  }));
  return {
    name: "Test open",
    shortName: "TO",
    primaryColor: "#02B16B",
    eventStartDate: "2026-10-01",
    eventEndDate: "2026-10-02",
    hostCountry: "ES",
    hostCity: "Madrid",
    isTeams: false,
    numPlayers,
    numRounds,
    numTries: 0,
    players,
    tables,
    createdByUid: "creator",
  };
}

async function counts() {
  const [tournaments, players, tables, hands] = await Promise.all([
    db.collection("tournaments").count().get(),
    db.collectionGroup("players").count().get(),
    db.collectionGroup("tables").count().get(),
    db.collectionGroup("hands").count().get(),
  ]);
  return [tournaments, players, tables, hands].map((snapshot) => snapshot.data().count);
}

describe("tournament creation", () => {
  before(requireEmulator);

  it("creates the tournament with all tables, hands and the progress counter", async () => {
    const tournament = await createTournament(request());

    const ref = db.collection("tournaments").doc(tournament.id);
    assert.equal((await ref.collection("tables").get()).size, 4);
    assert.equal((await ref.collection("players").get()).size, 8);
    for (const table of (await ref.collection("tables").get()).docs) {
      assert.equal((await table.ref.collection("hands").get()).size, 16, table.id);
    }
    assert.equal((await ref.get()).get("tablesWithProgress"), 0);
  });

  it("creates more tables than fit in one batch", async () => {
    // 32 players, 6 rounds: 48 tables, 768 hands, so several batches.
    const tournament = await createTournament(request({ numPlayers: 32, numRounds: 6 }));

    const tables = await db.collection("tournaments").doc(tournament.id).collection("tables").get();
    assert.equal(tables.size, 48);
    assert.equal((await db.collectionGroup("hands").where("handId", "==", 16).get())
      .docs.filter((doc) => doc.ref.path.startsWith(`tournaments/${tournament.id}/`)).length, 48);
  });

  it("leaves nothing behind when a write fails in the middle", async () => {
    const before = await counts();

    // A player document above the size limit makes one batch fail after other batches may have been written.
    await assert.rejects(createTournament(request({ numPlayers: 32, numRounds: 6, lastPlayerName: "x".repeat(1_100_000) })));

    assert.deepEqual(await counts(), before);
  });

  it("does not show a tournament while its children are being written", async () => {
    const existing = new Set((await db.collection("tournaments").get()).docs.map((doc) => doc.id));

    const creation = createTournament(request({ numPlayers: 32, numRounds: 6 }));
    let seenWithoutChildren = false;
    // The tournament document is the last write, so a visible tournament must already have its tables and hands.
    for (let attempt = 0; attempt < 20; attempt++) {
      const visible = (await db.collection("tournaments").get()).docs.filter((doc) => !existing.has(doc.id));
      for (const doc of visible) {
        const tables = await doc.ref.collection("tables").count().get();
        if (tables.data().count < 48) seenWithoutChildren = true;
      }
    }
    await creation;

    assert.equal(seenWithoutChildren, false);
    assert.equal((await db.collection("tournaments").get()).size, existing.size + 1);
  });

  it("refuses a payload with the wrong number of tables before writing anything", async () => {
    const before = await counts();
    const bad = request();
    bad.tables.pop();

    await assert.rejects(createTournament(bad));

    assert.deepEqual(await counts(), before);
  });
});
