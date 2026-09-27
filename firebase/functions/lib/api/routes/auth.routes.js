"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.authRouter = authRouter;
const express_1 = require("express");
const httpError_1 = require("../httpError");
const requireAuth_1 = require("../middleware/requireAuth");
const firebaseAuthRest_1 = require("../../services/firebaseAuthRest");
const usersService_1 = require("../../services/usersService");
function authRouter() {
    const router = (0, express_1.Router)();
    router.post("/signIn", async (req, res, next) => {
        try {
            // Accept identifier during the rollout of the email-only API.
            const email = String(req.body?.email ?? req.body?.identifier ?? "").trim();
            const password = String(req.body?.password ?? "");
            if (!email || !password) {
                throw (0, httpError_1.badRequest)("Missing email or password");
            }
            const tokens = await (0, firebaseAuthRest_1.signInWithEmailPassword)(email, password);
            res.status(200).json({
                idToken: tokens.idToken,
                refreshToken: tokens.refreshToken,
                uid: tokens.uid,
            });
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/passwordReset", async (req, res, next) => {
        try {
            const email = String(req.body?.email ?? "").trim();
            if (!email) {
                throw (0, httpError_1.badRequest)("Missing email");
            }
            await (0, firebaseAuthRest_1.sendPasswordResetEmail)(email);
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/refresh", async (req, res, next) => {
        try {
            const refreshToken = String(req.body?.refreshToken ?? "");
            if (!refreshToken) {
                throw (0, httpError_1.badRequest)("Missing refreshToken");
            }
            const tokens = await (0, firebaseAuthRest_1.refreshIdToken)(refreshToken);
            res.status(200).json(tokens);
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/me", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const profile = await (0, usersService_1.getUserProfile)(decoded.uid);
            res.status(200).json(profile);
        }
        catch (e) {
            next(e);
        }
    });
    return router;
}
//# sourceMappingURL=auth.routes.js.map