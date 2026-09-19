import { Router } from "express";

import { auth } from "../../firebase";
import { badRequest } from "../httpError";
import { requireAuth } from "../middleware/requireAuth";
import { refreshIdToken, sendPasswordResetEmail, signInWithEmailPassword, signUpWithEmailPassword } from "../../services/firebaseAuthRest";
import { assertEmaIdAvailable, createUserProfile, getUserProfile } from "../../services/usersService";

export function authRouter(): Router {
  const router = Router();

  router.post("/signUp", async (req, res, next) => {
    let createdUid: string | null = null;

    try {
      const email = String(req.body?.email ?? "").trim();
      const password = String(req.body?.password ?? "");
      const emaId = String(req.body?.emaId ?? "").trim();

      if (!email || !password || !emaId) {
        throw badRequest("Missing email, password, or emaId");
      }

      await assertEmaIdAvailable(emaId);

      const tokens = await signUpWithEmailPassword(email, password);
      createdUid = tokens.uid;

      await createUserProfile(tokens.uid, email, emaId);

      res.status(200).json({
        idToken: tokens.idToken,
        refreshToken: tokens.refreshToken,
        uid: tokens.uid,
      });
    } catch (e) {
      if (createdUid) {
        try {
          await auth.deleteUser(createdUid);
        } catch {
          // ignore cleanup failures
        }
      }

      next(e);
    }
  });

  router.post("/signIn", async (req, res, next) => {
    try {
      // Accept identifier during the rollout of the email-only API.
      const email = String(req.body?.email ?? req.body?.identifier ?? "").trim();
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
      next(e);
    }
  });

  router.post("/passwordReset", async (req, res, next) => {
    try {
      const email = String(req.body?.email ?? "").trim();
      if (!email) {
        throw badRequest("Missing email");
      }

      await sendPasswordResetEmail(email);
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.post("/refresh", async (req, res, next) => {
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
      const profile = await getUserProfile(decoded.uid);
      res.status(200).json(profile);
    } catch (e) {
      next(e);
    }
  });

  return router;
}
