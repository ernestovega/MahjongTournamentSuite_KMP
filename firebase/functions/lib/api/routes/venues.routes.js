"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.venuesRouter = venuesRouter;
const express_1 = require("express");
const requireAuth_1 = require("../middleware/requireAuth");
const requireEditor_1 = require("../middleware/requireEditor");
const mapLinkResolver_1 = require("../../services/mapLinkResolver");
/** Routes for the venue form. The form turns a short Google Maps link into a full link and reads the place from it. */
function venuesRouter() {
    const router = (0, express_1.Router)();
    router.post("/resolve-link", requireAuth_1.requireAuth, requireEditor_1.requireEditor, async (req, res, next) => {
        try {
            res.status(200).json({ url: await (0, mapLinkResolver_1.resolveMapLink)(req.body?.url) });
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=venues.routes.js.map