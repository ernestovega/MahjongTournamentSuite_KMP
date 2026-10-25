import { timingSafeEqual } from "node:crypto";
import { Router } from "express";
import { recordActivity } from "../../services/activityLog";
import { actorFromToken } from "../../services/changeLog";

import { auth, db } from "../../firebase";
import { badRequest, forbidden } from "../httpError";
import { readContentType, readPhotoData } from "./auth.routes";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireAuth } from "../middleware/requireAuth";
import { requireEditor } from "../middleware/requireEditor";
import { getGlobalUserRole, getGlobalUserRoles, hasAdminClaim, parseGlobalUserRole } from "../../models/globalRole";
import { sendPasswordResetEmail } from "../../services/firebaseAuthRest";
import {
  assertEmailAvailable,
  clearUserPhoto,
  countEnabledAdmins,
  createUserProfile,
  getManagedUser,
  getUserProfile,
  listAssignedTournamentIds,
  listManagedUsers,
  setGlobalUserRole,
  setManagedUserDisabled,
  setUserPhoto,
  syncUserTournamentAssignments,
  updateManagedUser,
  type GlobalUserRole,
  type TournamentAssignment,
} from "../../services/usersService";
import { BOOTSTRAP_KEY } from "../../config";
import { bumpGlobalDataVersion } from "../../services/dataVersionsService";
import { assertEmaIdAvailable, listInvitedPeople, setAccountEmaId } from "../../services/playerAccountsService";
import { DEFAULT_USAGE_PERIOD, loadUsageReport, USAGE_PERIODS } from "../../services/usageService";
import { pathParam } from "../pathParam";

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
  const providedBytes = Buffer.from(provided ?? "");
  const expectedBytes = Buffer.from(expected);
  if (providedBytes.length !== expectedBytes.length || !timingSafeEqual(providedBytes, expectedBytes)) {
    throw forbidden("Invalid bootstrap key");
  }
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
      await auth.setCustomUserClaims(uid, claims);
      await bumpGlobalDataVersion("users");
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  router.get("/whoami", requireAuth, async (_req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const user = await auth.getUser(actor.uid);
      res.status(200).json({ uid: actor.uid, role: getGlobalUserRole(user), roles: getGlobalUserRoles(user) });
    } catch (error) {
      next(error);
    }
  });

  /** Usage analytics for administrators: who uses the apps, what they use, when, and where problems appear. */
  router.get("/usage", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const days = Number(req.query.days ?? DEFAULT_USAGE_PERIOD);
      if (!(USAGE_PERIODS as readonly number[]).includes(days)) {
        throw badRequest("Invalid period", { allowed: USAGE_PERIODS });
      }
      res.status(200).json(await loadUsageReport(days));
    } catch (error) {
      next(error);
    }
  });

  router.get("/users", requireAuth, requireEditor, async (_req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      res.status(200).json({ users: await listManagedUsers(await assignmentScopeFor(actor)) });
    } catch (error) {
      next(error);
    }
  });

  /** People who got an invitation and did not accept yet. An editor sees only the assigned tournaments. */
  router.get("/invitations", requireAuth, requireEditor, async (_req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      res.status(200).json({ invitations: await listInvitedPeople(await assignmentScopeFor(actor)) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/users", requireAuth, requireEditor, async (req, res, next) => {
    let createdUid: string | null = null;
    try {
      const actor = res.locals.auth as Actor;
      const email = requireEmail(req.body?.email, "email");
      const alias = String(req.body?.alias ?? "").trim();
      // A player account always has an EMA id. An editor may set it for a player account only.
      const role = req.body?.role === "PLAYER" ? "PLAYER" as const : parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      if (role === "ADMIN" && !hasAdminClaim(actor)) throw forbidden("Only admins can create admins");
      if (role === "EDITOR" && !hasAdminClaim(actor)) throw forbidden("Only admins can create editor accounts");
      const assignmentScope = await assignmentScopeFor(actor);
      const tournamentAssignments = role === "EDITOR" ? parseTournamentAssignments(req.body?.tournamentAssignments) : [];
      // The EMA id links the account to a player. Only an admin sets it. Check it before the account exists.
      const emaId = String(req.body?.emaId ?? "").trim() || null;
      if (role === "PLAYER" && emaId == null) throw badRequest("A player account needs an EMA id");
      if (emaId != null) {
        if (!hasAdminClaim(actor)) throw forbidden("Only an admin can set the EMA id of an account");
        await assertEmaIdAvailable(emaId);
      }
      await assertEmailAvailable(email);

      const user = await auth.createUser({ email, disabled: false });
      createdUid = user.uid;
      await createUserProfile(user.uid, email, alias);
      await setGlobalUserRole(user.uid, role);
      await syncUserTournamentAssignments(user.uid, tournamentAssignments, assignmentScope);
      if (emaId != null) await setAccountEmaId({ uid: user.uid, emaId });
      await sendPasswordResetEmail(email);
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "user.create", actor: actorFromToken(res.locals.auth), target: `user ${user.uid}`,
        details: { role, emaId, tournamentAssignments },
      });

      res.status(201).json(await getManagedUser(user.uid, assignmentScope));
    } catch (error) {
      if (createdUid) {
        await setAccountEmaId({ uid: createdUid, emaId: null }).catch(() => undefined);
        await Promise.allSettled([
          auth.deleteUser(createdUid),
          db.doc(`users/${createdUid}`).delete(),
          syncUserTournamentAssignments(createdUid, []),
        ]);
      }
      next(error);
    }
  });

  router.put("/users/:uid", requireAuth, requireEditor, async (req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const current = await getManagedUser(pathParam(req, "uid"));
      // A player-only account may keep its role. Management cannot create players or demote editors to players.
      const role = parseGlobalUserRole(req.body?.role)
        ?? (req.body?.role === "PLAYER" && current.role === "PLAYER" ? "PLAYER" as const : null);
      if (!role) throw badRequest("Invalid role");
      if (!hasAdminClaim(actor) && (current.role === "ADMIN" || role === "ADMIN")) {
        throw forbidden("Editors cannot modify admin accounts");
      }
      if (!hasAdminClaim(actor) && current.role !== "PLAYER" && current.uid !== actor.uid) {
        throw forbidden("Editors can only edit their own account");
      }
      if (actor.uid === current.uid && role !== current.role) {
        throw badRequest("You cannot change your own role");
      }

      const email = requireEmail(req.body?.email, "email");
      // A new email plus a password reset takes over the account. Only admins may change the email of an account.
      if (!hasAdminClaim(actor) && email.toLowerCase() !== current.email.toLowerCase()) {
        throw forbidden("Only an admin can change the email of an account");
      }
      const alias = String(req.body?.alias ?? "").trim();
      const assignmentScope = await assignmentScopeFor(actor);
      const tournamentAssignments = role === "ADMIN" ? [] : parseTournamentAssignments(req.body?.tournamentAssignments);
      await assertAdminRemainsEnabled(current.role, current.disabled, role, current.disabled);
      await assertEmailAvailable(email, current.uid);
      // Check the EMA id before any change, so a refusal leaves the account as it was.
      const nextEmaId = typeof req.body?.emaId === "string" ? req.body.emaId.trim() || null : undefined;
      const emaIdChanges = nextEmaId !== undefined && nextEmaId !== (current.emaId ?? null);
      if (emaIdChanges) {
        if (role !== "PLAYER" && !hasAdminClaim(actor)) throw forbidden("Only an admin can change the EMA id of an editor account");
        if (nextEmaId != null) await assertEmaIdAvailable(nextEmaId, current.uid);
      }

      await updateManagedUser({
        uid: current.uid,
        email,
        alias,
        role,
        tournamentAssignments,
        assignmentScope,
      });
      if (emaIdChanges) await setAccountEmaId({ uid: current.uid, emaId: nextEmaId ?? null });
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "user.update", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`,
        details: {
          roleFrom: current.role, roleTo: role, alias, emaId: emaIdChanges ? nextEmaId : undefined, tournamentAssignments,
        },
      });
      res.status(200).json(await getManagedUser(current.uid, assignmentScope));
    } catch (error) {
      next(error);
    }
  });

  // Only admins set the photo of another user. A user sets the own photo with /auth/me/photo.
  router.put("/users/:uid/photo", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const contentType = readContentType(req.body);
      await setUserPhoto(pathParam(req, "uid"), contentType, readPhotoData(req.body));
      await recordActivity({
        action: "user.photo", actor: actorFromToken(res.locals.auth), target: pathParam(req, "uid"), details: { contentType },
      });
      res.status(200).json(await getManagedUser(pathParam(req, "uid")));
    } catch (error) {
      next(error);
    }
  });

  router.delete("/users/:uid/photo", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      await clearUserPhoto(pathParam(req, "uid"));
      await recordActivity({
        action: "user.photo.remove", actor: actorFromToken(res.locals.auth), target: pathParam(req, "uid"), details: {},
      });
      res.status(200).json(await getManagedUser(pathParam(req, "uid")));
    } catch (error) {
      next(error);
    }
  });

  router.put("/users/:uid/disabled", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const actor = res.locals.auth as Actor;
      const disabled = req.body?.disabled;
      if (typeof disabled !== "boolean") throw badRequest("disabled must be a boolean");
      const current = await getManagedUser(pathParam(req, "uid"));
      if (actor.uid === current.uid && disabled) throw badRequest("You cannot disable your own account");
      await assertAdminRemainsEnabled(current.role, current.disabled, current.role, disabled);

      await setManagedUserDisabled(current.uid, disabled);
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: disabled ? "user.disable" : "user.enable", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`,
      });
      res.status(200).json(await getManagedUser(current.uid));
    } catch (error) {
      next(error);
    }
  });

  router.get("/users/lookup", requireAuth, requireEditor, async (req, res, next) => {
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
