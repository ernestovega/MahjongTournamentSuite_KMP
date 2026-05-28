import express from "express";

import { errorHandler } from "./middleware/errorHandler";
import { adminRouter } from "./routes/admin.routes";
import { authRouter } from "./routes/auth.routes";
import { tournamentsRouter } from "./routes/tournaments.routes";

export function buildApp(): express.Express {
  const app = express();

  app.use(express.json({ limit: "1mb" }));

  app.get("/health", (_req, res) => {
    res.status(200).json({ ok: true });
  });

  app.get("/version", (_req, res) => {
    res.status(200).json({
      ok: true,
      project: process.env.GCLOUD_PROJECT ?? null,
      service: process.env.K_SERVICE ?? null,
      revision: process.env.K_REVISION ?? null,
      target: process.env.FUNCTION_TARGET ?? null,
      node: process.version,
    });
  });

  app.use("/auth", authRouter());
  app.use("/admin", adminRouter());
  app.use("/tournaments", tournamentsRouter());

  app.use(errorHandler);

  return app;
}
