"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireTournamentEditor = requireTournamentEditor;
const firebase_1 = require("../../firebase");
const globalRole_1 = require("../../models/globalRole");
const httpError_1 = require("../httpError");
// Editors are checked on every request, so a positive result is reused for a short time.
const MEMBERSHIP_TTL_MS = 30000;
const MAX_CACHED_MEMBERSHIPS = 2000;
const membershipValidUntil = new Map();
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
    const cacheKey = `${tournamentId}/${decoded.uid}`;
    const now = Date.now();
    if ((membershipValidUntil.get(cacheKey) ?? 0) > now) {
        next();
        return;
    }
    const member = await firebase_1.db.doc(`tournaments/${tournamentId}/members/${decoded.uid}`).get();
    if (!member.exists) {
        membershipValidUntil.delete(cacheKey);
        next((0, httpError_1.forbidden)("Editor is not assigned to this tournament"));
        return;
    }
    if (membershipValidUntil.size >= MAX_CACHED_MEMBERSHIPS)
        membershipValidUntil.clear();
    membershipValidUntil.set(cacheKey, now + MEMBERSHIP_TTL_MS);
    next();
}
//# sourceMappingURL=requireTournamentEditor.js.map