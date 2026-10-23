import { Router } from "express";

import { badRequest, forbidden, HttpError, unauthenticated } from "../httpError";
import { rateLimit } from "../middleware/rateLimit";
import { requireAuth } from "../middleware/requireAuth";
import { refreshIdToken, sendPasswordResetEmail, signInWithEmailPassword } from "../../services/firebaseAuthRest";
import { auth } from "../../firebase";
import { getGlobalUserRoles } from "../../models/globalRole";
import { clearUserPhoto, getOwnProfile, setUserPhoto, updateOwnAlias } from "../../services/usersService";

/** Turns the error code of the sign-in service into an answer that the apps can show to the user. */
export function signInError(error: unknown): unknown {
  const code = error instanceof Error ? error.message : "";
  if (["INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND"].includes(code)) return unauthenticated("INVALID_LOGIN_CREDENTIALS");
  if (code.startsWith("INVALID_EMAIL")) return badRequest("INVALID_EMAIL");
  if (code === "USER_DISABLED") return forbidden("Forbidden: this account is disabled");
  if (code.startsWith("TOO_MANY_ATTEMPTS")) return new HttpError(429, "too_many_requests", "Too many attempts");
  return error;
}

export function authRouter(): Router {
  const router = Router();
  // The sign-in routes are public. Each address may call them 30 times a minute.
  const publicLimit = rateLimit({ windowMs: 60_000, max: 30 });

  router.post("/signIn", publicLimit, async (req, res, next) => {
    try {
      const email = String(req.body?.email ?? "").trim();
      const password = String(req.body?.password ?? "");

      if (!email || !password) {
        throw badRequest("Missing email or password");
      }

      const tokens = await signInWithEmailPassword(email, password);

      res.status(200).json({
        idToken: tokens.idToken,
        refreshToken: tokens.refreshToken,
        uid: tokens.uid,
      });
    } catch (e) {
      next(signInError(e));
    }
  });

  router.post("/passwordReset", publicLimit, async (req, res, next) => {
    try {
      const email = String(req.body?.email ?? "").trim();
      if (!email) {
        throw badRequest("Missing email");
      }

      // The answer is the same for an unknown email, so it does not tell which emails have an account.
      await sendPasswordResetEmail(email).catch((error: unknown) => {
        if (!(error instanceof Error) || !error.message.startsWith("EMAIL_NOT_FOUND")) throw error;
      });
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.post("/refresh", publicLimit, async (req, res, next) => {
    try {
      const refreshToken = String(req.body?.refreshToken ?? "");
      if (!refreshToken) {
        throw badRequest("Missing refreshToken");
      }

      const tokens = await refreshIdToken(refreshToken);
      res.status(200).json(tokens);
    } catch (e) {
      next(e);
    }
  });

  router.get("/me", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      const [profile, user] = await Promise.all([getOwnProfile(decoded.uid), auth.getUser(decoded.uid)]);
      res.status(200).json({ ...profile, roles: getGlobalUserRoles(user) });
    } catch (e) {
      next(e);
    }
  });

  // Every signed-in user can change the own alias and the own photo. The EMA id stays as the admins set it.
  router.put("/me", requireAuth, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      await updateOwnAlias(decoded.uid, req.body?.alias);
      const [profile, user] = await Promise.all([getOwnProfile(decoded.uid), auth.getUser(decoded.uid)]);
      res.status(200).json({ ...profile, roles: getGlobalUserRoles(user) });
    } catch (e) {
      next(e);
    }
  });

  router.put("/me/photo", requireAuth, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      await setUserPhoto(decoded.uid, readContentType(req.body), readPhotoData(req.body));
      const [profile, user] = await Promise.all([getOwnProfile(decoded.uid), auth.getUser(decoded.uid)]);
      res.status(200).json({ ...profile, roles: getGlobalUserRoles(user) });
    } catch (e) {
      next(e);
    }
  });

  router.delete("/me/photo", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      await clearUserPhoto(decoded.uid);
      const [profile, user] = await Promise.all([getOwnProfile(decoded.uid), auth.getUser(decoded.uid)]);
      res.status(200).json({ ...profile, roles: getGlobalUserRoles(user) });
    } catch (e) {
      next(e);
    }
  });

  return router;
}

export function readContentType(body: unknown): string {
  return String((body as { contentType?: unknown })?.contentType ?? "").trim().toLowerCase();
}

export function readPhotoData(body: unknown): string {
  const data = String((body as { dataBase64?: unknown })?.dataBase64 ?? "").trim();
  if (!data) throw badRequest("Photo data is required");
  return data;
}
