import { Router } from "express";

import { requireAuth } from "../middleware/requireAuth";
import { requireEditor } from "../middleware/requireEditor";
import { resolveMapLink } from "../../services/mapLinkResolver";

/** Routes for the venue form. The form turns a short Google Maps link into a full link and reads the place from it. */
export function venuesRouter(): Router {
  const router = Router();

  router.post("/resolve-link", requireAuth, requireEditor, async (req, res, next) => {
    try {
      res.status(200).json({ url: await resolveMapLink(req.body?.url) });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
