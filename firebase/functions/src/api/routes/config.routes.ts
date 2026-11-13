import { Router } from "express";

import { getFeatureFlags } from "../../services/featureFlagsService";

export function configRouter(): Router {
  const router = Router();

  // Public: the sign-in screens also need the flags.
  router.get("/", async (_req, res, next) => {
    try {
      const flags = await getFeatureFlags();
      // The same for everyone, so the CDN of Firebase Hosting may share it for a minute. A changed flag reaches the apps
      // within that minute.
      res.setHeader("Cache-Control", "public, max-age=5, s-maxage=60");
      res.status(200).json(flags);
    } catch (error) {
      next(error);
    }
  });

  return router;
}
