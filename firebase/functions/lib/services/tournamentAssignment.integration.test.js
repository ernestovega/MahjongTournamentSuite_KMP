"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const dataVersionsService_1 = require("./dataVersionsService");
const tableManagerService_1 = require("./tableManagerService");
const tournamentContentService_1 = require("./tournamentContentService");
async function seedPlayers(tournamentId) {
    const tournament = firebase_1.db.collection("tournaments").doc(tournamentId);
    await tournament.update({ isTeams: false });
    for (const emaId of ["A", "B", "C"]) {
        await firebase_1.db.collection("emaPlayerRegistry").doc(emaId).set({ country: "ES" });
    }
    await tournament.collection("players").doc("1").set({ id: 1, assignedEmaId: "A", nonMember: null, assignedCountry: "ES" });
    await tournament.collection("emaPlayerAssignments").doc("A").set({ playerId: 1 });
    await tournament.collection("players").doc("2").set({ id: 2, assignedEmaId: null, nonMember: null, assignedCountry: "" });
}
function assign(tournamentId, playerId, emaId) {
    return (0, tournamentContentService_1.assignTournamentPlayer)({ tournamentId, playerId, emaId, nonMember: null });
}
async function startATable(tournamentId) {
    await (0, tableManagerService_1.saveTableState)({
        tournamentId,
        roundId: 1,
        tableId: 1,
        expectedVersion: 0,
        tablePatch: {},
        handPatches: [{ handId: 1, patch: { handScore: "8" } }],
    });
}
(0, node_test_1.describe)("changing players after results start", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("allows a change before any table has data", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedPlayers(id);
        await assign(id, 1, "B");
        strict_1.default.equal((await firebase_1.db.collection("tournaments").doc(id).collection("players").doc("1").get()).get("assignedEmaId"), "B");
    });
    (0, node_test_1.it)("refuses to replace a player after a table has data", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedPlayers(id);
        await startATable(id);
        await strict_1.default.rejects(assign(id, 1, "B"), (error) => error instanceof httpError_1.HttpError && error.status === 409);
    });
    (0, node_test_1.it)("still allows filling an empty slot after a table has data", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedPlayers(id);
        await startATable(id);
        await assign(id, 2, "C");
        strict_1.default.equal((await firebase_1.db.collection("tournaments").doc(id).collection("players").doc("2").get()).get("assignedEmaId"), "C");
    });
    (0, node_test_1.it)("stays consistent when a player change and the first result happen together", async () => {
        // Production Firestore locks the documents that a transaction reads, so the check and the save are ordered.
        // The emulator lets a blind write pass such a lock. So this test checks consistency, not the order.
        for (let attempt = 0; attempt < 6; attempt++) {
            const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
            await seedPlayers(id);
            const [assigned, saved] = await Promise.allSettled([assign(id, 1, "B"), startATable(id)]);
            strict_1.default.equal(saved.status, "fulfilled", `attempt ${attempt}`);
            if (assigned.status === "rejected") {
                strict_1.default.ok(assigned.reason instanceof httpError_1.HttpError && assigned.reason.status === 409, `attempt ${attempt}`);
            }
            const player = await firebase_1.db.collection("tournaments").doc(id).collection("players").doc("1").get();
            strict_1.default.equal(player.get("assignedEmaId"), assigned.status === "fulfilled" ? "B" : "A", `attempt ${attempt}`);
            strict_1.default.equal((await firebase_1.db.collection("tournaments").doc(id).get()).get("tablesWithProgress"), 1);
        }
    });
    (0, node_test_1.it)("changes the players revision in the same commit as the data", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedPlayers(id);
        await assign(id, 2, "C");
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.ok(manifest.resources.players.revision >= 1);
    });
});
//# sourceMappingURL=tournamentAssignment.integration.test.js.map