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
            res.status(200).json(await (0, playersService_1.createPlayer)({ emaId, name, country }));
        }
        catch (error) {
            next(error);
        }
    });
    router.put("/:emaId", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const emaId = (0, playersService_1.validateEmaId)(req.params.emaId);
            const name = String(req.body?.name ?? "").trim();
            const country = String(req.body?.country ?? "").trim();
            if (!name)
                throw (0, httpError_1.badRequest)("Player name is required");
            await (0, playersService_1.updatePlayer)({ emaId, name, country });
            res.status(200).json({ ok: true });
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=players.routes.js.map