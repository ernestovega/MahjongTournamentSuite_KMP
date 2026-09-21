import type { NextFunction, Request, Response } from "express";

import { forbidden } from "../httpError";

/** Kept for compatibility. Global administration now requires a superadmin. */
export function requireAdmin(_req: Request, res: Response, next: NextFunction): void {
  const decoded = res.locals.auth as { superadmin?: boolean } | undefined;
  if (decoded?.superadmin === true) {
    next();
    return;
  }
  next(forbidden("Superadmin required"));
}
