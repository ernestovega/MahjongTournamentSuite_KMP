import type { NextFunction, Request, Response } from "express";
import { getAppCheck } from "firebase-admin/app-check";

import { adminApp } from "../../firebase";
import { HttpError } from "../httpError";

/**
 * App Check proves that a request comes from one of the apps and not from a script.
 * - `off`: the header is ignored. This is the default.
 * - `monitor`: a missing or wrong token is logged and the request goes on.
 * - `enforce`: a missing or wrong token gets 401.
 */
export type AppCheckMode = "off" | "monitor" | "enforce";

export const APP_CHECK_HEADER = "x-firebase-appcheck";

export function appCheckModeFromEnv(value: string | undefined = process.env.APP_CHECK_MODE): AppCheckMode {
  const mode = value?.trim().toLowerCase();
  return mode === "monitor" || mode === "enforce" ? mode : "off";
}

export function appCheck(options: {
  mode?: () => AppCheckMode;
  verify?: (token: string) => Promise<unknown>;
} = {}) {
  const mode = options.mode ?? (() => appCheckModeFromEnv());
  const verify = options.verify ?? ((token: string) => getAppCheck(adminApp).verifyToken(token));

  return async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const current = mode();
    if (current === "off") {
      next();
      return;
    }
    const token = req.header(APP_CHECK_HEADER);
    let reason: string | null = null;
    if (!token) {
      reason = "missing";
    } else {
      try {
        await verify(token);
      } catch {
        reason = "invalid";
      }
    }
    if (reason == null) {
      next();
      return;
    }
    if (current === "enforce") {
      next(new HttpError(401, "app_check_failed", "The request does not come from a known app."));
      return;
    }
    console.warn("App Check failed", { reason, method: req.method, path: req.path });
    next();
  };
}
