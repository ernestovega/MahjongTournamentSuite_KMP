import { NextFunction, Request, Response, Router } from "express";
import { recordActivity, tournamentNameOf } from "../../services/activityLog";

import { auth, db } from "../../firebase";
import { requireAuth } from "../middleware/requireAuth";
import { requireEditor } from "../middleware/requireEditor";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireTournamentEditor } from "../middleware/requireTournamentEditor";
import { requireTournamentReader } from "../middleware/requireTournamentReader";
import { runAccountAction } from "../../services/playerAccountsService";
import { assertSafeDocumentId, badRequest, forbidden } from "../httpError";
import { hasAdminClaim } from "../../models/globalRole";
import { listTournamentMembers, removeTournamentMember, upsertTournamentMember } from "../../services/membersService";
import {
  createTournament,
  deleteTournament,
  listAllTournaments,
  listTournamentsForPlayer,
  listTournamentsForUser,
  renameTournament,
  updateTournamentSettings,
} from "../../services/tournamentsService";
import {
  buildIdCardProofPdf,
  generateTournamentIdCards,
  idCardSheetGrid,
  type IdCardSheetOptions,
} from "../../services/idCardsService";
import { generateTournamentIdList } from "../../services/idListService";
import { generateEmaReport } from "../../services/emaReportService";
import { readSubstitution } from "../../services/playerSubstitution";
import {
  assignTournamentPlayer,
  setPlayerSubstitution,
  listTournamentPlayers,
  listTournamentRounds,
  listTournamentTables,
  listTournamentTeams,
  updateTournamentTeam,
} from "../../services/tournamentContentService";
import { playerExists, validateEmaId } from "../../services/playersService";
import {
  getAllTablesWithHands,
  getRoundTablesWithHands,
  getTableWithHands,
  resetTable,
  saveTableState,
} from "../../services/tableManagerService";
import { actorFromToken } from "../../services/changeLog";
import { getUserProfile } from "../../services/usersService";
import { isValidPlayerCount, MIN_TOURNAMENT_PLAYERS } from "../../services/tournamentContentRules";
import { isValidIsoDate, isValidIsoDateRange } from "../../services/tournamentDates";
import { normalizeVenue, venueRequiredProblems } from "../../services/tournamentVenue";
import { getTournamentManifest } from "../../services/dataVersionsService";
import { parseSaveTableRequest } from "../tableRequests";
import { pathParam } from "../pathParam";
import { loadOverview, loadPersonStatistics, loadStandings, MAX_STANDINGS_PER_REQUEST } from "../../services/statisticsStore";
import { applyPlayerImport, exportPlayers, previewPlayerImport } from "../../services/playerImportService";
import { runTournamentIntegrityCheck } from "../../services/tournamentIntegrityService";

/** Reads the optional A4 sheet layout of the ID cards. No `layout=sheet` value means one card per page. */
function readIdCardSheetOptions(query: Request["query"]): IdCardSheetOptions | undefined {
  if (query.layout !== "sheet") return undefined;
  const cardsPerSheet = Number(query.cardsPerSheet ?? 10);
  const scalePercent = Number(query.scalePercent ?? 100);
  const landscape = query.landscape === "true";
  if (!Number.isInteger(cardsPerSheet) || cardsPerSheet < 1 || cardsPerSheet > 40) {
    throw badRequest("Invalid number of cards per sheet", { value: query.cardsPerSheet });
  }
  if (!Number.isFinite(scalePercent) || scalePercent < 25 || scalePercent > 400) {
    throw badRequest("Invalid card size", { value: query.scalePercent });
  }
  const options: IdCardSheetOptions = {
    cardsPerSheet,
    scalePercent,
    landscape,
    doubleSided: query.doubleSided !== "false",
    cutMarks: query.cutMarks !== "false",
  };
  if (idCardSheetGrid(options) == null) throw badRequest("The card size does not fit on an A4 sheet", { scalePercent });
  return options;
}

/** Only an admin changes the tables of a completed tournament, so late changes to the results are traceable. */
async function assertTournamentOpenForEditor(tournamentId: string, token: { admin?: boolean } | undefined): Promise<void> {
  if (hasAdminClaim(token)) return;
  const snapshot = await db.collection("tournaments").doc(tournamentId).get();
  if (snapshot.get("isCompleted") === true) {
    throw forbidden("The tournament is completed. Only an admin can change its tables");
  }
}

