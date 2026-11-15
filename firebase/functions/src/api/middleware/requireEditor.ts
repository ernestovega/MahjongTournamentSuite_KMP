import type { NextFunction, Request, Response } from "express";

import { auth } from "../../firebase";
import { forbidden } from "../httpError";
import { getGlobalUserRole, hasEditorAccess } from "../../models/globalRole";

/**
 * Lets only accounts with the editor or admin claim use management routes. Run it after requireAuth.
 * A token keeps the claims of the moment it was issued, for up to one hour. When the token has no such claim,
 * the account itself is read, so an account that just became an editor does not wait for a new token.
 */
export async function requireEditor(_req: Request, res: Response, next: NextFunction): Promise<void> {
  const decoded = res.locals.auth as { uid?: string; admin?: boolean; editor?: boolean; player?: boolean } | undefined;
  if (hasEditorAccess(decoded)) {
    next();
    return;
  }
  const user = decoded?.uid ? await auth.getUser(decoded.uid).catch(() => null) : null;
  if (user && !user.disabled && getGlobalUserRole(user) !== "PLAYER") {
    next();
    return;
  }
  next(forbidden("Editor required"));
}
