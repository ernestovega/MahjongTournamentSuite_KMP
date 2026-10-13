"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireTournamentReader = requireTournamentReader;
const globalRole_1 = require("../../models/globalRole");
const playerAccountsService_1 = require("../../services/playerAccountsService");
const httpError_1 = require("../httpError");
const requireTournamentEditor_1 = require("./requireTournamentEditor");
/**
 * Lets an editor of the tournament, an admin, or a player registered in the tournament read it.
 * It sets `res.locals.reader`. Editors who also play keep the editor view.
 */
async function requireTournamentReader(req, res, next) {
    const decoded = res.locals.auth;
    if (!decoded) {
        next((0, httpError_1.forbidden)("Auth context missing"));
        return;
    }
    if ((0, globalRole_1.hasAdminClaim)(decoded) || decoded.editor === true) {
        res.locals.reader = "EDITOR";
        await (0, requireTournamentEditor_1.requireTournamentEditor)(req, res, next);
        return;
    }
    const tournamentId = req.params.tournamentId;
    if (decoded.player === true && tournamentId) {
        const registration = await (0, playerAccountsService_1.getPlayerRegistration)(decoded.uid, tournamentId);
        if (registration) {
            res.locals.reader = "PLAYER";
            res.locals.playerRegistration = registration;
            next();
            return;
        }
    }
    next((0, httpError_1.forbidden)("You are not registered in this tournament"));
}
//# sourceMappingURL=requireTournamentReader.js.map