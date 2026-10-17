import { Router } from "express";

import { getFeatureFlags } from "../../services/featureFlagsService";

export function configRouter(): Router {
  const router = Router();

  // Public: the sign-in screens also need the flags.
  router.get("/", async (_req, res, next) => {
    try {
      res.status(200).json(await getFeatureFlags());
    } catch (error) {
      next(error);
    }
  });

  return router;
}
