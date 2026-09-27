import type { NextFunction, Request, Response } from "express";

import { forbidden } from "../httpError";
import { hasAdminClaim } from "../../models/globalRole";

export function requireAdmin(_req: Request, res: Response, next: NextFunction): void {
  const decoded = res.locals.auth as { admin?: boolean } | undefined;
  if (hasAdminClaim(decoded)) {
    next();
    return;
  }
  next(forbidden("Admin required"));
}
