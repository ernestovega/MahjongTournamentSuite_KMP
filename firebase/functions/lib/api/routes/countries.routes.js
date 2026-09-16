"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.countriesRouter = countriesRouter;
const express_1 = require("express");
const requireAuth_1 = require("../middleware/requireAuth");
const countriesService_1 = require("../../services/countriesService");
function countriesRouter() {
    const router = (0, express_1.Router)();
    router.get("/", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            res.status(200).json({ countries: await (0, countriesService_1.listCountries)() });
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=countries.routes.js.map