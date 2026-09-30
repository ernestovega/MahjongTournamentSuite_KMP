"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.syncRouter = syncRouter;
const express_1 = require("express");
const requireAuth_1 = require("../middleware/requireAuth");
const dataVersionsService_1 = require("../../services/dataVersionsService");
function syncRouter() {
    const router = (0, express_1.Router)();
    router.get("/manifest", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            res.setHeader("Cache-Control", "no-store");
            res.status(200).json({ resources: await (0, dataVersionsService_1.getGlobalDataVersions)() });
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=sync.routes.js.map