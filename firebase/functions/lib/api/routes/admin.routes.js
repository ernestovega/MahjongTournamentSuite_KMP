"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.adminRouter = adminRouter;
const express_1 = require("express");
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
const requireAdmin_1 = require("../middleware/requireAdmin");
const requireAuth_1 = require("../middleware/requireAuth");
const globalRole_1 = require("../../models/globalRole");
const firebaseAuthRest_1 = require("../../services/firebaseAuthRest");
const usersService_1 = require("../../services/usersService");
const config_1 = require("../../config");
const dataVersionsService_1 = require("../../services/dataVersionsService");
function requireEmail(value, fieldName) {
    const email = String(value ?? "").trim();
    if (!email || !email.includes("@"))
        throw (0, httpError_1.badRequest)(`${fieldName} must be a valid email`);
    return email;
}
function parseTournamentAssignments(value) {
    if (!Array.isArray(value))
        throw (0, httpError_1.badRequest)("tournamentAssignments must be an array");
    return value.map((item) => {
        const record = item != null && typeof item === "object" ? item : null;
        const tournamentId = String(record?.tournamentId ?? "").trim();
        if (!tournamentId)
            throw (0, httpError_1.badRequest)("Invalid tournament assignment");
        return { tournamentId, tournamentName: "" };
    });
}
async function assignmentScopeFor(actor) {
    return (0, globalRole_1.hasAdminClaim)(actor) ? undefined : (0, usersService_1.listAssignedTournamentIds)(actor.uid);
}
async function assertAdminRemainsEnabled(currentRole, currentDisabled, nextRole, nextDisabled) {
    if (currentRole !== "ADMIN" || currentDisabled || (nextRole === "ADMIN" && !nextDisabled))
        return;
    if (await (0, usersService_1.countEnabledAdmins)() <= 1) {
        throw (0, httpError_1.badRequest)("The last enabled admin cannot be demoted or disabled");
    }
}
function requireBootstrapKey(req) {
    const expected = config_1.BOOTSTRAP_KEY.value();
    if (!expected)
        throw new Error("Missing secret MTS_BOOTSTRAP_KEY");
    const provided = req.header("x-bootstrap-key");
    if (!provided || provided !== expected)
        throw (0, httpError_1.forbidden)("Invalid bootstrap key");
}
function adminRouter() {
    const router = (0, express_1.Router)();
    router.post("/bootstrapAdmin", async (req, res, next) => {
        try {
            requireBootstrapKey(req);
            if (await (0, usersService_1.countEnabledAdmins)() > 0) {
                throw (0, httpError_1.forbidden)("An enabled admin already exists");
            }
            const uid = String(req.body?.uid ?? "").trim();
            if (!uid)
                throw (0, httpError_1.badRequest)("Missing uid");
            const user = await firebase_1.auth.getUser(uid);
            if (!user.email)
                throw (0, httpError_1.badRequest)("The bootstrap account must have an email address");
            await (0, usersService_1.createUserProfile)(uid, user.email, user.displayName ?? "");
            const claims = { ...(user.customClaims ?? {}), admin: true };
            delete claims.superadmin;
            await firebase_1.auth.setCustomUserClaims(uid, claims);
            await (0, dataVersionsService_1.bumpGlobalDataVersion)("users");
            res.status(200).json({ ok: true });
        }
        catch (error) {
            next(error);
        }
    });
    router.get("/whoami", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const actor = res.locals.auth;
            res.status(200).json({ uid: actor.uid, role: (0, globalRole_1.hasAdminClaim)(actor) ? "ADMIN" : "EDITOR" });
        }
        catch (error) {
            next(error);
        }
    });
    router.get("/users", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const actor = res.locals.auth;
            res.status(200).json({ users: await (0, usersService_1.listManagedUsers)(await assignmentScopeFor(actor)) });
        }
        catch (error) {
            next(error);
        }
    });
    router.post("/users", requireAuth_1.requireAuth, async (req, res, next) => {
        let createdUid = null;
        try {
            const actor = res.locals.auth;
            const email = requireEmail(req.body?.email, "email");
            const alias = String(req.body?.alias ?? "").trim();
            const role = (0, globalRole_1.parseGlobalUserRole)(req.body?.role);
            if (!role)
                throw (0, httpError_1.badRequest)("Invalid role");
            if (role === "ADMIN" && !(0, globalRole_1.hasAdminClaim)(actor))
                throw (0, httpError_1.forbidden)("Only admins can create admins");
            const assignmentScope = await assignmentScopeFor(actor);
            const tournamentAssignments = role === "ADMIN" ? [] : parseTournamentAssignments(req.body?.tournamentAssignments);
            await (0, usersService_1.assertEmailAvailable)(email);
            const user = await firebase_1.auth.createUser({ email, disabled: false });
            createdUid = user.uid;
            await (0, usersService_1.createUserProfile)(user.uid, email, alias);
            await (0, usersService_1.setGlobalUserRole)(user.uid, role);
            await (0, usersService_1.syncUserTournamentAssignments)(user.uid, tournamentAssignments, assignmentScope);
            await (0, firebaseAuthRest_1.sendPasswordResetEmail)(email);
            await (0, dataVersionsService_1.bumpGlobalDataVersion)("users");
            res.status(201).json(await (0, usersService_1.getManagedUser)(user.uid, assignmentScope));
        }
        catch (error) {
            if (createdUid) {
                await Promise.allSettled([
                    firebase_1.auth.deleteUser(createdUid),
                    firebase_1.db.doc(`users/${createdUid}`).delete(),
                    (0, usersService_1.syncUserTournamentAssignments)(createdUid, []),
                ]);
            }
            next(error);
        }
    });
    router.put("/users/:uid", requireAuth_1.requireAuth, async (req, res, next) => {
        try {
            const actor = res.locals.auth;
            const current = await (0, usersService_1.getManagedUser)(req.params.uid);
            const role = (0, globalRole_1.parseGlobalUserRole)(req.body?.role);
            if (!role)
                throw (0, httpError_1.badRequest)("Invalid role");
            if (!(0, globalRole_1.hasAdminClaim)(actor) && (current.role === "ADMIN" || role === "ADMIN")) {
                throw (0, httpError_1.forbidden)("Editors cannot modify admin accounts");
            }
            if (actor.uid === current.uid && role !== current.role) {
                throw (0, httpError_1.badRequest)("You cannot change your own role");
            }
            const email = requireEmail(req.body?.email, "email");
            const alias = String(req.body?.alias ?? "").trim();
            const assignmentScope = await assignmentScopeFor(actor);
            const tournamentAssignments = role === "ADMIN" ? [] : parseTournamentAssignments(req.body?.tournamentAssignments);
            await assertAdminRemainsEnabled(current.role, current.disabled, role, current.disabled);
            await (0, usersService_1.assertEmailAvailable)(email, current.uid);
            await (0, usersService_1.updateManagedUser)({
                uid: current.uid,
                email,
                alias,
                role,
                tournamentAssignments,
                assignmentScope,
            });
            await (0, dataVersionsService_1.bumpGlobalDataVersion)("users");
            res.status(200).json(await (0, usersService_1.getManagedUser)(current.uid, assignmentScope));
        }
        catch (error) {
            next(error);
        }
    });
    router.put("/users/:uid/disabled", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const actor = res.locals.auth;
            const disabled = req.body?.disabled;
            if (typeof disabled !== "boolean")
                throw (0, httpError_1.badRequest)("disabled must be a boolean");
            const current = await (0, usersService_1.getManagedUser)(req.params.uid);
            if (actor.uid === current.uid && disabled)
                throw (0, httpError_1.badRequest)("You cannot disable your own account");
            await assertAdminRemainsEnabled(current.role, current.disabled, current.role, disabled);
            await (0, usersService_1.setManagedUserDisabled)(current.uid, disabled);
            await (0, dataVersionsService_1.bumpGlobalDataVersion)("users");
            res.status(200).json(await (0, usersService_1.getManagedUser)(current.uid));
        }
        catch (error) {
            next(error);
        }
    });
    router.get("/users/lookup", requireAuth_1.requireAuth, async (req, res, next) => {
        try {
            const identifier = String(req.query.identifier ?? "").trim();
            if (!identifier)
                throw (0, httpError_1.badRequest)("Missing identifier");
            if (!identifier.includes("@"))
                throw (0, httpError_1.badRequest)("Enter a user email address");
            const profile = await firebase_1.auth.getUserByEmail(identifier).then((user) => (0, usersService_1.getUserProfile)(user.uid));
            res.status(200).json(profile);
        }
        catch (error) {
            next(error);
        }
    });
    return router;
}
//# sourceMappingURL=admin.routes.js.map