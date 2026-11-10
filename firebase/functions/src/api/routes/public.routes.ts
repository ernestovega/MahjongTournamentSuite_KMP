import { NextFunction, Request, Response, Router } from "express";

import { db } from "../../firebase";
import { listCountries } from "../../services/countriesService";
import { getGlobalDataVersions, getTournamentManifest } from "../../services/dataVersionsService";
import { listPlayers } from "../../services/playersService";
import { loadOverview, loadPersonStatistics, loadStandings, MAX_STANDINGS_PER_REQUEST } from "../../services/statisticsStore";
import {
  getAllTablesWithHands,
  getRoundTablesWithHands,
  getTableWithHands,
} from "../../services/tableManagerService";
import {
  listTournamentPlayers,
  listTournamentRounds,
  listTournamentTables,
  listTournamentTeams,
} from "../../services/tournamentContentService";
import { getTournament, listAllTournaments, type Tournament } from "../../services/tournamentsService";
import { getUserProfile } from "../../services/usersService";
import { assertSafeDocumentId, badRequest, notFound } from "../httpError";
import { requireAuth } from "../middleware/requireAuth";
import { rateLimit } from "../middleware/rateLimit";
import { pathParam } from "../pathParam";

/**
 * Seconds that Firebase Hosting keeps a public answer in its CDN.
 * Every viewer of a tournament shares one call to the function in each window.
 */
export const PUBLIC_CACHE_SECONDS = {
  /** A tournament in play changes with every hand. */
  live: 15,
  /** A completed tournament changes only when an admin corrects it. */
  completed: 300,
  /** The tournament list and the statistics of people. */
  lists: 60,
  /** The EMA registry and the countries. */
  registry: 3600,
} as const;

/** The browser keeps an answer for a few seconds only, so polling sees new data soon after the CDN does. */
const BROWSER_MAX_AGE_SECONDS = 5;
const READS_PER_MINUTE = 600;

function cacheFor(res: Response, seconds: number): void {
  res.setHeader("Cache-Control", `public, max-age=${Math.min(seconds, BROWSER_MAX_AGE_SECONDS)}, s-maxage=${seconds}`);
}

/** The fields of a tournament that anyone can see. The creator stays private. */
export function publicTournament(tournament: Tournament): Omit<Tournament, "createdByUid" | "createdByName"> {
  const { createdByUid, createdByName, ...rest } = tournament;
  return rest;
}

const readEmaParam = (value: unknown): string => {
  const emaId = String(value ?? "").trim();
  if (emaId === "" || emaId.length > 64) throw badRequest("Invalid EMA id");
  return emaId;
};

const readPositiveInt = (value: unknown, name: string): number => {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw badRequest(`${name} must be a positive integer`);
  return number;
};

type Handler = (req: Request, res: Response) => Promise<void>;
const handle = (handler: Handler) => (req: Request, res: Response, next: NextFunction) => {
  handler(req, res).catch(next);
};

/** The ids of the tournaments that anyone can see. */
async function publicTournamentIds(): Promise<Set<string>> {
  const snapshot = await db.collection("tournaments").select("isPublic").get();
  return new Set(snapshot.docs.filter((doc) => doc.get("isPublic") !== false).map((doc) => doc.id));
}

/**
 * Read routes for everyone, without an account. They never read the token, so a cached answer never holds the data
 * of an editor. Writes and the data of the own account stay on the routes that need a token.
 */
