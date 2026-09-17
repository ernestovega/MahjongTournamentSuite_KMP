"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.playersRouter = playersRouter;
const express_1 = require("express");
const httpError_1 = require("../httpError");
const requireAdmin_1 = require("../middleware/requireAdmin");
const requireAuth_1 = require("../middleware/requireAuth");
const playersService_1 = require("../../services/playersService");
function playersRouter() {
    const router = (0, express_1.Router)();
    router.get("/", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            res.status(200).json({ players: await (0, playersService_1.listPlayers)() });
        }
        catch (error) {
            next(error);
        }
    });
    router.post("/", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const emaId = (0, playersService_1.validateEmaId)(req.body?.emaId);
            const name = String(req.body?.name ?? "").trim();
            const country = String(req.body?.country ?? "").trim();
            if (!name)
                throw (0, httpError_1.badRequest)("Player name is required");
            if (!country)
                throw (0, httpError_1.badRequest)("Player country is required");
            res.status(200).json(await (0, playersService_1.createPlayer)({ emaId, name, country }));
        }
        catch (error) {
            next(error);
        }
    });
    router.put("/:emaId", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const emaId = (0, playersService_1.validateEmaId)(req.params.emaId);
            const newEmaId = (0, playersService_1.validateEmaId)(req.body?.emaId ?? emaId);
            if (newEmaId !== emaId) {
                const decoded = res.locals.auth;
                if (decoded?.superadmin !== true)
                    throw (0, httpError_1.forbidden)("Superadmin required to change an EMA number");
            }
            const name = String(req.body?.name ?? "").trim();
            const country = String(req.body?.country ?? "").trim();
            if (!name)
                throw (0, httpError_1.badRequest)("Player name is required");
            if (!country)
                throw (0, httpError_1.badRequest)("Player country is required");
            await (0, playersService_1.updatePlayer)({ previousEmaId: emaId, emaId: newEmaId, name, country });
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
            res.status(200).json(await (0, playersService_1.updatePlayerPhoto)({ emaId, contentType, dataBase64 }));
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=players.routes.js.map