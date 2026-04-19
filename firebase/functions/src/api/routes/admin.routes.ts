import { Router } from "express";

import { auth } from "../../firebase";
import { badRequest, forbidden } from "../httpError";
import { requireAuth } from "../middleware/requireAuth";
import { requireSuperadmin } from "../middleware/requireSuperadmin";
import { getEmaIdMapping, getUserProfile } from "../../services/usersService";
import { BOOTSTRAP_KEY } from "../../config";

function requireBootstrapKey(req: { header(name: string): string | undefined }): void {
  const expected = BOOTSTRAP_KEY.value();
  if (!expected) {
    throw new Error("Missing secret MTS_BOOTSTRAP_KEY");
  }
  const provided = req.header("x-bootstrap-key");
  if (!provided || provided != expected) {
    throw forbidden("Invalid bootstrap key");
  }
}

export function adminRouter(): Router {
  const router = Router();

  // One-time helper to set superadmin claim.
  router.post("/bootstrapSuperadmin", async (req, res, next) => {
    try {
      requireBootstrapKey(req);

      const uid = String(req.body?.uid ?? "").trim();
      if (!uid) {
        throw badRequest("Missing uid");
      }

      await auth.setCustomUserClaims(uid, { superadmin: true });

      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.get("/whoami", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; superadmin?: boolean };
      res.status(200).json({ uid: decoded.uid, superadmin: decoded.superadmin === true });
    } catch (e) {
      next(e);
    }
  });

  router.get("/users/lookup", requireAuth, requireSuperadmin, async (req, res, next) => {
    try {
      const identifier = String(req.query.identifier ?? "").trim();
      if (!identifier) {
        throw badRequest("Missing identifier");
      }

      if (identifier.includes("@")) {
        const user = await auth.getUserByEmail(identifier);
        const profile = await getUserProfile(user.uid);
        res.status(200).json(profile);
        return;
      }

      const mapping = await getEmaIdMapping(identifier);
      const profile = await getUserProfile(mapping.uid);
      res.status(200).json(profile);
    } catch (e) {
      next(e);
    }
  });

  return router;
}
