"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.signInError = signInError;
exports.authRouter = authRouter;
exports.readContentType = readContentType;
exports.readPhotoData = readPhotoData;
const express_1 = require("express");
const httpError_1 = require("../httpError");
const requireAuth_1 = require("../middleware/requireAuth");
const firebaseAuthRest_1 = require("../../services/firebaseAuthRest");
const firebase_1 = require("../../firebase");
const globalRole_1 = require("../../models/globalRole");
const usersService_1 = require("../../services/usersService");
/** Turns the error code of the sign-in service into an answer that the apps can show to the user. */
function signInError(error) {
    const code = error instanceof Error ? error.message : "";
    if (["INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND"].includes(code))
        return (0, httpError_1.unauthenticated)("INVALID_LOGIN_CREDENTIALS");
    if (code.startsWith("INVALID_EMAIL"))
        return (0, httpError_1.badRequest)("INVALID_EMAIL");
    if (code === "USER_DISABLED")
        return (0, httpError_1.forbidden)("Forbidden: this account is disabled");
    if (code.startsWith("TOO_MANY_ATTEMPTS"))
        return new httpError_1.HttpError(429, "too_many_requests", "Too many attempts");
    return error;
}
function authRouter() {
    const router = (0, express_1.Router)();
    router.post("/signIn", async (req, res, next) => {
        try {
            const email = String(req.body?.email ?? "").trim();
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
            next(signInError(e));
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
            const [profile, user] = await Promise.all([(0, usersService_1.getOwnProfile)(decoded.uid), firebase_1.auth.getUser(decoded.uid)]);
            res.status(200).json({ ...profile, roles: (0, globalRole_1.getGlobalUserRoles)(user) });
        }
        catch (e) {
            next(e);
        }
    });
    // Every signed-in user can change the own alias and the own photo. The EMA id stays as the admins set it.
    router.put("/me", requireAuth_1.requireAuth, async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            await (0, usersService_1.updateOwnAlias)(decoded.uid, req.body?.alias);
            const [profile, user] = await Promise.all([(0, usersService_1.getOwnProfile)(decoded.uid), firebase_1.auth.getUser(decoded.uid)]);
            res.status(200).json({ ...profile, roles: (0, globalRole_1.getGlobalUserRoles)(user) });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/me/photo", requireAuth_1.requireAuth, async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            await (0, usersService_1.setUserPhoto)(decoded.uid, readContentType(req.body), readPhotoData(req.body));
            const [profile, user] = await Promise.all([(0, usersService_1.getOwnProfile)(decoded.uid), firebase_1.auth.getUser(decoded.uid)]);
            res.status(200).json({ ...profile, roles: (0, globalRole_1.getGlobalUserRoles)(user) });
        }
        catch (e) {
            next(e);
        }
    });
    router.delete("/me/photo", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const decoded = res.locals.auth;
            await (0, usersService_1.clearUserPhoto)(decoded.uid);
            const [profile, user] = await Promise.all([(0, usersService_1.getOwnProfile)(decoded.uid), firebase_1.auth.getUser(decoded.uid)]);
            res.status(200).json({ ...profile, roles: (0, globalRole_1.getGlobalUserRoles)(user) });
        }
        catch (e) {
            next(e);
        }
    });
    return router;
}
function readContentType(body) {
    return String(body?.contentType ?? "").trim().toLowerCase();
}
function readPhotoData(body) {
    const data = String(body?.dataBase64 ?? "").trim();
    if (!data)
        throw (0, httpError_1.badRequest)("Photo data is required");
    return data;
}
//# sourceMappingURL=auth.routes.js.map