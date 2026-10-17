"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.configRouter = configRouter;
const express_1 = require("express");
const featureFlagsService_1 = require("../../services/featureFlagsService");
function configRouter() {
    const router = (0, express_1.Router)();
    // Public: the sign-in screens also need the flags.
    router.get("/", async (_req, res, next) => {
        try {
            res.status(200).json(await (0, featureFlagsService_1.getFeatureFlags)());
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=config.routes.js.map