export function tournamentsRouter(): Router {
  const router = Router();

  router.param("tournamentId", (_req, _res, next, value: string) => {
    try {
      assertSafeDocumentId(value, "tournament id");
      next();
    } catch (error) {
      next(error);
    }
  });

  router.get(
    "/:tournamentId/sync/manifest",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        res.setHeader("Cache-Control", "no-store");
        res.status(200).json(await getTournamentManifest(pathParam(req, "tournamentId")));
      } catch (error) {
        next(error);
      }
    },
  );

  // Admins see every tournament. Editors see assigned ones. Players see the ones where they are registered.
  // The player app sends scope=player. It shows only the tournaments where the account is a player, whatever its role.
  router.get("/", requireAuth, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean; player?: boolean };
      let tournaments: Awaited<ReturnType<typeof listAllTournaments>>;
      if (req.query.scope === "player") {
        tournaments = await listTournamentsForPlayer(decoded.uid);
      } else if (decoded.admin === true) {
        tournaments = await listAllTournaments();
      } else {
        const lists = await Promise.all([
          decoded.editor === true ? listTournamentsForUser(decoded.uid) : Promise.resolve([]),
          decoded.player === true ? listTournamentsForPlayer(decoded.uid) : Promise.resolve([]),
        ]);
        const byId = new Map(lists.flat().map((tournament) => [tournament.id, tournament]));
        tournaments = [...byId.values()];
      }
      res.status(200).json({ tournaments });
    } catch (e) {
      next(e);
    }
  });

  /**
   * The statistics of all tournaments together: sums and the leaderboards.
   * The server keeps them up to date when a table changes. This request reads a fixed number of documents.
   */
  router.get("/statistics/overview", requireAuth, requireAdmin, async (_req, res, next) => {
    try {
      res.status(200).json(await loadOverview());
    } catch (e) {
      next(e);
    }
  });

  /**
   * The ids of the tournaments that the signed-in account can see, or null for an administrator.
   * It is the same rule as the tournament list above.
   */
  const visibleTournamentIds = async (decoded: { uid: string; admin?: boolean; editor?: boolean; player?: boolean }) => {
    if (decoded.admin === true) return null;
    const lists = await Promise.all([
      decoded.editor === true ? listTournamentsForUser(decoded.uid) : Promise.resolve([]),
      decoded.player === true ? listTournamentsForPlayer(decoded.uid) : Promise.resolve([]),
    ]);
    return new Set(lists.flat().map((tournament) => tournament.id));
  };

  const readEmaParam = (value: unknown): string => {
    const emaId = String(value ?? "").trim();
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
      const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean; player?: boolean };
      const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(pathParam(req, "emaId"));
      if (emaId === "") {
        res.status(200).json({ emaId, tournaments: [] });
        return;
      }
      res.status(200).json(await loadPersonStatistics(emaId, await visibleTournamentIds(decoded)));
    } catch (e) {
      next(e);
    }
  };

  /** The final places of one player in a few tournaments. The app asks for the tournaments on screen. */
  const standingsHandler = (own: boolean) => async (req: Request, res: Response, next: NextFunction) => {
    try {
      const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean; player?: boolean };
      const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(pathParam(req, "emaId"));
      const allowed = await visibleTournamentIds(decoded);
      const ids = readTournamentIds(req.query.tournaments).filter((id) => allowed == null || allowed.has(id));
      res.status(200).json({ standings: emaId === "" ? [] : await loadStandings(emaId, ids) });
    } catch (e) {
      next(e);
    }
  };

  router.get("/statistics/me", requireAuth, personHandler(true));
  router.get("/statistics/me/standings", requireAuth, standingsHandler(true));
  router.get("/statistics/persons/:emaId", requireAuth, personHandler(false));
  router.get("/statistics/persons/:emaId/standings", requireAuth, standingsHandler(false));

  router.post("/", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };

      const body = (req.body != null && typeof req.body === "object") ? (req.body as Record<string, unknown>) : null;

      const name = String(body?.name ?? "").trim();
      const shortName = String(body?.shortName ?? name.slice(0, 10)).trim();
      const primaryColor = String(body?.primaryColor ?? "#02B16B").trim().toUpperCase();
      const associationLogoContentType = body?.associationLogoContentType == null
        ? null
        : String(body.associationLogoContentType).trim();
      const associationLogoDataBase64 = body?.associationLogoDataBase64 == null
        ? null
        : String(body.associationLogoDataBase64).trim();
      const associationLogoSourceTournamentId = body?.associationLogoSourceTournamentId == null
        ? null
        : String(body.associationLogoSourceTournamentId).trim();
      const eventStartDate = String(body?.eventStartDate ?? "").trim();
      const eventEndDate = String(body?.eventEndDate ?? "").trim();
      const hostCountry = String(body?.hostCountry ?? "").trim().toUpperCase();
      const hostCity = String(body?.hostCity ?? "").trim();
      const isTeams = Boolean(body?.isTeams ?? false);
      const countBestHands = body?.countBestHands !== false;
      const countChickenHands = body?.countChickenHands !== false;

      const numPlayersValue = body?.numPlayers;
      const numRoundsValue = body?.numRounds;
      const numTriesValue = body?.numTries;

      const numPlayers = Number(numPlayersValue ?? NaN);
      const numRounds = Number(numRoundsValue ?? NaN);
      const numTries = Number(numTriesValue ?? NaN);
      const players = Array.isArray(body?.players) ? (body?.players as unknown[]) : null;
      const tables = Array.isArray(body?.tables) ? (body?.tables as unknown[]) : null;

      const issues: Array<{ field: string; message: string; value?: unknown }> = [];
      let venue = normalizeVenue(null);
      try {
        venue = normalizeVenue(body);
      } catch (error) {
        issues.push({ field: "venue", message: error instanceof Error ? error.message : "Invalid venue" });
      }
      if (issues.length === 0) {
        for (const message of venueRequiredProblems(venue)) {
          issues.push({ field: message === "Required" ? "contactEmail" : "venue", message });
        }
      }
      if (!body) issues.push({ field: "body", message: "Request body must be JSON", value: req.body });
      if (!name) issues.push({ field: "name", message: "Required", value: body?.name });
      if (!/^[A-Z]{2}$/.test(hostCountry)) {
        issues.push({ field: "hostCountry", message: "Must use a two-letter country code", value: body?.hostCountry });
      }
      if (!hostCity) issues.push({ field: "hostCity", message: "Required", value: body?.hostCity });
      if (!shortName || shortName.length > 10) {
        issues.push({ field: "shortName", message: "Required, with at most 10 characters", value: body?.shortName });
      }
      if (!/^#[0-9A-F]{6}$/.test(primaryColor)) {
        issues.push({ field: "primaryColor", message: "Must use #RRGGBB format", value: body?.primaryColor });
      }
      if ((associationLogoContentType == null) !== (associationLogoDataBase64 == null)) {
        issues.push({ field: "associationLogo", message: "Content type and image data must be supplied together" });
      }
      if (associationLogoSourceTournamentId && associationLogoDataBase64) {
        issues.push({ field: "associationLogo", message: "Supply an uploaded logo or a reusable logo, not both" });
      }
      if (!isValidIsoDate(eventStartDate)) {
        issues.push({ field: "eventStartDate", message: "Must use yyyy-MM-dd", value: body?.eventStartDate });
      }
      if (!isValidIsoDate(eventEndDate)) {
        issues.push({ field: "eventEndDate", message: "Must use yyyy-MM-dd", value: body?.eventEndDate });
      }
      if (isValidIsoDate(eventStartDate) && isValidIsoDate(eventEndDate) && !isValidIsoDateRange(eventStartDate, eventEndDate)) {
        issues.push({ field: "eventEndDate", message: "Must not be before eventStartDate", value: body?.eventEndDate });
      }

      if (!isValidPlayerCount(numPlayers)) {
        issues.push({ field: "numPlayers", message: `Must be a multiple of 4 and at least ${MIN_TOURNAMENT_PLAYERS}`, value: numPlayersValue });
      }
      if (!Number.isFinite(numRounds) || !Number.isInteger(numRounds) || numRounds <= 0) {
        issues.push({ field: "numRounds", message: "Must be a positive integer", value: numRoundsValue });
      }
      if (!Number.isFinite(numTries) || !Number.isInteger(numTries) || numTries <= 0) {
        issues.push({ field: "numTries", message: "Must be a positive integer", value: numTriesValue });
      }
      if (!players) issues.push({ field: "players", message: "Required (array)", value: body?.players });
      if (!tables) issues.push({ field: "tables", message: "Required (array)", value: body?.tables });

      if (issues.length > 0) {
        throw badRequest("Missing or invalid tournament fields", {
          issues,
          received: body == null ? null : {
            ...body,
            associationLogoDataBase64: associationLogoDataBase64 == null ? null : "[image data]",
          },
          inferred: { name, isTeams, numPlayers, numRounds, numTries, playersCount: players?.length, tablesCount: tables?.length },
        });
      }

      const parsedPlayers = (players ?? []).map((p, idx) => {
        const row = (p != null && typeof p === "object") ? (p as Record<string, unknown>) : null;
        const id = Number(row?.id ?? NaN);
        const team = Number(row?.team ?? NaN);
        const playerName = row?.name == null ? undefined : String(row?.name);
        const country = row?.country == null ? undefined : String(row?.country);
        if (!Number.isFinite(id) || !Number.isInteger(id)) throw badRequest("Invalid player id", { idx, value: row?.id });
        if (!Number.isFinite(team) || !Number.isInteger(team)) throw badRequest("Invalid player team", { idx, value: row?.team });
        return { id, team, name: playerName, country };
      });

      const parsedTables = (tables ?? []).map((t, idx) => {
        const row = (t != null && typeof t === "object") ? (t as Record<string, unknown>) : null;
        const roundId = Number(row?.roundId ?? NaN);
        const tableId = Number(row?.tableId ?? NaN);
        const playerIdsRaw = Array.isArray(row?.playerIds) ? (row?.playerIds as unknown[]) : null;
        const isCompletedRaw = row?.isCompleted;
        const useTotalsOnlyRaw = row?.useTotalsOnly;
        if (!Number.isFinite(roundId) || !Number.isInteger(roundId)) throw badRequest("Invalid table roundId", { idx, value: row?.roundId });
        if (!Number.isFinite(tableId) || !Number.isInteger(tableId)) throw badRequest("Invalid table tableId", { idx, value: row?.tableId });
        if (!playerIdsRaw || playerIdsRaw.length !== 4) throw badRequest("Invalid table playerIds", { idx, value: row?.playerIds });
        if (isCompletedRaw != null && typeof isCompletedRaw !== "boolean") throw badRequest("Invalid table isCompleted", { idx, value: isCompletedRaw });
        if (useTotalsOnlyRaw != null && typeof useTotalsOnlyRaw !== "boolean") throw badRequest("Invalid table useTotalsOnly", { idx, value: useTotalsOnlyRaw });
        const playerIds = playerIdsRaw.map((x) => Number(x ?? NaN));
        for (const pid of playerIds) {
          if (!Number.isFinite(pid) || !Number.isInteger(pid)) {
            throw badRequest("Invalid table playerIds entry", { idx, value: row?.playerIds });
          }
        }
        return {
          roundId,
          tableId,
          playerIds,
          isCompleted: isCompletedRaw as boolean | undefined,
          useTotalsOnly: useTotalsOnlyRaw as boolean | undefined,
        };
      });

      const tournament = await createTournament({
        name,
        shortName,
        primaryColor,
        associationLogoContentType,
        associationLogoDataBase64,
        associationLogoSourceTournamentId,
        eventStartDate,
        eventEndDate,
        hostCountry,
        hostCity,
        ...venue,
        isTeams,
        countBestHands,
        countChickenHands,
        numPlayers,
        numRounds,
        numTries,
        players: parsedPlayers,
        tables: parsedTables,
        roundSchedules: body?.roundSchedules,
        agendaItems: body?.agendaItems,
        matchDurationMinutes: body?.matchDurationMinutes,
        matchEndMode: body?.matchEndMode,
        matchExtraMinutes: body?.matchExtraMinutes,
        timeZone: body?.timeZone,
        createdByUid: decoded.uid,
      });

      await recordActivity({
        action: "tournament.create", actor: actorFromToken(res.locals.auth), tournamentId: tournament.id,
        target: tournament.name,
        details: { shortName, eventStartDate, eventEndDate, hostCountry, hostCity, isTeams, numPlayers, numRounds },
      });
      res.status(200).json(tournament);
    } catch (e) {
      next(e);
    }
  });

  const generateIdCardProof = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const body = (req.body != null && typeof req.body === "object")
        ? req.body as Record<string, unknown>
        : {};
      const shortName = String(body.shortName ?? "").trim();
      const year = String(body.year ?? "").trim();
      const primaryColor = String(body.primaryColor ?? "#02B16B").trim().toUpperCase();
      const associationLogoContentType = body.associationLogoContentType == null
        ? null
        : String(body.associationLogoContentType).trim();
      const associationLogoDataBase64 = body.associationLogoDataBase64 == null
        ? null
        : String(body.associationLogoDataBase64).trim();
      const associationLogoUrl = body.associationLogoUrl == null
        ? null
        : String(body.associationLogoUrl).trim();
      const roundSchedules = body.roundSchedules;

      if (shortName.length > 32 || year.length > 4 || !/^#[0-9A-F]{6}$/.test(primaryColor)) {
        throw badRequest("Invalid ID card preview fields");
      }
      if (associationLogoDataBase64 && associationLogoDataBase64.length > 3_000_000) {
        throw badRequest("ID card preview logo is too large");
      }

      const pdf = await buildIdCardProofPdf({
        shortName,
        year,
        primaryColor,
        associationLogoContentType,
        associationLogoDataBase64,
        associationLogoUrl,
        roundSchedules,
      });
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Cache-Control", "private, max-age=3600");
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  };

  router.post("/id-card-proof", requireAuth, requireEditor, generateIdCardProof);
  // Keep the previous route working for already deployed web bundles.
  router.post("/id-card-preview", requireAuth, requireEditor, generateIdCardProof);

  // Reads the whole tournament and reports wrong or odd data. It changes nothing.
  router.get("/:tournamentId/integrity", requireAuth, requireTournamentEditor, async (req, res, next) => {
    try {
      res.setHeader("Cache-Control", "no-store");
      res.status(200).json(await runTournamentIntegrityCheck(pathParam(req, "tournamentId")));
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-cards", requireAuth, requireTournamentEditor, async (req, res, next) => {
    try {
      const pdf = await generateTournamentIdCards(pathParam(req, "tournamentId"), readIdCardSheetOptions(req.query));
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-id-cards.pdf"`);
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-list", requireAuth, requireTournamentEditor, async (req, res, next) => {
    try {
      const workbook = await generateTournamentIdList(pathParam(req, "tournamentId"));
      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-id-list.xlsx"`);
      res.status(200).send(workbook);
    } catch (e) {
      next(e);
    }
  });

  router.post("/:tournamentId/ema-report", requireAuth, requireTournamentEditor, async (req, res, next) => {
    try {
      const rawRows = Array.isArray(req.body?.rows) ? req.body.rows as unknown[] : null;
      if (rawRows == null || rawRows.length === 0) throw badRequest("Ranking rows are required");
      const rows = rawRows.map((value, index) => {
        if (value == null || typeof value !== "object") throw badRequest("Invalid ranking row", { index });
        const row = value as Record<string, unknown>;
        const parsed = {
          playerId: Number(row.playerId),
          place: Number(row.place),
          tablePoints: Number(row.tablePoints),
          score: Number(row.score),
        };
        if (!Number.isInteger(parsed.playerId) || parsed.playerId <= 0
          || !Number.isInteger(parsed.place) || parsed.place <= 0
          || !Number.isFinite(parsed.tablePoints) || !Number.isFinite(parsed.score)) {
          throw badRequest("Invalid ranking row", { index, value });
        }
        return parsed;
      });
      const report = await generateEmaReport({ tournamentId: pathParam(req, "tournamentId"), rows });
      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-ema.xlsx"`);
      res.status(200).send(report);
    } catch (e) {
      next(e);
    }
  });

  router.put("/:tournamentId/settings", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
      if (body == null) throw badRequest("Request body must be JSON");
      const name = String(body.name ?? "").trim();
      const shortName = String(body.shortName ?? "").trim();
      const primaryColor = String(body.primaryColor ?? "").trim().toUpperCase();
      const eventStartDate = String(body.eventStartDate ?? "").trim();
      const eventEndDate = String(body.eventEndDate ?? "").trim();
      const hostCountry = String(body.hostCountry ?? "").trim().toUpperCase();
      const hostCity = String(body.hostCity ?? "").trim();
      const associationLogoContentType = body.associationLogoContentType == null
        ? null
        : String(body.associationLogoContentType).trim();
      const associationLogoDataBase64 = body.associationLogoDataBase64 == null
        ? null
        : String(body.associationLogoDataBase64).trim();
      const associationLogoSourceTournamentId = body.associationLogoSourceTournamentId == null
        ? null
        : String(body.associationLogoSourceTournamentId).trim();
      if (!name) throw badRequest("Tournament name is required");
      if (!shortName || shortName.length > 10) {
        throw badRequest("Tournament short name must contain 1 to 10 characters");
      }
      if (!/^#[0-9A-F]{6}$/.test(primaryColor)) {
        throw badRequest("Primary color must use #RRGGBB format");
      }
      if (!isValidIsoDateRange(eventStartDate, eventEndDate)) {
        throw badRequest("Tournament dates must use yyyy-MM-dd, and the end date must not be before the start date");
      }
      if (!/^[A-Z]{2}$/.test(hostCountry)) {
        throw badRequest("Host country must use a two-letter country code");
      }
      if (!hostCity) throw badRequest("Host city is required");
      const venue = normalizeVenue(body);
      const venueProblems = venueRequiredProblems(venue);
      if (venueProblems.length > 0) throw badRequest(venueProblems.join(". "));
      if ((associationLogoContentType == null) !== (associationLogoDataBase64 == null)) {
        throw badRequest("Association logo content type and image data must be supplied together");
      }
      if (associationLogoSourceTournamentId && associationLogoDataBase64) {
        throw badRequest("Supply an uploaded logo or a reusable logo, not both");
      }
      const tournament = await updateTournamentSettings({
        tournamentId: pathParam(req, "tournamentId"),
        name,
        shortName,
        primaryColor,
        eventStartDate,
        eventEndDate,
        hostCountry,
        hostCity,
        ...venue,
        associationLogoContentType,
        associationLogoDataBase64,
        associationLogoSourceTournamentId,
        countBestHands: typeof body.countBestHands === "boolean" ? body.countBestHands : undefined,
        countChickenHands: typeof body.countChickenHands === "boolean" ? body.countChickenHands : undefined,
        removeAssociationLogo: body.removeAssociationLogo === true,
        roundSchedules: body.roundSchedules,
        agendaItems: body.agendaItems,
        matchDurationMinutes: body.matchDurationMinutes,
        matchEndMode: body.matchEndMode,
        matchExtraMinutes: body.matchExtraMinutes,
        timeZone: body.timeZone,
      });
      await recordActivity({
        action: "tournament.update", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
        target: name,
        details: {
          shortName, primaryColor, eventStartDate, eventEndDate, hostCountry, hostCity,
          countBestHands: body.countBestHands, countChickenHands: body.countChickenHands,
          matchDurationMinutes: body.matchDurationMinutes, matchEndMode: body.matchEndMode,
          matchExtraMinutes: body.matchExtraMinutes, timeZone: body.timeZone,
          logo: body.removeAssociationLogo === true ? "removed"
            : associationLogoDataBase64 != null || associationLogoSourceTournamentId ? "changed" : undefined,
          roundSchedules: body.roundSchedules, agendaItems: body.agendaItems,
        },
      });
      res.status(200).json(tournament);
    } catch (e) {
      next(e);
    }
  });

  router.delete("/:tournamentId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const removed = await tournamentNameOf(pathParam(req, "tournamentId"));
      await deleteTournament(pathParam(req, "tournamentId"));
      await recordActivity({
        action: "tournament.delete", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
        target: removed,
      });
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.put("/:tournamentId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
      const name = String(body?.name ?? "").trim();
      if (!name) throw badRequest("Tournament name is required");

      await renameTournament(pathParam(req, "tournamentId"), name);
      await recordActivity({
        action: "tournament.rename", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
        target: name,
      });
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.get(
    "/:tournamentId/users/lookup",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const email = String(req.query.email ?? "").trim();
        if (!email.includes("@")) throw badRequest("Enter a user email address");
        const profile = await auth.getUserByEmail(email).then((user) => getUserProfile(user.uid));
        res.status(200).json(profile);
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/members",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const members = await listTournamentMembers(pathParam(req, "tournamentId"));
        res.status(200).json({ members });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/players/:playerId",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const playerId = Number(pathParam(req, "playerId"));
        const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
        const rawEmaId = body?.emaId;
        const emaId = rawEmaId == null ? null : validateEmaId(rawEmaId);
        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        if (emaId != null && !(await playerExists(emaId))) {
          throw badRequest("EMA player does not exist");
        }

        await assignTournamentPlayer({
          tournamentId: pathParam(req, "tournamentId"),
          playerId,
          emaId,
          uid: (res.locals.auth as { uid: string }).uid,
        });
        await recordActivity({
          action: "player.assign", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `player ${playerId}`,
          details: { emaId },
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/players/:playerId/substitution",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const playerId = Number(pathParam(req, "playerId"));
        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        const substitution = readSubstitution(req.body);
        if (substitution == null) throw badRequest("A substitution needs a reason and a round");
        await setPlayerSubstitution({
          tournamentId: pathParam(req, "tournamentId"),
          playerId,
          substitution,
          uid: (res.locals.auth as { uid: string }).uid,
        });
        await recordActivity({
          action: "player.substitute", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `player ${playerId}`, details: { substitution: { ...substitution } },
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.delete(
    "/:tournamentId/players/:playerId/substitution",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const playerId = Number(pathParam(req, "playerId"));
        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        await setPlayerSubstitution({
          tournamentId: pathParam(req, "tournamentId"),
          playerId,
          substitution: null,
          uid: (res.locals.auth as { uid: string }).uid,
        });
        await recordActivity({
          action: "player.clearSubstitution", actor: actorFromToken(res.locals.auth),
          tournamentId: pathParam(req, "tournamentId"), target: `player ${playerId}`,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/members/:uid",
    requireAuth,
    requireAdmin,
    async (req, res, next) => {
      try {
        await upsertTournamentMember({
          tournamentId: pathParam(req, "tournamentId"),
          uid: pathParam(req, "uid"),
        });
        await recordActivity({
          action: "member.add", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `user ${pathParam(req, "uid")}`,
        });

        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.delete(
    "/:tournamentId/members/:uid",
    requireAuth,
    requireAdmin,
    async (req, res, next) => {
      try {
        await removeTournamentMember({
          tournamentId: pathParam(req, "tournamentId"),
          uid: pathParam(req, "uid"),
        });
        await recordActivity({
          action: "member.remove", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `user ${pathParam(req, "uid")}`,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/players",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const players = await listTournamentPlayers(pathParam(req, "tournamentId"));
        if (res.locals.reader === "PLAYER") {
          // Players see who plays and where, never the accounts or emails of other players.
          res.status(200).json({
            players: players.map((player) => ({ ...player, accountUid: null, hasAccount: false, email: null, hasEmail: false })),
          });
          return;
        }
        // Only admins read emails. Editors see whether a slot has an account.
        const isAdmin = hasAdminClaim(res.locals.auth);
        res.status(200).json({ players: players.map((player) => (isAdmin ? player : { ...player, email: null })) });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/teams",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const teams = await listTournamentTeams(pathParam(req, "tournamentId"));
        res.status(200).json({ teams });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/teams/:teamId",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const teamId = Number(pathParam(req, "teamId"));
        const body = (req.body != null && typeof req.body === "object")
          ? req.body as Record<string, unknown>
          : null;
        const name = String(body?.name ?? "").trim();
        const rawEmaIds = Array.isArray(body?.emaIds) ? body.emaIds : null;
        if (!Number.isInteger(teamId) || teamId <= 0) {
          throw badRequest("teamId must be a positive integer");
        }
        if (name.length === 0 || name.length > 80) {
          throw badRequest("Team name must contain 1 to 80 characters");
        }
        if (rawEmaIds == null || rawEmaIds.length > 4) {
          throw badRequest("emaIds must contain up to four team slots");
        }
        const emaIds = rawEmaIds.map((value, index) => {
          if (value == null || String(value).trim().length === 0) return null;
          try {
            return validateEmaId(value);
          } catch (_error) {
            throw badRequest("Invalid EMA player", { index, value });
          }
        });
        const assignedEmaIds = emaIds.filter((emaId): emaId is string => emaId != null);
        if (new Set(assignedEmaIds).size !== assignedEmaIds.length) {
          throw badRequest("A player cannot occupy two team slots");
        }
        const existence = await Promise.all(assignedEmaIds.map((emaId) => playerExists(emaId)));
        const missingIndex = existence.findIndex((exists) => !exists);
        if (missingIndex >= 0) {
          throw badRequest("EMA player does not exist", { emaId: assignedEmaIds[missingIndex] });
        }

        await updateTournamentTeam({
          tournamentId: pathParam(req, "tournamentId"),
          teamId,
          name,
          emaIds,
        });
        await recordActivity({
          action: "team.update", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `team ${teamId}`, details: { name, emaIds },
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/rounds",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const rounds = await listTournamentRounds(pathParam(req, "tournamentId"));
        res.status(200).json({ rounds });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const roundIdParam = req.query.roundId;
        const roundId = roundIdParam == null ? null : Number(roundIdParam);
        if (roundId != null && (!Number.isFinite(roundId) || !Number.isInteger(roundId) || roundId <= 0)) {
          throw badRequest("roundId must be a positive integer");
        }

        const tables = await listTournamentTables(pathParam(req, "tournamentId"), roundId);
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables-with-hands",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const tables = await getAllTablesWithHands({ tournamentId: pathParam(req, "tournamentId") });
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/rounds/:roundId/tables-with-hands",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        if (!Number.isInteger(roundId) || roundId <= 0) {
          throw badRequest("roundId must be a positive integer");
        }

        const tables = await getRoundTablesWithHands({ tournamentId: pathParam(req, "tournamentId"), roundId });
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables/:roundId/:tableId",
    requireAuth,
    requireTournamentReader,
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        const tableId = Number(pathParam(req, "tableId"));
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        const data = await getTableWithHands({
          tournamentId: pathParam(req, "tournamentId"),
          roundId,
          tableId,
        });
        res.status(200).json(data);
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/tables/:roundId/:tableId/state",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const request = parseSaveTableRequest(req.params, req.body);
        await assertTournamentOpenForEditor(pathParam(req, "tournamentId"), res.locals.auth);
        res.status(200).json(await saveTableState({
          tournamentId: pathParam(req, "tournamentId"),
          ...request,
          actor: actorFromToken(res.locals.auth),
        }));
      } catch (error) {
        next(error);
      }
    },
  );

  router.post(
    "/:tournamentId/tables/:roundId/:tableId/reset",
    requireAuth,
    requireAdmin,
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        const tableId = Number(pathParam(req, "tableId"));
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        await resetTable({
          tournamentId: pathParam(req, "tournamentId"),
          roundId,
          tableId,
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  // One action for one player or for many. The server invites the players with no account and resets the others.
  router.post(
    "/:tournamentId/player-accounts/send",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const raw = Array.isArray(req.body?.players) ? req.body.players as unknown[] : [];
        const items = raw.map((entry) => {
          const playerId = Number((entry as { playerId?: unknown }).playerId);
          if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
          return { playerId };
        });
        const results = await runAccountAction({ tournamentId: pathParam(req, "tournamentId"), items });
        await recordActivity({
          action: "account.send", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `${items.length} players`, details: { playerIds: items.map((item) => item.playerId) },
        });
        res.status(200).json({ results });
      } catch (e) {
        next(e);
      }
    },
  );

  /** Reads the file of the request. The apply step reads it again, so the server never trusts rows from the client. */
  const readImportFile = (body: unknown): { fileName: string; buffer: Buffer } => {
    const value = (body != null && typeof body === "object") ? body as Record<string, unknown> : {};
    const dataBase64 = String(value.dataBase64 ?? "").trim();
    if (!dataBase64) throw badRequest("The file is required");
    return { fileName: String(value.fileName ?? "players.csv"), buffer: Buffer.from(dataBase64, "base64") };
  };

  router.get(
    "/:tournamentId/player-import/export",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const file = await exportPlayers(pathParam(req, "tournamentId"), { includeEmails: hasAdminClaim(res.locals.auth) });
        res.status(200).json({ fileName: file.fileName, dataBase64: file.buffer.toString("base64") });
      } catch (e) {
        next(e);
      }
    },
  );

  router.post(
    "/:tournamentId/player-import/preview",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const file = readImportFile(req.body);
        res.status(200).json(await previewPlayerImport({ tournamentId: pathParam(req, "tournamentId"), ...file }));
      } catch (e) {
        next(e);
      }
    },
  );

  router.post(
    "/:tournamentId/player-import/apply",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const file = readImportFile(req.body);
        const uid = (res.locals.auth as { uid: string }).uid;
        const result = await applyPlayerImport({ tournamentId: pathParam(req, "tournamentId"), ...file, uid });
        await recordActivity({
          action: "player.import", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `${result.players} players`, details: { ...result },
        });
        res.status(200).json(result);
      } catch (e) {
        next(e);
      }
    },
  );

  // Placeholder: future endpoints for players/teams/tables/hands will live here.
  router.get(
    "/:tournamentId",
    requireAuth,
    requireTournamentEditor,
    async (req, res) => {
      res.status(501).json({
        error: "not_implemented",
        message: "Tournament details endpoint not implemented yet",
        tournamentId: pathParam(req, "tournamentId"),
      });
    },
  );

  return router;
}
