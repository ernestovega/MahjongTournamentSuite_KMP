"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const draw_1 = require("../testing/draw");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const tournamentsService_1 = require("./tournamentsService");
function request(overrides = {}) {
    const numPlayers = overrides.numPlayers ?? 16;
    const numRounds = overrides.numRounds ?? 2;
    const players = Array.from({ length: numPlayers }, (_, index) => ({
        id: index + 1,
        team: 0,
        name: index === numPlayers - 1 && overrides.lastPlayerName ? overrides.lastPlayerName : `Player ${index + 1}`,
        country: "ES",
    }));
    const tables = (0, draw_1.validDrawTables)(numPlayers, numRounds);
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
        firebase_1.db.collection("tournaments").count().get(),
        firebase_1.db.collectionGroup("players").count().get(),
        firebase_1.db.collectionGroup("tables").count().get(),
        firebase_1.db.collectionGroup("hands").count().get(),
    ]);
    return [tournaments, players, tables, hands].map((snapshot) => snapshot.data().count);
}
(0, node_test_1.describe)("tournament creation", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("creates the tournament with all tables, hands and the progress counter", async () => {
        const tournament = await (0, tournamentsService_1.createTournament)(request());
        const ref = firebase_1.db.collection("tournaments").doc(tournament.id);
        strict_1.default.equal((await ref.collection("tables").get()).size, 8);
        strict_1.default.equal((await ref.collection("players").get()).size, 16);
        for (const table of (await ref.collection("tables").get()).docs) {
            strict_1.default.equal((await table.ref.collection("hands").get()).size, 16, table.id);
        }
        strict_1.default.equal((await ref.get()).get("tablesWithProgress"), 0);
    });
    (0, node_test_1.it)("creates more tables than fit in one batch", async () => {
        // 32 players, 6 rounds: 48 tables, 768 hands, so several batches.
        const tournament = await (0, tournamentsService_1.createTournament)(request({ numPlayers: 32, numRounds: 6 }));
        const tables = await firebase_1.db.collection("tournaments").doc(tournament.id).collection("tables").get();
        strict_1.default.equal(tables.size, 48);
        strict_1.default.equal((await firebase_1.db.collectionGroup("hands").where("handId", "==", 16).get())
            .docs.filter((doc) => doc.ref.path.startsWith(`tournaments/${tournament.id}/`)).length, 48);
    });
    (0, node_test_1.it)("leaves nothing behind when a write fails in the middle", async () => {
        const before = await counts();
        // A player document above the size limit makes one batch fail after other batches may have been written.
        await strict_1.default.rejects((0, tournamentsService_1.createTournament)(request({ numPlayers: 32, numRounds: 6, lastPlayerName: "x".repeat(1100000) })));
        strict_1.default.deepEqual(await counts(), before);
    });
    (0, node_test_1.it)("does not show a tournament while its children are being written", async () => {
        const existing = new Set((await firebase_1.db.collection("tournaments").get()).docs.map((doc) => doc.id));
        const creation = (0, tournamentsService_1.createTournament)(request({ numPlayers: 32, numRounds: 6 }));
        let seenWithoutChildren = false;
        // The tournament document is the last write, so a visible tournament must already have its tables and hands.
        for (let attempt = 0; attempt < 20; attempt++) {
            const visible = (await firebase_1.db.collection("tournaments").get()).docs.filter((doc) => !existing.has(doc.id));
            for (const doc of visible) {
                const tables = await doc.ref.collection("tables").count().get();
                if (tables.data().count < 48)
                    seenWithoutChildren = true;
            }
        }
        await creation;
        strict_1.default.equal(seenWithoutChildren, false);
        strict_1.default.equal((await firebase_1.db.collection("tournaments").get()).size, existing.size + 1);
    });
    (0, node_test_1.it)("refuses a payload with the wrong number of tables before writing anything", async () => {
        const before = await counts();
        const bad = request();
        bad.tables.pop();
        await strict_1.default.rejects((0, tournamentsService_1.createTournament)(bad));
        strict_1.default.deepEqual(await counts(), before);
    });
    (0, node_test_1.it)("refuses a draw where a player sits at two tables of one round", async () => {
        const before = await counts();
        const bad = request();
        const [a, b] = [bad.tables[0].playerIds, bad.tables[1].playerIds];
        bad.tables[1].playerIds = [a[0], ...b.slice(1)];
        await strict_1.default.rejects((0, tournamentsService_1.createTournament)(bad), /Invalid draw/);
        strict_1.default.deepEqual(await counts(), before);
    });
});
//# sourceMappingURL=tournamentCreation.integration.test.js.map