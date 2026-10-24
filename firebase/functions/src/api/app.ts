import express from "express";

import { gzipJson } from "./middleware/gzipJson";
import { errorHandler } from "./middleware/errorHandler";
import { adminRouter } from "./routes/admin.routes";
import { authRouter } from "./routes/auth.routes";
import { configRouter } from "./routes/config.routes";
import { countriesRouter } from "./routes/countries.routes";
import { playerRouter } from "./routes/player.routes";
import { playersRouter } from "./routes/players.routes";
import { tournamentsRouter } from "./routes/tournaments.routes";
import { syncRouter } from "./routes/sync.routes";
import { venuesRouter } from "./routes/venues.routes";

export function buildApp(): express.Express {
  const app = express();

  // The API runs behind Firebase Hosting and Google front ends. Trust them to get the client address.
  app.set("trust proxy", true);
  app.disable("x-powered-by");
  // Answers hold personal data. A browser or a proxy must not keep them, unless a route says otherwise.
  app.use((_req, res, next) => {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cache-Control", "no-store");
    next();
  });
  app.use(gzipJson);
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
    res.status(200).json({ ok: true, version: "1.0.0" });
  });

  app.use("/auth", authRouter());
  app.use("/config", configRouter());
  app.use("/countries", countriesRouter());
  app.use("/ema-player-registry", playersRouter());
  app.use("/admin", adminRouter());
  app.use("/sync", syncRouter());
  app.use("/player", playerRouter());
  app.use("/tournaments", tournamentsRouter());
  app.use("/venues", venuesRouter());

  app.use(errorHandler);

  return app;
}
