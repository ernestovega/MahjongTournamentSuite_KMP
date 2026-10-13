"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireRegisteredPlayer = requireRegisteredPlayer;
const playerAccountsService_1 = require("../../services/playerAccountsService");
const httpError_1 = require("../httpError");
/** Lets only a player account that is registered in the tournament of the route. Run it after requireAuth. */
async function requireRegisteredPlayer(req, res, next) {
    const decoded = res.locals.auth;
    if (decoded?.player !== true) {
        next((0, httpError_1.forbidden)("Player account required"));
        return;
    }
    try {
        const registration = await (0, playerAccountsService_1.getPlayerRegistration)(decoded.uid, req.params.tournamentId);
        if (!registration) {
            next((0, httpError_1.forbidden)("You are not registered in this tournament"));
            return;
        }
        res.locals.playerRegistration = registration;
        next();
    }
    catch (error) {
        next(error);
    }
}
//# sourceMappingURL=requireRegisteredPlayer.js.map