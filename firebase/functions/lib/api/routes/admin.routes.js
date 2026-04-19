"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.adminRouter = adminRouter;
const express_1 = require("express");
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
const requireAuth_1 = require("../middleware/requireAuth");
const requireSuperadmin_1 = require("../middleware/requireSuperadmin");
const usersService_1 = require("../../services/usersService");
const config_1 = require("../../config");
function requireBootstrapKey(req) {
    const expected = config_1.BOOTSTRAP_KEY.value();
    if (!expected) {
        throw new Error("Missing secret MTS_BOOTSTRAP_KEY");
    }
    const provided = req.header("x-bootstrap-key");
    if (!provided || provided != expected) {
        throw (0, httpError_1.forbidden)("Invalid bootstrap key");
    }
}
function adminRouter() {
    const router = (0, express_1.Router)();
    // One-time helper to set superadmin claim.
    router.post("/bootstrapSuperadmin", async (req, res, next) => {
        try {
            requireBootstrapKey(req);
            const uid = String(req.body?.uid ?? "").trim();
            if (!uid) {
                throw (0, httpError_1.badRequest)("Missing uid");
            }
            await firebase_1.auth.setCustomUserClaims(uid, { superadmin: true });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/whoami", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const decoded = res.locals.auth;
            res.status(200).json({ uid: decoded.uid, superadmin: decoded.superadmin === true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/users/lookup", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (req, res, next) => {
        try {
            const identifier = String(req.query.identifier ?? "").trim();
            if (!identifier) {
                throw (0, httpError_1.badRequest)("Missing identifier");
            }
            if (identifier.includes("@")) {
                const user = await firebase_1.auth.getUserByEmail(identifier);
                const profile = await (0, usersService_1.getUserProfile)(user.uid);
                res.status(200).json(profile);
                return;
            }
            const mapping = await (0, usersService_1.getEmaIdMapping)(identifier);
            const profile = await (0, usersService_1.getUserProfile)(mapping.uid);
            res.status(200).json(profile);
        }
        catch (e) {
            next(e);
        }
    });
    return router;
}
//# sourceMappingURL=admin.routes.js.map