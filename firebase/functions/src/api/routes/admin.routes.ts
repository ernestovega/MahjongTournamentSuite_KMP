import { Router } from "express";

import { auth, db } from "../../firebase";
import { badRequest, forbidden } from "../httpError";
import { requireAuth } from "../middleware/requireAuth";
import { requireSuperadmin } from "../middleware/requireSuperadmin";
import { sendPasswordResetEmail } from "../../services/firebaseAuthRest";
import {
  assertEmailAvailable,
  countEnabledSuperadmins,
  createUserProfile,
  getManagedUser,
  getUserProfile,
  listManagedUsers,
  setGlobalUserRole,
  setManagedUserDisabled,
  syncUserTournamentAssignments,
  updateManagedUser,
  type GlobalUserRole,
  type TournamentAssignment,
} from "../../services/usersService";
import { BOOTSTRAP_KEY } from "../../config";
import { parseRole } from "../../models/role";

type Actor = { uid: string; superadmin?: boolean };

function parseGlobalUserRole(value: unknown): GlobalUserRole | null {
  return value === "REGULAR" || value === "SUPERADMIN" ? value : null;
}

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
    const role = parseRole(record?.role);
    if (!tournamentId || !role) throw badRequest("Invalid tournament assignment");
    return { tournamentId, tournamentName: "", role };
  });
}

async function assertSuperadminRemainsEnabled(
  currentRole: GlobalUserRole,
  currentDisabled: boolean,
  nextRole: GlobalUserRole,
  nextDisabled: boolean,
): Promise<void> {
  if (currentRole !== "SUPERADMIN" || currentDisabled || (nextRole === "SUPERADMIN" && !nextDisabled)) return;
  if (await countEnabledSuperadmins() <= 1) {
    throw badRequest("The last enabled superadmin cannot be demoted or disabled");
  }
}

function requireBootstrapKey(req: { header(name: string): string | undefined }): void {
  const expected = BOOTSTRAP_KEY.value();
  if (!expected) {
    throw new Error("Missing secret MTS_BOOTSTRAP_KEY");
  }
  const provided = req.header("x-bootstrap-key");
  if (!provided || provided != expected) {
    throw forbidden("Invalid bootstrap key");
  }
}

export function adminRouter(): Router {
  const router = Router();

  // One-time helper to set superadmin claim.
  router.post("/bootstrapSuperadmin", async (req, res, next) => {
    try {
      requireBootstrapKey(req);

      const uid = String(req.body?.uid ?? "").trim();
      if (!uid) {
        throw badRequest("Missing uid");
      }

      await auth.setCustomUserClaims(uid, { superadmin: true });

      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.get("/whoami", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; superadmin?: boolean };
      res.status(200).json({ uid: decoded.uid, admin: false, superadmin: decoded.superadmin === true });
    } catch (e) {
      next(e);
    }
  });

  router.get("/users", requireAuth, requireSuperadmin, async (_req, res, next) => {
    try {
      res.status(200).json({ users: await listManagedUsers() });
    } catch (e) {
      next(e);
    }
  });

  router.post("/users", requireAuth, requireSuperadmin, async (req, res, next) => {
    let createdUid: string | null = null;
    try {
      const email = requireEmail(req.body?.email, "email");
      const alias = String(req.body?.alias ?? "").trim();
      const role = parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      const tournamentAssignments = parseTournamentAssignments(req.body?.tournamentAssignments);
      await assertEmailAvailable(email);

      const user = await auth.createUser({ email, disabled: false });
      createdUid = user.uid;
      await createUserProfile(user.uid, email, alias);
      await setGlobalUserRole(user.uid, role);
      await syncUserTournamentAssignments(user.uid, tournamentAssignments);
      await sendPasswordResetEmail(email);

      res.status(201).json(await getManagedUser(user.uid));
    } catch (e) {
      if (createdUid) {
        await Promise.allSettled([
          auth.deleteUser(createdUid),
          db.doc(`users/${createdUid}`).delete(),
          syncUserTournamentAssignments(createdUid, []),
        ]);
      }
      next(e);
    }
  });

  router.put("/users/:uid", requireAuth, requireSuperadmin, async (req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const current = await getManagedUser(req.params.uid);
      const email = requireEmail(req.body?.email, "email");
      const alias = String(req.body?.alias ?? "").trim();
      const role = parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      const tournamentAssignments = parseTournamentAssignments(req.body?.tournamentAssignments);
      if (actor.uid === current.uid && role !== current.role) {
        throw badRequest("You cannot change your own role");
      }
      await assertSuperadminRemainsEnabled(current.role, current.disabled, role, current.disabled);
      await assertEmailAvailable(email, current.uid);

      await updateManagedUser({ uid: current.uid, email, alias, role, tournamentAssignments });
      res.status(200).json(await getManagedUser(current.uid));
    } catch (e) {
      next(e);
    }
  });

  router.put("/users/:uid/disabled", requireAuth, requireSuperadmin, async (req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const disabled = req.body?.disabled;
      if (typeof disabled !== "boolean") throw badRequest("disabled must be a boolean");
      const current = await getManagedUser(req.params.uid);
      if (actor.uid === current.uid && disabled) {
        throw badRequest("You cannot disable your own account");
      }
      await assertSuperadminRemainsEnabled(current.role, current.disabled, current.role, disabled);

      await setManagedUserDisabled(current.uid, disabled);
      res.status(200).json(await getManagedUser(current.uid));
    } catch (e) {
      next(e);
    }
  });

  router.get("/users/lookup", requireAuth, requireSuperadmin, async (req, res, next) => {
    try {
      const identifier = String(req.query.identifier ?? "").trim();
      if (!identifier) {
        throw badRequest("Missing identifier");
      }

      if (!identifier.includes("@")) throw badRequest("Enter a user email address");
      const profile = await auth.getUserByEmail(identifier).then((user) => getUserProfile(user.uid));
      res.status(200).json(profile);
    } catch (e) {
      next(e);
    }
  });

  return router;
}
