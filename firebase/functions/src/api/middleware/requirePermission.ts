import type { NextFunction, Request, RequestHandler, Response } from "express";

import { forbidden, notFound } from "../httpError";
import type { Permission } from "../../models/permissions";
import { canInTournament, hasPermissionSomewhere, loadAccess, type Access } from "../../services/accessService";

/** A guard that names the permission it checks, so the permission test can read the guard of every route. */
export type PermissionGuard = RequestHandler & { permission: Permission; scope: "anywhere" | "tournament" };

/** The access of the signed-in account. Run a permission guard before reading it. */
export function accessOf(res: Response): Access {
  const access = res.locals.access as Access | undefined;
  if (!access) throw forbidden("Access context missing");
  return access;
}

async function loadCaller(res: Response): Promise<Access | null> {
  const uid = (res.locals.auth as { uid?: string } | undefined)?.uid;
  if (!uid) return null;
  const access = await loadAccess(uid);
  res.locals.access = access;
  return access.disabled ? null : access;
}

/**
 * Lets an account through when one of its roles has the permission in some scope. The route itself checks the
 * scope of what it changes, for example that a club admin creates a tournament for one of its organizations.
 * Run it after requireAuth.
 */
export function requirePermission(permission: Permission): PermissionGuard {
  const guard = async (_req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const access = await loadCaller(res);
      if (access && hasPermissionSomewhere(access, permission)) {
        next();
        return;
      }
      next(forbidden(`Permission required: ${permission}`));
    } catch (error) {
      next(error);
    }
  };
  return Object.assign(guard, { permission, scope: "anywhere" as const });
}

/**
 * Lets an account through when it has the permission in the tournament of the route: a super admin always, a
 * club admin in the tournaments of its organizations and an editor in the tournaments assigned to it.
 * Run it after requireAuth.
 */
export function requireTournamentPermission(permission: Permission): PermissionGuard {
  const guard = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const tournamentId = String(req.params.tournamentId ?? "");
      if (!tournamentId) {
        next(notFound("Missing tournamentId"));
        return;
      }
      const access = await loadCaller(res);
      if (!access) {
        next(forbidden("Auth context missing"));
        return;
      }
      if (await canInTournament(access, permission, tournamentId)) {
        next();
        return;
      }
      next(forbidden(`Permission required in this tournament: ${permission}`));
    } catch (error) {
      next(error);
    }
  };
  return Object.assign(guard, { permission, scope: "tournament" as const });
}
