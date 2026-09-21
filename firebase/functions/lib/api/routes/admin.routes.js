"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.adminRouter = adminRouter;
const express_1 = require("express");
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
const requireAuth_1 = require("../middleware/requireAuth");
const requireSuperadmin_1 = require("../middleware/requireSuperadmin");
const firebaseAuthRest_1 = require("../../services/firebaseAuthRest");
const usersService_1 = require("../../services/usersService");
const config_1 = require("../../config");
const role_1 = require("../../models/role");
function parseGlobalUserRole(value) {
    return value === "REGULAR" || value === "SUPERADMIN" ? value : null;
}
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
        const role = (0, role_1.parseRole)(record?.role);
        if (!tournamentId || !role)
            throw (0, httpError_1.badRequest)("Invalid tournament assignment");
        return { tournamentId, tournamentName: "", role };
    });
}
async function assertSuperadminRemainsEnabled(currentRole, currentDisabled, nextRole, nextDisabled) {
    if (currentRole !== "SUPERADMIN" || currentDisabled || (nextRole === "SUPERADMIN" && !nextDisabled))
        return;
    if (await (0, usersService_1.countEnabledSuperadmins)() <= 1) {
        throw (0, httpError_1.badRequest)("The last enabled superadmin cannot be demoted or disabled");
    }
}
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
            res.status(200).json({ uid: decoded.uid, admin: false, superadmin: decoded.superadmin === true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/users", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (_req, res, next) => {
        try {
            res.status(200).json({ users: await (0, usersService_1.listManagedUsers)() });
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/users", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (req, res, next) => {
        let createdUid = null;
        try {
            const email = requireEmail(req.body?.email, "email");
            const alias = String(req.body?.alias ?? "").trim();
            const role = parseGlobalUserRole(req.body?.role);
            if (!role)
                throw (0, httpError_1.badRequest)("Invalid role");
            const tournamentAssignments = parseTournamentAssignments(req.body?.tournamentAssignments);
            await (0, usersService_1.assertEmailAvailable)(email);
            const user = await firebase_1.auth.createUser({ email, disabled: false });
            createdUid = user.uid;
            await (0, usersService_1.createUserProfile)(user.uid, email, alias);
            await (0, usersService_1.setGlobalUserRole)(user.uid, role);
            await (0, usersService_1.syncUserTournamentAssignments)(user.uid, tournamentAssignments);
            await (0, firebaseAuthRest_1.sendPasswordResetEmail)(email);
            res.status(201).json(await (0, usersService_1.getManagedUser)(user.uid));
        }
        catch (e) {
            if (createdUid) {
                await Promise.allSettled([
                    firebase_1.auth.deleteUser(createdUid),
                    firebase_1.db.doc(`users/${createdUid}`).delete(),
                    (0, usersService_1.syncUserTournamentAssignments)(createdUid, []),
                ]);
            }
            next(e);
        }
    });
    router.put("/users/:uid", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (req, res, next) => {
        try {
            const actor = res.locals.auth;
            const current = await (0, usersService_1.getManagedUser)(req.params.uid);
            const email = requireEmail(req.body?.email, "email");
            const alias = String(req.body?.alias ?? "").trim();
            const role = parseGlobalUserRole(req.body?.role);
            if (!role)
                throw (0, httpError_1.badRequest)("Invalid role");
            const tournamentAssignments = parseTournamentAssignments(req.body?.tournamentAssignments);
            if (actor.uid === current.uid && role !== current.role) {
                throw (0, httpError_1.badRequest)("You cannot change your own role");
            }
            await assertSuperadminRemainsEnabled(current.role, current.disabled, role, current.disabled);
            await (0, usersService_1.assertEmailAvailable)(email, current.uid);
            await (0, usersService_1.updateManagedUser)({ uid: current.uid, email, alias, role, tournamentAssignments });
            res.status(200).json(await (0, usersService_1.getManagedUser)(current.uid));
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/users/:uid/disabled", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (req, res, next) => {
        try {
            const actor = res.locals.auth;
            const disabled = req.body?.disabled;
            if (typeof disabled !== "boolean")
                throw (0, httpError_1.badRequest)("disabled must be a boolean");
            const current = await (0, usersService_1.getManagedUser)(req.params.uid);
            if (actor.uid === current.uid && disabled) {
                throw (0, httpError_1.badRequest)("You cannot disable your own account");
            }
            await assertSuperadminRemainsEnabled(current.role, current.disabled, current.role, disabled);
            await (0, usersService_1.setManagedUserDisabled)(current.uid, disabled);
            res.status(200).json(await (0, usersService_1.getManagedUser)(current.uid));
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
            if (!identifier.includes("@"))
                throw (0, httpError_1.badRequest)("Enter a user email address");
            const profile = await firebase_1.auth.getUserByEmail(identifier).then((user) => (0, usersService_1.getUserProfile)(user.uid));
            res.status(200).json(profile);
        }
        catch (e) {
            next(e);
        }
    });
    return router;
}
//# sourceMappingURL=admin.routes.js.map