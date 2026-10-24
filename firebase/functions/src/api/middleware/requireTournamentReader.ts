import type { NextFunction, Request, Response } from "express";

import { hasAdminClaim } from "../../models/globalRole";
import { getPlayerRegistration } from "../../services/playerAccountsService";
import { forbidden } from "../httpError";
import { requireTournamentEditor } from "./requireTournamentEditor";
import { pathParam } from "../pathParam";

/** Who a read route serves. Player readers get a reduced view of the data. */
export type ReaderKind = "EDITOR" | "PLAYER";

/**
 * Lets an editor of the tournament, an admin, or a player registered in the tournament read it.
 * It sets `res.locals.reader`. Editors who also play keep the editor view.
 */
export async function requireTournamentReader(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean; player?: boolean } | undefined;
  if (!decoded) {
    next(forbidden("Auth context missing"));
    return;
  }

  if (hasAdminClaim(decoded) || decoded.editor === true) {
    res.locals.reader = "EDITOR" satisfies ReaderKind;
    await requireTournamentEditor(req, res, next);
    return;
  }

  const tournamentId = pathParam(req, "tournamentId");
  if (decoded.player === true && tournamentId) {
    const registration = await getPlayerRegistration(decoded.uid, tournamentId);
    if (registration) {
      res.locals.reader = "PLAYER" satisfies ReaderKind;
      res.locals.playerRegistration = registration;
      next();
      return;
    }
  }
  next(forbidden("You are not registered in this tournament"));
}
