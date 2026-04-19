"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.authRouter = authRouter;
const express_1 = require("express");
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
const requireAuth_1 = require("../middleware/requireAuth");
const firebaseAuthRest_1 = require("../../services/firebaseAuthRest");
const usersService_1 = require("../../services/usersService");
function authRouter() {
    const router = (0, express_1.Router)();
    router.post("/signUp", async (req, res, next) => {
        let createdUid = null;
        try {
            const email = String(req.body?.email ?? "").trim();
            const password = String(req.body?.password ?? "");
            const emaId = String(req.body?.emaId ?? "").trim();
            if (!email || !password || !emaId) {
                throw (0, httpError_1.badRequest)("Missing email, password, or emaId");
            }
            // Pre-check to avoid orphan Auth accounts in the common case.
            await (0, usersService_1.assertEmaIdAvailable)(emaId);
            const tokens = await (0, firebaseAuthRest_1.signUpWithEmailPassword)(email, password);
            createdUid = tokens.uid;
            await (0, usersService_1.linkEmaIdToUser)(tokens.uid, email, emaId);
            res.status(200).json({
                idToken: tokens.idToken,
                refreshToken: tokens.refreshToken,
                uid: tokens.uid,
            });
        }
        catch (e) {
            // If a rare race caused emaId conflict after Auth user creation, clean it up.
            if (createdUid &&
                e instanceof httpError_1.HttpError &&
                e.code === "conflict") {
                try {
                    await firebase_1.auth.deleteUser(createdUid);
                }
                catch {
                    // ignore cleanup failures
                }
            }
            next(e);
        }
    });
    router.post("/signIn", async (req, res, next) => {
        try {
            const identifier = String(req.body?.identifier ?? "").trim();
            const password = String(req.body?.password ?? "");
            if (!identifier || !password) {
                throw (0, httpError_1.badRequest)("Missing identifier or password");
            }
            const email = identifier.includes("@") ? identifier : await (0, usersService_1.getEmailForEmaId)(identifier);
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