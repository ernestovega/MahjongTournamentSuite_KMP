import { Router } from "express";

import { requireAuth } from "../middleware/requireAuth";
import { requireEditor } from "../middleware/requireEditor";
import { PLACES_API_KEY } from "../../config";
import { resolveMapLink } from "../../services/mapLinkResolver";
import { lookupPlace } from "../../services/placesLookup";

function readPlacesKey(): string {
  try {
    return PLACES_API_KEY.value();
  } catch (_error) {
    return "";
  }
}

/** Routes for the venue form. The form turns a short Google Maps link into a full link and reads the place from it. */
export function venuesRouter(): Router {
  const router = Router();

  router.post("/resolve-link", requireAuth, requireEditor, async (req, res, next) => {
    try {
      const url = await resolveMapLink(req.body?.url);
      // The place is null when Places is off or finds nothing. The form then reads the place from the link text.
      const place = await lookupPlace(url, readPlacesKey());
      res.status(200).json({ url, place });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
