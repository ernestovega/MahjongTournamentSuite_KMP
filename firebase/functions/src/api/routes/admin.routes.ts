import { Router } from "express";

import { auth, db } from "../../firebase";
import { badRequest, forbidden } from "../httpError";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireAuth } from "../middleware/requireAuth";
import { hasAdminClaim, parseGlobalUserRole } from "../../models/globalRole";
import { sendPasswordResetEmail } from "../../services/firebaseAuthRest";
import {
  assertEmailAvailable,
  countEnabledAdmins,
  createUserProfile,
  getManagedUser,
  getUserProfile,
  listAssignedTournamentIds,
  listManagedUsers,
  setGlobalUserRole,
  setManagedUserDisabled,
  syncUserTournamentAssignments,
  updateManagedUser,
  type GlobalUserRole,
  type TournamentAssignment,
} from "../../services/usersService";
import { BOOTSTRAP_KEY } from "../../config";

type Actor = { uid: string; admin?: boolean };

function requireEmail(value: unknown, fieldName: string): string {
  const email = String(value ?? "").trim();
  if (!email || !email.includes("@")) throw badRequest(`${fieldName} must be a valid email`);
  return email;
}

function parseTournamentAssignments(value: unknown): TournamentAssignment[] {
  if (!Array.isArray(value)) throw badRequest("tournamentAssignments must be an array");
  return value.map((item) => {
    const record = item != null && typeof item === "object" ? item as Record<string, unknown> : null;
    const tournamentId = String(record?.tournamentId ?? "").trim();
    if (!tournamentId) throw badRequest("Invalid tournament assignment");
    return { tournamentId, tournamentName: "" };
  });
}

async function assignmentScopeFor(actor: Actor): Promise<Set<string> | undefined> {
  return hasAdminClaim(actor) ? undefined : listAssignedTournamentIds(actor.uid);
}

async function assertAdminRemainsEnabled(
  currentRole: GlobalUserRole,
  currentDisabled: boolean,
  nextRole: GlobalUserRole,
  nextDisabled: boolean,
): Promise<void> {
  if (currentRole !== "ADMIN" || currentDisabled || (nextRole === "ADMIN" && !nextDisabled)) return;
  if (await countEnabledAdmins() <= 1) {
    throw badRequest("The last enabled admin cannot be demoted or disabled");
  }
}

function requireBootstrapKey(req: { header(name: string): string | undefined }): void {
  const expected = BOOTSTRAP_KEY.value();
  if (!expected) throw new Error("Missing secret MTS_BOOTSTRAP_KEY");
  const provided = req.header("x-bootstrap-key");
  if (!provided || provided !== expected) throw forbidden("Invalid bootstrap key");
}

export function adminRouter(): Router {
  const router = Router();

  router.post("/bootstrapAdmin", async (req, res, next) => {
    try {
      requireBootstrapKey(req);
      if (await countEnabledAdmins() > 0) {
        throw forbidden("An enabled admin already exists");
      }
      const uid = String(req.body?.uid ?? "").trim();
      if (!uid) throw badRequest("Missing uid");
      const user = await auth.getUser(uid);
      if (!user.email) throw badRequest("The bootstrap account must have an email address");
      await createUserProfile(uid, user.email, user.displayName ?? "");
      const claims: Record<string, unknown> = { ...(user.customClaims ?? {}), admin: true };
      delete claims.superadmin;
      await auth.setCustomUserClaims(uid, claims);
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  router.get("/whoami", requireAuth, async (_req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      res.status(200).json({ uid: actor.uid, role: hasAdminClaim(actor) ? "ADMIN" : "EDITOR" });
    } catch (error) {
      next(error);
    }
  });

  router.get("/users", requireAuth, async (_req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      res.status(200).json({ users: await listManagedUsers(await assignmentScopeFor(actor)) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/users", requireAuth, async (req, res, next) => {
    let createdUid: string | null = null;
    try {
      const actor = res.locals.auth as Actor;
      const email = requireEmail(req.body?.email, "email");
      const alias = String(req.body?.alias ?? "").trim();
      const role = parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      if (role === "ADMIN" && !hasAdminClaim(actor)) throw forbidden("Only admins can create admins");
      const assignmentScope = await assignmentScopeFor(actor);
      const tournamentAssignments = role === "ADMIN" ? [] : parseTournamentAssignments(req.body?.tournamentAssignments);
      await assertEmailAvailable(email);

      const user = await auth.createUser({ email, disabled: false });
      createdUid = user.uid;
      await createUserProfile(user.uid, email, alias);
      await setGlobalUserRole(user.uid, role);
      await syncUserTournamentAssignments(user.uid, tournamentAssignments, assignmentScope);
      await sendPasswordResetEmail(email);

      res.status(201).json(await getManagedUser(user.uid, assignmentScope));
    } catch (error) {
      if (createdUid) {
        await Promise.allSettled([
          auth.deleteUser(createdUid),
          db.doc(`users/${createdUid}`).delete(),
          syncUserTournamentAssignments(createdUid, []),
        ]);
      }
      next(error);
    }
  });

  router.put("/users/:uid", requireAuth, async (req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const current = await getManagedUser(req.params.uid);
      const role = parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      if (!hasAdminClaim(actor) && (current.role === "ADMIN" || role === "ADMIN")) {
        throw forbidden("Editors cannot modify admin accounts");
      }
      if (actor.uid === current.uid && role !== current.role) {
        throw badRequest("You cannot change your own role");
      }

      const email = requireEmail(req.body?.email, "email");
      const alias = String(req.body?.alias ?? "").trim();
      const assignmentScope = await assignmentScopeFor(actor);
      const tournamentAssignments = role === "ADMIN" ? [] : parseTournamentAssignments(req.body?.tournamentAssignments);
      await assertAdminRemainsEnabled(current.role, current.disabled, role, current.disabled);
      await assertEmailAvailable(email, current.uid);

      await updateManagedUser({
        uid: current.uid,
        email,
        alias,
        role,
        tournamentAssignments,
        assignmentScope,
      });
      res.status(200).json(await getManagedUser(current.uid, assignmentScope));
    } catch (error) {
      next(error);
    }
  });

  router.put("/users/:uid/disabled", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const disabled = req.body?.disabled;
      if (typeof disabled !== "boolean") throw badRequest("disabled must be a boolean");
      const current = await getManagedUser(req.params.uid);
      if (actor.uid === current.uid && disabled) throw badRequest("You cannot disable your own account");
      await assertAdminRemainsEnabled(current.role, current.disabled, current.role, disabled);

      await setManagedUserDisabled(current.uid, disabled);
      res.status(200).json(await getManagedUser(current.uid));
    } catch (error) {
      next(error);
    }
  });

  router.get("/users/lookup", requireAuth, async (req, res, next) => {
    try {
      const identifier = String(req.query.identifier ?? "").trim();
      if (!identifier) throw badRequest("Missing identifier");
      if (!identifier.includes("@")) throw badRequest("Enter a user email address");
      const profile = await auth.getUserByEmail(identifier).then((user) => getUserProfile(user.uid));
      res.status(200).json(profile);
    } catch (error) {
      next(error);
    }
  });

  return router;
}
