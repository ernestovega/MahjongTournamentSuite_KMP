import type { NextFunction, Request, Response } from "express";

import { db } from "../../firebase";
import { hasAdminClaim } from "../../models/globalRole";
import { forbidden, notFound } from "../httpError";

// Editors are checked on every request, so a positive result is reused for a short time.
const MEMBERSHIP_TTL_MS = 30_000;
const MAX_CACHED_MEMBERSHIPS = 2000;
const membershipValidUntil = new Map<string, number>();

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

  const cacheKey = `${tournamentId}/${decoded.uid}`;
  const now = Date.now();
  if ((membershipValidUntil.get(cacheKey) ?? 0) > now) {
    next();
    return;
  }

  const member = await db.doc(`tournaments/${tournamentId}/members/${decoded.uid}`).get();
  if (!member.exists) {
    membershipValidUntil.delete(cacheKey);
    next(forbidden("Editor is not assigned to this tournament"));
    return;
  }
  if (membershipValidUntil.size >= MAX_CACHED_MEMBERSHIPS) membershipValidUntil.clear();
  membershipValidUntil.set(cacheKey, now + MEMBERSHIP_TTL_MS);

  next();
}
