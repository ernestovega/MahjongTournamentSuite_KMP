import type { NextFunction, Request, Response } from "express";

import { getPlayerRegistration } from "../../services/playerAccountsService";
import { forbidden } from "../httpError";
import { pathParam } from "../pathParam";

/** Lets only a player account that is registered in the tournament of the route. Run it after requireAuth. */
export async function requireRegisteredPlayer(req: Request, res: Response, next: NextFunction): Promise<void> {
  const decoded = res.locals.auth as { uid: string; player?: boolean } | undefined;
  if (decoded?.player !== true) {
    next(forbidden("Player account required"));
    return;
  }
  try {
    const registration = await getPlayerRegistration(decoded.uid, pathParam(req, "tournamentId"));
    if (!registration) {
      next(forbidden("You are not registered in this tournament"));
      return;
    }
    res.locals.playerRegistration = registration;
    next();
  } catch (error) {
    next(error);
  }
}
