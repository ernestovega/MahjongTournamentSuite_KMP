import type { NextFunction, Request, Response } from "express";

import { forbidden } from "../httpError";
import { hasEditorAccess } from "../../models/globalRole";

/** Lets only accounts with the editor or admin claim use management routes. Run it after requireAuth. */
export function requireEditor(_req: Request, res: Response, next: NextFunction): void {
  const decoded = res.locals.auth as { admin?: boolean; editor?: boolean; player?: boolean } | undefined;
  if (hasEditorAccess(decoded)) {
    next();
    return;
  }
  next(forbidden("Editor required"));
}
