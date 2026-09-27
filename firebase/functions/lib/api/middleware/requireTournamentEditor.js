"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireTournamentEditor = requireTournamentEditor;
const firebase_1 = require("../../firebase");
const globalRole_1 = require("../../models/globalRole");
const httpError_1 = require("../httpError");
async function requireTournamentEditor(req, res, next) {
    const decoded = res.locals.auth;
    if (!decoded) {
        next((0, httpError_1.forbidden)("Auth context missing"));
        return;
    }
    if ((0, globalRole_1.hasAdminClaim)(decoded)) {
        next();
        return;
    }
    const tournamentId = req.params.tournamentId;
    if (!tournamentId) {
        next((0, httpError_1.notFound)("Missing tournamentId"));
        return;
    }
    const member = await firebase_1.db.doc(`tournaments/${tournamentId}/members/${decoded.uid}`).get();
    if (!member.exists) {
        next((0, httpError_1.forbidden)("Editor is not assigned to this tournament"));
        return;
    }
    next();
}
//# sourceMappingURL=requireTournamentEditor.js.map