import type { NextFunction, Request, Response } from "express";

import { db } from "../../firebase";
import { hasAdminClaim } from "../../models/globalRole";
import { forbidden, notFound } from "../httpError";

export async function requireTournamentEditor(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  const decoded = res.locals.auth as { uid: string; admin?: boolean } | undefined;
  if (!decoded) {
    next(forbidden("Auth context missing"));
    return;
  }

  if (hasAdminClaim(decoded)) {
    next();
    return;
  }

  const tournamentId = req.params.tournamentId;
  if (!tournamentId) {
    next(notFound("Missing tournamentId"));
    return;
  }

  const member = await db.doc(`tournaments/${tournamentId}/members/${decoded.uid}`).get();
  if (!member.exists) {
    next(forbidden("Editor is not assigned to this tournament"));
    return;
  }

  next();
}