export function publicRouter(): Router {
  const router = Router();

  router.use(rateLimit({ windowMs: 60_000, max: READS_PER_MINUTE }));

  // Only a public tournament answers. Its cache time comes from its state.
  router.param("tournamentId", (req, res, next, value: string) => {
    try {
      assertSafeDocumentId(value, "tournament id");
    } catch (error) {
      next(error);
      return;
    }
    db.collection("tournaments").doc(value).get().then((snapshot) => {
      if (!snapshot.exists || snapshot.get("isPublic") === false) {
        next(notFound("Tournament not found"));
        return;
      }
      res.locals.publicCacheSeconds = snapshot.get("isCompleted") === true
        ? PUBLIC_CACHE_SECONDS.completed
        : PUBLIC_CACHE_SECONDS.live;
      next();
    }, next);
  });
  const tournamentCache = (res: Response) => cacheFor(res, Number(res.locals.publicCacheSeconds ?? PUBLIC_CACHE_SECONDS.live));

  router.get("/sync/manifest", handle(async (_req, res) => {
    cacheFor(res, PUBLIC_CACHE_SECONDS.live);
    res.status(200).json({ resources: await getGlobalDataVersions() });
  }));

  router.get("/tournaments", handle(async (_req, res) => {
    const tournaments = (await listAllTournaments()).filter((tournament) => tournament.isPublic).map(publicTournament);
    cacheFor(res, PUBLIC_CACHE_SECONDS.lists);
    res.status(200).json({ tournaments });
  }));

  router.get("/tournaments/:tournamentId", handle(async (req, res) => {
    const tournament = await getTournament(pathParam(req, "tournamentId"));
    if (tournament == null) throw notFound("Tournament not found");
    tournamentCache(res);
    res.status(200).json(publicTournament(tournament));
  }));

  router.get("/tournaments/:tournamentId/sync/manifest", handle(async (req, res) => {
    tournamentCache(res);
    res.status(200).json(await getTournamentManifest(pathParam(req, "tournamentId")));
  }));

  router.get("/tournaments/:tournamentId/players", handle(async (req, res) => {
    const players = await listTournamentPlayers(pathParam(req, "tournamentId"), { withAccounts: false });
    tournamentCache(res);
    // Who plays and where. Never the accounts or the emails.
    res.status(200).json({
      players: players.map((player) => ({ ...player, accountUid: null, hasAccount: false, email: null, hasEmail: false })),
    });
  }));

  router.get("/tournaments/:tournamentId/teams", handle(async (req, res) => {
    const teams = await listTournamentTeams(pathParam(req, "tournamentId"));
    tournamentCache(res);
    res.status(200).json({ teams });
  }));

  router.get("/tournaments/:tournamentId/rounds", handle(async (req, res) => {
    const rounds = await listTournamentRounds(pathParam(req, "tournamentId"));
    tournamentCache(res);
    res.status(200).json({ rounds });
  }));

  router.get("/tournaments/:tournamentId/tables", handle(async (req, res) => {
    const roundId = req.query.roundId == null ? null : readPositiveInt(req.query.roundId, "roundId");
    const tables = await listTournamentTables(pathParam(req, "tournamentId"), roundId);
    tournamentCache(res);
    res.status(200).json({ tables });
  }));

  router.get("/tournaments/:tournamentId/tables-with-hands", handle(async (req, res) => {
    const tables = await getAllTablesWithHands({ tournamentId: pathParam(req, "tournamentId") });
    tournamentCache(res);
    res.status(200).json({ tables });
  }));

  router.get("/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", handle(async (req, res) => {
    const roundId = readPositiveInt(pathParam(req, "roundId"), "roundId");
    const tables = await getRoundTablesWithHands({ tournamentId: pathParam(req, "tournamentId"), roundId });
    tournamentCache(res);
    res.status(200).json({ tables });
  }));

  router.get("/tournaments/:tournamentId/tables/:roundId/:tableId", handle(async (req, res) => {
    const roundId = readPositiveInt(pathParam(req, "roundId"), "roundId");
    const tableId = readPositiveInt(pathParam(req, "tableId"), "tableId");
    const data = await getTableWithHands({ tournamentId: pathParam(req, "tournamentId"), roundId, tableId });
    tournamentCache(res);
    res.status(200).json(data);
  }));

  // The sums and leaderboards of all tournaments.
  router.get("/statistics/overview", handle(async (_req, res) => {
    cacheFor(res, PUBLIC_CACHE_SECONDS.lists);
    res.status(200).json(await loadOverview());
  }));

  // The results of one person in the public tournaments.
  router.get("/statistics/persons/:emaId", handle(async (req, res) => {
    const emaId = readEmaParam(pathParam(req, "emaId"));
    cacheFor(res, PUBLIC_CACHE_SECONDS.lists);
    res.status(200).json(await loadPersonStatistics(emaId, await publicTournamentIds()));
  }));

  router.get("/statistics/persons/:emaId/standings", handle(async (req, res) => {
    const emaId = readEmaParam(pathParam(req, "emaId"));
    const ids = String(req.query.tournaments ?? "").split(",").map((id) => id.trim()).filter((id) => id !== "");
    if (ids.length > MAX_STANDINGS_PER_REQUEST) throw badRequest("Too many tournaments", { max: MAX_STANDINGS_PER_REQUEST });
    const allowed = await publicTournamentIds();
    cacheFor(res, PUBLIC_CACHE_SECONDS.lists);
    res.status(200).json({ standings: await loadStandings(emaId, ids.filter((id) => allowed.has(id))) });
  }));

  // The EMA registry: name, country and number. The photos stay private.
  router.get("/players", handle(async (_req, res) => {
    const players = (await listPlayers()).map((player) => ({ ...player, photoUrl: null }));
    cacheFor(res, PUBLIC_CACHE_SECONDS.registry);
    res.status(200).json({ players });
  }));

  router.get("/countries", handle(async (_req, res) => {
    cacheFor(res, PUBLIC_CACHE_SECONDS.registry);
    res.status(200).json({ countries: await listCountries() });
  }));

  return router;
}

export type MyTournamentSlot = { tournamentId: string; playerId: number; teamId: number };

/**
 * Who the signed-in account is in the tournaments: its EMA number and its slot and team in each tournament.
 * The apps read everything else from the public routes and use this to mark the own player.
 */
export function meRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, handle(async (_req, res) => {
    const decoded = res.locals.auth as { uid: string };
    const emaId = (await getUserProfile(decoded.uid).catch(() => null))?.emaId ?? "";
    let slots: MyTournamentSlot[] = [];
    if (emaId !== "") {
      const snapshot = await db.collectionGroup("players").where("assignedEmaId", "==", emaId).get();
      slots = snapshot.docs.flatMap((slot) => {
        const tournamentId = slot.ref.parent.parent?.id;
        if (tournamentId == null) return [];
        return [{ tournamentId, playerId: Number(slot.get("id") ?? slot.id), teamId: Number(slot.get("team") ?? 0) }];
      });
    }
    res.setHeader("Cache-Control", "no-store");
    res.status(200).json({ emaId, tournaments: slots });
  }));

  return router;
}
