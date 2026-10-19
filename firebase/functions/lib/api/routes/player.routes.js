"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.playerRouter = playerRouter;
const express_1 = require("express");
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
const requireAuth_1 = require("../middleware/requireAuth");
const requireRegisteredPlayer_1 = require("../middleware/requireRegisteredPlayer");
const playerAccountsService_1 = require("../../services/playerAccountsService");
const playerHandService_1 = require("../../services/playerHandService");
/** Routes for player accounts. The invitation route is public because a new player has no account yet. */
function playerRouter() {
    const router = (0, express_1.Router)();
    router.post("/invitations/accept", async (req, res, next) => {
        try {
            const token = String(req.body?.token ?? "").trim();
            if (!token)
                throw (0, httpError_1.badRequest)("Missing token");
            const password = typeof req.body?.password === "string" ? req.body.password : undefined;
            const bearer = req.header("authorization")?.match(/^Bearer\s+(.+)$/i)?.[1];
            // An existing account signs in first. A new account sends a password instead.
            const actorUid = bearer ? (await firebase_1.auth.verifyIdToken(bearer, true).catch(() => null))?.uid : undefined;
            const result = await (0, playerAccountsService_1.acceptInvitation)({ token, password, actorUid });
            res.status(200).json(result);
        }
        catch (e) {
            next(e);
        }
    });
    const tableRoute = "/tournaments/:tournamentId/tables/:roundId/:tableId";
    const tableParams = (req) => {
        const roundId = Number(req.params.roundId);
        const tableId = Number(req.params.tableId);
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
            throw (0, httpError_1.badRequest)("roundId and tableId must be positive integers");
        }
        return { tournamentId: req.params.tournamentId, roundId, tableId };
    };
    const uidOf = (res) => res.locals.auth.uid;
    // The app shows this result to the table first. Nothing is written.
    router.post(`${tableRoute}/hands/preview`, requireAuth_1.requireAuth, requireRegisteredPlayer_1.requireRegisteredPlayer, async (req, res, next) => {
        try {
            const input = (0, playerHandService_1.parseHandInput)(req.body);
            res.status(200).json(await (0, playerHandService_1.previewHand)({ ...tableParams(req), uid: uidOf(res), input }));
        }
        catch (e) {
            next(e);
        }
    });
    // Penalties belong to the hand in play. They do not finish the hand.
    router.post(`${tableRoute}/penalties`, requireAuth_1.requireAuth, requireRegisteredPlayer_1.requireRegisteredPlayer, async (req, res, next) => {
        try {
            const penalties = (0, playerHandService_1.parsePenaltyInput)(req.body);
            const expectedVersion = req.body?.expectedVersion;
            if (!Number.isInteger(expectedVersion) || expectedVersion < 0) {
                throw (0, httpError_1.badRequest)("expectedVersion must be the table version that the app shows");
            }
            res.status(200).json(await (0, playerHandService_1.addPenalties)({ ...tableParams(req), uid: uidOf(res), penalties, expectedVersion }));
        }
        catch (e) {
            next(e);
        }
    });
    router.post(`${tableRoute}/hands`, requireAuth_1.requireAuth, requireRegisteredPlayer_1.requireRegisteredPlayer, async (req, res, next) => {
        try {
            const input = (0, playerHandService_1.parseHandInput)(req.body);
            const expectedVersion = req.body?.expectedVersion;
            if (!Number.isInteger(expectedVersion) || expectedVersion < 0) {
                throw (0, httpError_1.badRequest)("expectedVersion must be the table version of the preview");
            }
            res.status(200).json(await (0, playerHandService_1.recordHand)({ ...tableParams(req), uid: uidOf(res), input, expectedVersion }));
        }
        catch (e) {
            next(e);
        }
    });
    return router;
}
//# sourceMappingURL=player.routes.js.map