import { NextFunction, Request, Response, Router } from "express";
import { requireAuth } from "../middleware/requireAuth";
import { badRequest } from "../httpError";
import { isStaff, loadAccess } from "../../services/accessService";
import { visibleTournamentIdSet } from "../../services/tournamentScope";
import { listAllTournaments } from "../../services/tournamentsService";
import { listHiddenEmaIds } from "../../services/playersService";
import { getUserProfile } from "../../services/usersService";
import { pathParam } from "../pathParam";
import { readGroupFilter } from "../groupFilter";
import { hiddenRoundsByTournament, withoutHiddenRounds } from "../../services/resultsVisibility";
import { groupTournament, loadGroupStatistics } from "../../services/statisticsGroups";
import {
  loadOverview,
  loadPersonStatistics,
  loadStandings,
  MAX_STANDINGS_PER_REQUEST,
  publicTournamentIds,
} from "../../services/statisticsStore";

/** The statistics routes of /tournaments: overview, groups and the statistics of a person. Registered by tournamentsRouter, in its order. */
export function registerStatisticsRoutes(router: Router): void {
  /**
   * The statistics of all tournaments together: sums and the leaderboards.
   * The server keeps them up to date when a table changes. This request reads a fixed number of documents.
   * Statistics are public data, so every signed-in account can read them.
   */
  router.get("/statistics/overview", requireAuth, async (_req, res, next) => {
    try {
      res.status(200).json(await loadOverview());
    } catch (e) {
      next(e);
    }
  });

  /**
   * The ids of the tournaments whose statistics the signed-in account can see, or null for a super admin.
   * Statistics are public data: every account sees the public tournaments, and the staff also the ones that it manages.
   */
  const visibleTournamentIds = async (decoded: { uid: string }) => {
    const access = await loadAccess(decoded.uid);
    if (access.superAdmin && !access.disabled) return null;
    const visible = await publicTournamentIds();
    // An enabled super admin left above, so the set is there.
    if (!access.disabled && isStaff(access)) (await visibleTournamentIdSet(access))!.forEach((id) => visible.add(id));
    return visible;
  };

  /**
   * The hidden results (see resultsVisibility) that this account cannot see: those of the tournaments it does not
   * manage. A super admin sees them all.
   */
  const hiddenForAccount = async (decoded: { uid: string }): Promise<Map<string, number>> => {
    const hidden = await hiddenRoundsByTournament();
    if (hidden.size === 0) return hidden;
    const access = await loadAccess(decoded.uid);
    if (access.superAdmin && !access.disabled) return new Map();
    const managed = !access.disabled && isStaff(access) ? (await visibleTournamentIdSet(access))! : new Set<string>();
    managed.forEach((id) => hidden.delete(id));
    return hidden;
  };

  const readEmaParam = (value: string): string => {
    const emaId = value.trim();
    if (emaId === "" || emaId.length > 64) throw badRequest("Invalid EMA id");
    return emaId;
  };

  /** The EMA number of the signed-in account. It is empty when the account is not linked to a player. */
  const ownEmaId = async (uid: string): Promise<string> => (await getUserProfile(uid)).emaId ?? "";

  const readTournamentIds = (value: unknown): string[] => {
    const ids = String(value ?? "").split(",").map((id) => id.trim()).filter((id) => id !== "");
    if (ids.length > MAX_STANDINGS_PER_REQUEST) throw badRequest("Too many tournaments", { max: MAX_STANDINGS_PER_REQUEST });
    return ids;
  };

  /**
   * The results of one player, in the tournaments that the account can see. The own route reads the player of the account.
   * The route with an EMA number is open to every signed-in account, so a player can look at the statistics of others.
   */
  const personHandler = (own: boolean) => async (req: Request, res: Response, next: NextFunction) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(pathParam(req, "emaId"));
      if (emaId === "") {
        res.status(200).json({ emaId, tournaments: [] });
        return;
      }
      const [statistics, hidden] = await Promise.all([loadPersonStatistics(emaId, await visibleTournamentIds(decoded)), hiddenForAccount(decoded)]);
      res.status(200).json(withoutHiddenRounds(statistics, hidden));
    } catch (e) {
      next(e);
    }
  };

  /** The final places of one player in a few tournaments. The app asks for the tournaments on screen. */
  const standingsHandler = (own: boolean) => async (req: Request, res: Response, next: NextFunction) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(pathParam(req, "emaId"));
      const [allowed, hidden] = await Promise.all([visibleTournamentIds(decoded), hiddenForAccount(decoded)]);
      const ids = readTournamentIds(req.query.tournaments).filter((id) => (allowed == null || allowed.has(id)) && !hidden.has(id));
      res.status(200).json({ standings: emaId === "" ? [] : await loadStandings(emaId, ids) });
    } catch (e) {
      next(e);
    }
  };

  /** The statistics of a group of tournaments and players, in the tournaments that the account can see. See readGroupFilter. */
  router.get("/statistics/groups", requireAuth, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean };
      const filter = readGroupFilter(req.query as Record<string, unknown>);
      const [tournaments, allowed, hiddenResults] = await Promise.all([
        listAllTournaments(), visibleTournamentIds(decoded), hiddenForAccount(decoded),
      ]);
      const visible = tournaments
        .filter((tournament) => (allowed == null || allowed.has(tournament.id)) && !hiddenResults.has(tournament.id))
        .map(groupTournament);
      // An administrator also sees the people who asked not to be shown publicly.
      const hidden = allowed == null ? new Set<string>() : await listHiddenEmaIds();
      res.status(200).json(await loadGroupStatistics(visible, filter, hidden));
    } catch (e) {
      next(e);
    }
  });

  router.get("/statistics/me", requireAuth, personHandler(true));
  router.get("/statistics/me/standings", requireAuth, standingsHandler(true));
  router.get("/statistics/persons/:emaId", requireAuth, personHandler(false));
  router.get("/statistics/persons/:emaId/standings", requireAuth, standingsHandler(false));
}
