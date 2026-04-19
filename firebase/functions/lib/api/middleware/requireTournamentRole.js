"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireTournamentRole = requireTournamentRole;
const firebase_1 = require("../../firebase");
const role_1 = require("../../models/role");
const httpError_1 = require("../httpError");
function requireTournamentRole(minimumRole) {
    return async function (req, res, next) {
        const decoded = res.locals.auth;
        if (!decoded) {
            next((0, httpError_1.forbidden)("Auth context missing"));
            return;
        }
        if (decoded.superadmin === true) {
            next();
            return;
        }
        const tournamentId = req.params.tournamentId;
        if (!tournamentId) {
            next((0, httpError_1.notFound)("Missing tournamentId"));
            return;
        }
        const uid = decoded.uid;
        const memberRef = firebase_1.db.doc(`tournaments/${tournamentId}/members/${uid}`);
        const memberSnap = await memberRef.get();
        if (!memberSnap.exists) {
            next((0, httpError_1.forbidden)("Not a tournament member"));
            return;
        }
        const role = memberSnap.get("role");
        if (!role) {
            next((0, httpError_1.forbidden)("Missing role"));
            return;
        }
        if (!(0, role_1.roleMeetsMinimum)(role, minimumRole)) {
            next((0, httpError_1.forbidden)("Insufficient role"));
            return;
        }
        next();
    };
}
//# sourceMappingURL=requireTournamentRole.js.map