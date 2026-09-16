import type { NextFunction, Request, Response } from "express";

import { forbidden } from "../httpError";

/** Allows global admins and superadmins to manage the shared player base. */
export function requireAdmin(_req: Request, res: Response, next: NextFunction): void {
  const decoded = res.locals.auth as { admin?: boolean; superadmin?: boolean } | undefined;
  if (decoded?.admin === true || decoded?.superadmin === true) {
    next();
    return;
  }
  next(forbidden("Admin required"));
}
