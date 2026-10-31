import express from "express";

import { gzipJson } from "./middleware/gzipJson";
import { appCheck } from "./middleware/appCheck";
import { errorHandler } from "./middleware/errorHandler";
import { rateLimit } from "./middleware/rateLimit";
import { adminRouter } from "./routes/admin.routes";
import { authRouter } from "./routes/auth.routes";
import { configRouter } from "./routes/config.routes";
import { countriesRouter } from "./routes/countries.routes";
import { playerRouter } from "./routes/player.routes";
import { playersRouter } from "./routes/players.routes";
import { tournamentsRouter } from "./routes/tournaments.routes";
import { syncRouter } from "./routes/sync.routes";
import { telemetryRouter } from "./routes/telemetry.routes";
import { venuesRouter } from "./routes/venues.routes";

const READ_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const WRITES_PER_MINUTE = 120;

/** The route groups of the API and where each one is mounted. A test reads this list to check every route. */
export const MOUNTED_ROUTERS: Array<[string, () => express.Router]> = [
  ["/auth", authRouter],
  ["/config", configRouter],
  ["/countries", countriesRouter],
  ["/ema-player-registry", playersRouter],
  ["/admin", adminRouter],
  ["/sync", syncRouter],
  ["/player", playerRouter],
  ["/tournaments", tournamentsRouter],
  ["/venues", venuesRouter],
  ["/telemetry", telemetryRouter],
];

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

  // Every route except /health needs a valid App Check token when the mode is "enforce".
  app.use(appCheck());

  // A brake on writes. The key is the address plus the token, so players who share one venue network do not
  // share one limit.
  const writeLimit = rateLimit({
    windowMs: 60_000,
    max: WRITES_PER_MINUTE,
    key: (req) => `${req.ip ?? "unknown"}|${(req.header("authorization") ?? "").slice(-32)}`,
  });
  app.use((req, res, next) => (READ_METHODS.has(req.method) ? next() : writeLimit(req, res, next)));

  MOUNTED_ROUTERS.forEach(([prefix, router]) => app.use(prefix, router()));

  app.use(errorHandler);

  return app;
}
