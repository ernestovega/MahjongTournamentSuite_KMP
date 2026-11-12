import { Router } from "express";

import { rateLimit } from "../middleware/rateLimit";
import { readSupportMessage, sendSupportMessage } from "../../services/supportService";

/** The "Need help?" form of the sign-in screen. It is public, so it has tight limits against spam. */
export function supportRouter(): Router {
  const router = Router();
  // Each address may send 3 messages in 15 minutes, and one instance at most 30 in an hour.
  const perAddress = rateLimit({ windowMs: 15 * 60_000, max: 3 });
  const perInstance = rateLimit({ windowMs: 60 * 60_000, max: 30, key: () => "all" });

  router.post("/message", perAddress, perInstance, async (req, res, next) => {
    try {
      const message = readSupportMessage(req.body);
      if (message) await sendSupportMessage(message);
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  return router;
}
