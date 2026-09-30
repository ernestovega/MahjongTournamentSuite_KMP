import { Router } from "express";

import { requireAuth } from "../middleware/requireAuth";
import { getGlobalDataVersions } from "../../services/dataVersionsService";

export function syncRouter(): Router {
  const router = Router();

  router.get("/manifest", requireAuth, async (_req, res, next) => {
    try {
      res.setHeader("Cache-Control", "no-store");
      res.status(200).json({ resources: await getGlobalDataVersions() });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
