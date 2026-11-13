import { Router } from "express";

import { parseErrorReport, recordErrorReport } from "../../services/errorReports";
import { rateLimit } from "../middleware/rateLimit";

/** Reports per minute that one client address may send. A crash loop must not fill the database. */
const REPORTS_PER_MINUTE = 30;
/** Reports per minute that one function instance accepts from everyone, so many addresses cannot fill it either. */
const REPORTS_PER_INSTANCE_PER_MINUTE = 300;

export function telemetryRouter(): Router {
  const router = Router();

  // Public: a crash can happen before the user signs in. App Check and the limit protect the route.
  const perAddress = rateLimit({ windowMs: 60_000, max: REPORTS_PER_MINUTE });
  const perInstance = rateLimit({ windowMs: 60_000, max: REPORTS_PER_INSTANCE_PER_MINUTE, key: () => "all" });
  router.post("/errors", perAddress, perInstance, async (req, res, next) => {
    try {
      await recordErrorReport(parseErrorReport(req.body));
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  return router;
}
