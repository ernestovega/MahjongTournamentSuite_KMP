import { Router } from "express";

import { parseErrorReport, recordErrorReport } from "../../services/errorReports";
import { rateLimit } from "../middleware/rateLimit";

/** Reports per minute that one client address may send. A crash loop must not fill the database. */
const REPORTS_PER_MINUTE = 30;

export function telemetryRouter(): Router {
  const router = Router();

  // Public: a crash can happen before the user signs in. App Check and the limit protect the route.
  router.post("/errors", rateLimit({ windowMs: 60_000, max: REPORTS_PER_MINUTE }), async (req, res, next) => {
    try {
      await recordErrorReport(parseErrorReport(req.body));
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  return router;
}
