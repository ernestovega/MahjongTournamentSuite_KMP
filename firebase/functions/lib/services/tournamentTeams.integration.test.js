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
async function seedTeams() {
    const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
    const tournament = firebase_1.db.collection("tournaments").doc(id);
    await tournament.update({ isTeams: true });
    for (const emaId of ["A", "B", "C"]) {
        await firebase_1.db.collection("emaPlayerRegistry").doc(emaId).set({ country: "ES" });
    }
    await tournament.collection("players").doc("1").set({ id: 1, team: 1, assignedEmaId: null, nonMember: null, assignedCountry: "" });
    await tournament.collection("players").doc("2").set({ id: 2, team: 1, assignedEmaId: null, nonMember: null, assignedCountry: "" });
    await tournament.collection("players").doc("3").set({ id: 3, team: 2, assignedEmaId: null, nonMember: null, assignedCountry: "" });
    return id;
}
async function startATable(tournamentId) {
    await (0, tableManagerService_1.saveTableState)({
        tournamentId, roundId: 1, tableId: 1, expectedVersion: 0, tablePatch: {},
        handPatches: [{ handId: 1, patch: { handScore: "8" } }],
    });
}
const isConflict = (error) => error instanceof httpError_1.HttpError && error.status === 409;
(0, node_test_1.describe)("team changes", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("assigns players to the slots of a team before results start", async () => {
        const id = await seedTeams();
        await (0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] });
        const players = firebase_1.db.collection("tournaments").doc(id).collection("players");
        strict_1.default.equal((await players.doc("1").get()).get("assignedEmaId"), "A");
        strict_1.default.equal((await players.doc("2").get()).get("assignedEmaId"), "B");
        strict_1.default.equal((await firebase_1.db.collection("tournaments").doc(id).collection("teams").doc("1").get()).get("name"), "Reds");
    });
    (0, node_test_1.it)("raises the team and player revisions", async () => {
        const id = await seedTeams();
        const before = await (0, dataVersionsService_1.getTournamentManifest)(id);
        await (0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", null] });
        const after = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.ok(after.resources.teams.revision > before.resources.teams.revision);
        strict_1.default.ok(after.resources.players.revision > before.resources.players.revision);
    });
    (0, node_test_1.it)("refuses to change the players of a team after a table has data", async () => {
        const id = await seedTeams();
        await (0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] });
        await startATable(id);
        await strict_1.default.rejects((0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "C"] }), isConflict);
    });
    (0, node_test_1.it)("still allows a new team name after a table has data", async () => {
        const id = await seedTeams();
        await (0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] });
        await startATable(id);
        await (0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Blues", emaIds: ["A", "B"] });
        strict_1.default.equal((await firebase_1.db.collection("tournaments").doc(id).collection("teams").doc("1").get()).get("name"), "Blues");
    });
    (0, node_test_1.it)("refuses the same player in two slots", async () => {
        const id = await seedTeams();
        await strict_1.default.rejects((0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "A"] }), isConflict);
    });
    (0, node_test_1.it)("refuses a team name that another team uses", async () => {
        const id = await seedTeams();
        await (0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 2, name: "Reds", emaIds: ["C"] });
        await strict_1.default.rejects((0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "reds", emaIds: ["A", "B"] }), isConflict);
    });
    (0, node_test_1.it)("refuses a team in a tournament without teams", async () => {
        const id = await seedTeams();
        await firebase_1.db.collection("tournaments").doc(id).update({ isTeams: false });
        await strict_1.default.rejects((0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] }), isConflict);
    });
    (0, node_test_1.it)("fails with not found for a team that does not exist", async () => {
        const id = await seedTeams();
        await strict_1.default.rejects((0, tournamentContentService_1.updateTournamentTeam)({ tournamentId: id, teamId: 9, name: "Ghosts", emaIds: ["A"] }), (error) => error instanceof httpError_1.HttpError && error.status === 404);
    });
});
//# sourceMappingURL=tournamentTeams.integration.test.js.map