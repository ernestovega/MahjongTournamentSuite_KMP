"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentsService_1 = require("./tournamentsService");
(0, node_test_1.default)("deletes the stored logo before the tournament tree", async () => {
    const calls = [];
    await (0, tournamentsService_1.deleteTournamentResources)({
        logoPath: "tournamentLogos/tournament-1.png",
        deleteLogo: async (logoPath) => {
            calls.push(`logo:${logoPath}`);
        },
        deleteTournamentTree: async () => {
            calls.push("tree");
        },
    });
    strict_1.default.deepEqual(calls, ["logo:tournamentLogos/tournament-1.png", "tree"]);
});
(0, node_test_1.default)("deletes the tournament tree when no stored logo exists", async () => {
    const calls = [];
    await (0, tournamentsService_1.deleteTournamentResources)({
        logoPath: "",
        deleteLogo: async () => {
            calls.push("logo");
        },
        deleteTournamentTree: async () => {
            calls.push("tree");
        },
    });
    strict_1.default.deepEqual(calls, ["tree"]);
});
(0, node_test_1.default)("keeps the tournament tree when stored logo deletion fails", async () => {
    let treeDeleted = false;
    await strict_1.default.rejects((0, tournamentsService_1.deleteTournamentResources)({
        logoPath: "tournamentLogos/tournament-1.png",
        deleteLogo: async () => {
            throw new Error("Storage unavailable");
        },
        deleteTournamentTree: async () => {
            treeDeleted = true;
        },
    }), /Storage unavailable/);
    strict_1.default.equal(treeDeleted, false);
});
//# sourceMappingURL=tournamentsService.test.js.map