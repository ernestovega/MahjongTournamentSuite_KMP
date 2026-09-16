import { Router } from "express";

import { requireAuth } from "../middleware/requireAuth";
import { listCountries } from "../../services/countriesService";

export function countriesRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      res.status(200).json({ countries: await listCountries() });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
