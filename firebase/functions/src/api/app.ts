import express from "express";

import { errorHandler } from "./middleware/errorHandler";
import { adminRouter } from "./routes/admin.routes";
import { authRouter } from "./routes/auth.routes";
import { countriesRouter } from "./routes/countries.routes";
import { playersRouter } from "./routes/players.routes";
import { tournamentsRouter } from "./routes/tournaments.routes";
import { syncRouter } from "./routes/sync.routes";

export function buildApp(): express.Express {
  const app = express();

  app.use(express.json({ limit: "8mb" }));

  // Firebase Hosting keeps the rewrite source in the forwarded path.
  // Remove it so hosted web requests use the same routes as direct function calls.
  app.use((req, _res, next) => {
    if (req.url === "/api") {
      req.url = "/";
    } else if (req.url.startsWith("/api/")) {
      req.url = req.url.slice(4);
    }
    next();
  });

  app.get("/health", (_req, res) => {
    res.status(200).json({ ok: true });
  });

  app.get("/version", (_req, res) => {
    res.status(200).json({
      ok: true,
      version: "1.0.0",
      project: process.env.GCLOUD_PROJECT ?? null,
      service: process.env.K_SERVICE ?? null,
      revision: process.env.K_REVISION ?? null,
      target: process.env.FUNCTION_TARGET ?? null,
      node: process.version,
    });
  });

  app.use("/auth", authRouter());
  app.use("/countries", countriesRouter());
  app.use("/ema-player-registry", playersRouter());
  app.use("/admin", adminRouter());
  app.use("/sync", syncRouter());
  app.use("/tournaments", tournamentsRouter());

  app.use(errorHandler);

  return app;
}
