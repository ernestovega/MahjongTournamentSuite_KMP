"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const tournamentsService_1 = require("./tournamentsService");
function createRequest(extra = {}) {
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
function updateRequest(tournamentId, extra = {}) {
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
async function badRequest(promise) {
    await strict_1.default.rejects(promise, (error) => error instanceof httpError_1.HttpError && error.status === 400);
}
(0, node_test_1.describe)("tournament match settings", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("uses the defaults when the request has no match settings", async () => {
        const tournament = await (0, tournamentsService_1.createTournament)(createRequest());
        strict_1.default.equal(tournament.matchDurationMinutes, 120);
        strict_1.default.equal(tournament.matchEndMode, "STOP");
        strict_1.default.equal(tournament.matchExtraMinutes, 15);
        strict_1.default.equal(tournament.timeZone, null);
    });
    (0, node_test_1.it)("stores and returns the match settings of a new tournament", async () => {
        const tournament = await (0, tournamentsService_1.createTournament)(createRequest({
            matchDurationMinutes: 115,
            matchEndMode: "FINISH_HAND",
            matchExtraMinutes: 10,
            timeZone: " Europe/Madrid ",
        }));
        const stored = (await firebase_1.db.doc(`tournaments/${tournament.id}`).get()).data() ?? {};
        strict_1.default.equal(stored.matchDurationMinutes, 115);
        strict_1.default.equal(stored.matchEndMode, "FINISH_HAND");
        strict_1.default.equal(stored.matchExtraMinutes, 10);
        strict_1.default.equal(stored.timeZone, "Europe/Madrid");
        strict_1.default.equal(tournament.matchExtraMinutes, 10);
    });
    (0, node_test_1.it)("refuses invalid match settings when it creates a tournament", async () => {
        await badRequest((0, tournamentsService_1.createTournament)(createRequest({ matchDurationMinutes: 0 })));
        await badRequest((0, tournamentsService_1.createTournament)(createRequest({ matchEndMode: "LATER" })));
        await badRequest((0, tournamentsService_1.createTournament)(createRequest({ matchExtraMinutes: 500 })));
        await badRequest((0, tournamentsService_1.createTournament)(createRequest({ timeZone: "Mars/Base" })));
    });
    (0, node_test_1.it)("changes the match settings and keeps them when a later update has none", async () => {
        const created = await (0, tournamentsService_1.createTournament)(createRequest());
        const changed = await (0, tournamentsService_1.updateTournamentSettings)(updateRequest(created.id, {
            matchDurationMinutes: 100,
            matchEndMode: "FINISH_HAND",
            matchExtraMinutes: 12,
            timeZone: "Europe/Lisbon",
        }));
        strict_1.default.equal(changed.matchDurationMinutes, 100);
        strict_1.default.equal(changed.matchEndMode, "FINISH_HAND");
        strict_1.default.equal(changed.matchExtraMinutes, 12);
        strict_1.default.equal(changed.timeZone, "Europe/Lisbon");
        const kept = await (0, tournamentsService_1.updateTournamentSettings)(updateRequest(created.id, { name: "Renamed" }));
        strict_1.default.equal(kept.name, "Renamed");
        strict_1.default.equal(kept.matchDurationMinutes, 100);
        strict_1.default.equal(kept.matchEndMode, "FINISH_HAND");
        strict_1.default.equal(kept.matchExtraMinutes, 12);
        strict_1.default.equal(kept.timeZone, "Europe/Lisbon");
    });
    (0, node_test_1.it)("clears the time zone with an empty value and refuses invalid values on update", async () => {
        const created = await (0, tournamentsService_1.createTournament)(createRequest({ timeZone: "Europe/Madrid" }));
        const cleared = await (0, tournamentsService_1.updateTournamentSettings)(updateRequest(created.id, { timeZone: "" }));
        strict_1.default.equal(cleared.timeZone, null);
        await badRequest((0, tournamentsService_1.updateTournamentSettings)(updateRequest(created.id, { matchDurationMinutes: 601 })));
        await badRequest((0, tournamentsService_1.updateTournamentSettings)(updateRequest(created.id, { timeZone: "Nowhere/Land" })));
    });
    (0, node_test_1.it)("reads an old tournament document without the new fields", async () => {
        const created = await (0, tournamentsService_1.createTournament)(createRequest());
        await firebase_1.db.doc(`tournaments/${created.id}`).update({
            matchDurationMinutes: null, matchEndMode: null, matchExtraMinutes: null, timeZone: null,
        });
        const updated = await (0, tournamentsService_1.updateTournamentSettings)(updateRequest(created.id));
        strict_1.default.equal(updated.matchDurationMinutes, 120);
        strict_1.default.equal(updated.matchEndMode, "STOP");
        strict_1.default.equal(updated.matchExtraMinutes, 15);
        strict_1.default.equal(updated.timeZone, null);
    });
});
//# sourceMappingURL=tournamentMatchSettings.integration.test.js.map