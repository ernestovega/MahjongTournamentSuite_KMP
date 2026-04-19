import type { NextFunction, Request, Response } from "express";

import { db } from "../../firebase";
import { Role, roleMeetsMinimum } from "../../models/role";
import { forbidden, notFound } from "../httpError";

export function requireTournamentRole(minimumRole: Role) {
  return async function (req: Request, res: Response, next: NextFunction): Promise<void> {
    const decoded = res.locals.auth as { uid: string; superadmin?: boolean } | undefined;
    if (!decoded) {
      next(forbidden("Auth context missing"));
      return;
    }

    if (decoded.superadmin === true) {
      next();
      return;
    }

    const tournamentId = req.params.tournamentId;
    if (!tournamentId) {
      next(notFound("Missing tournamentId"));
      return;
    }

    const uid = decoded.uid;
    const memberRef = db.doc(`tournaments/${tournamentId}/members/${uid}`);
    const memberSnap = await memberRef.get();
    if (!memberSnap.exists) {
      next(forbidden("Not a tournament member"));
      return;
    }

    const role = memberSnap.get("role") as Role | undefined;
    if (!role) {
      next(forbidden("Missing role"));
      return;
    }

    if (!roleMeetsMinimum(role, minimumRole)) {
      next(forbidden("Insufficient role"));
      return;
    }

    next();
  };
}
