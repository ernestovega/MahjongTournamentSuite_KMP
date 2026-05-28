import type { NextFunction, Request, Response } from "express";

import { forbidden } from "../httpError";

export function requireSuperadmin(
  _req: Request,
  res: Response,
  next: NextFunction,
): void {
  const decoded = res.locals.auth as { superadmin?: boolean } | undefined;
  if (decoded?.superadmin === true) {
    next();
    return;
  }

  next(forbidden("Superadmin required"));
}
