"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.playersRouter = playersRouter;
const express_1 = require("express");
const activityLog_1 = require("../../services/activityLog");
const changeLog_1 = require("../../services/changeLog");
const httpError_1 = require("../httpError");
const requireAdmin_1 = require("../middleware/requireAdmin");
const requireAuth_1 = require("../middleware/requireAuth");
const playerAccountsService_1 = require("../../services/playerAccountsService");
const playersService_1 = require("../../services/playersService");
function playersRouter() {
    const router = (0, express_1.Router)();
    router.get("/", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const decoded = res.locals.auth;
            if (decoded.admin === true || decoded.editor === true) {
                res.status(200).json({ players: await (0, playersService_1.listPlayers)() });
                return;
            }
            if (decoded.player !== true)
                throw (0, httpError_1.forbidden)("Editor or player required");
            const tournamentIds = await (0, playerAccountsService_1.listPlayerTournamentIds)(decoded.uid);
            res.status(200).json({ players: await (0, playersService_1.listPlayersOfTournaments)(tournamentIds) });
        }
        catch (error) {
            next(error);
        }
    });
    router.post("/", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const emaId = (0, playersService_1.validateEmaId)(req.body?.emaId);
            const firstName = String(req.body?.firstName ?? "").trim();
            const lastName = String(req.body?.lastName ?? "").trim();
            const country = String(req.body?.country ?? "").trim().toUpperCase();
            if (!firstName || !lastName)
                throw (0, httpError_1.badRequest)("First name and last name are required");
            if (!/^[A-Z]{3}$/.test(country))
                throw (0, httpError_1.badRequest)("Player country must use a three-letter EMA code");
            const created = await (0, playersService_1.createPlayer)({ emaId, firstName, lastName, country });
            await (0, activityLog_1.recordActivity)({
                action: "registry.create", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), target: `EMA ${emaId}`,
                details: { firstName, lastName, country },
            });
            res.status(200).json(created);
        }
        catch (error) {
            next(error);
        }
    });
    router.put("/:emaId", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const emaId = (0, playersService_1.validateEmaId)(req.params.emaId);
            const newEmaId = (0, playersService_1.validateEmaId)(req.body?.emaId ?? emaId);
            const firstName = String(req.body?.firstName ?? "").trim();
            const lastName = String(req.body?.lastName ?? "").trim();
            const country = String(req.body?.country ?? "").trim().toUpperCase();
            if (!firstName || !lastName)
                throw (0, httpError_1.badRequest)("First name and last name are required");
            if (!/^[A-Z]{3}$/.test(country))
                throw (0, httpError_1.badRequest)("Player country must use a three-letter EMA code");
            await (0, playersService_1.updatePlayer)({ previousEmaId: emaId, emaId: newEmaId, firstName, lastName, country });
            await (0, activityLog_1.recordActivity)({
                action: "registry.update", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), target: `EMA ${emaId}`,
                details: { emaId: newEmaId, firstName, lastName, country },
            });
            res.status(200).json({ ok: true });
        }
        catch (error) {
            next(error);
        }
    });
    router.put("/:emaId/photo", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const emaId = (0, playersService_1.validateEmaId)(req.params.emaId);
            const contentType = String(req.body?.contentType ?? "").trim().toLowerCase();
            const dataBase64 = String(req.body?.dataBase64 ?? "").trim();
            if (!dataBase64)
                throw (0, httpError_1.badRequest)("Photo data is required");
            const updated = await (0, playersService_1.updatePlayerPhoto)({ emaId, contentType, dataBase64 });
            await (0, activityLog_1.recordActivity)({
                action: "registry.photo", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), target: `EMA ${emaId}`, details: { contentType },
            });
            res.status(200).json(updated);
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=players.routes.js.map