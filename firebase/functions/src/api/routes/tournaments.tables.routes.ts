import { Router } from "express";
import { db } from "../../firebase";
import { requireAuth } from "../middleware/requireAuth";
import { accessOf, requireTournamentPermission } from "../middleware/requirePermission";
import { badRequest, forbidden } from "../httpError";
import { canInTournament, type Access } from "../../services/accessService";
import { listTournamentRounds, listTournamentTables } from "../../services/tournamentContentService";
import {
  getAllTablesWithHands,
  getRoundTablesWithHands,
  getTableWithHands,
  resetTable,
  saveTableState,
} from "../../services/tableManagerService";
import { actorFromToken } from "../../services/changeLog";
import { completeTables, getRoundClocks, updateRoundClock, updateRoundControl } from "../../services/roundClockService";
import { parseRoundClockAction } from "../../services/roundClock";
import { parseRoundControlChange, parseTablesToComplete } from "../../services/roundControl";
import { parseSaveTableRequest } from "../tableRequests";
import { pathParam } from "../pathParam";
import { setResultsRevealed } from "../../services/resultsVisibility";
import { recordActivity, tournamentNameOf } from "../../services/activityLog";

/**
 * Only a super admin or a club admin of the tournament changes the tables of a completed tournament, so late
 * changes to the results are traceable.
 */
async function assertTournamentOpenForEditor(tournamentId: string, access: Access): Promise<void> {
  if (await canInTournament(access, "tournament.editCompleted", tournamentId)) return;
  const snapshot = await db.collection("tournaments").doc(tournamentId).get();
  if (snapshot.get("isCompleted") === true) {
    throw forbidden("The tournament is completed. Only an admin of the tournament can change its tables");
  }
}

/** The rounds (clock and control) and the tables of a tournament. Registered by tournamentsRouter, in its order. */
export function registerRoundAndTableRoutes(router: Router): void {
  router.get(
    "/:tournamentId/rounds",
    requireAuth,
    requireTournamentPermission("tournament.view"),
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
    "/:tournamentId/round-clocks",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        res.status(200).json(await getRoundClocks(pathParam(req, "tournamentId")));
      } catch (e) {
        next(e);
      }
    },
  );

  // Play, Pause, Resume, Reset and "Set time" of the Timer. The server keeps the clock of each round.
  router.post(
    "/:tournamentId/rounds/:roundId/clock",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        if (!Number.isInteger(roundId) || roundId <= 0) throw badRequest("roundId must be a positive integer");
        const answer = await updateRoundClock({
          tournamentId: pathParam(req, "tournamentId"),
          roundId,
          action: parseRoundClockAction(req.body),
          force: req.body?.force === true,
          closePrevious: req.body?.closePrevious === true,
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json(answer);
      } catch (e) {
        next(e);
      }
    },
  );

  // The plan B of the Timer: an admin or an editor of the tournament opens or closes the hand entry and the seats by hand, and opens closed tables.
  router.post(
    "/:tournamentId/rounds/:roundId/control",
    requireAuth,
    requireTournamentPermission("tournament.roundControl"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        if (!Number.isInteger(roundId) || roundId <= 0) throw badRequest("roundId must be a positive integer");
        const answer = await updateRoundControl({
          tournamentId: pathParam(req, "tournamentId"),
          roundId,
          change: parseRoundControlChange(req.body),
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json(answer);
      } catch (e) {
        next(e);
      }
    },
  );

  // The prize giving: an admin reveals the hidden results, or hides them again after a reveal by mistake.
  router.post(
    "/:tournamentId/results-visibility",
    requireAuth,
    requireTournamentPermission("tournament.roundControl"),
    async (req, res, next) => {
      try {
        const revealed = req.body?.revealed;
        if (typeof revealed !== "boolean") throw badRequest("revealed must be a boolean");
        const tournamentId = pathParam(req, "tournamentId");
        await setResultsRevealed(tournamentId, revealed);
        await recordActivity({
          action: "tournament.update", actor: actorFromToken(res.locals.auth), tournamentId,
          target: await tournamentNameOf(tournamentId), details: { resultsRevealed: revealed },
        });
        res.status(200).json({ resultsRevealed: revealed });
      } catch (e) {
        next(e);
      }
    },
  );

  // The round control closes in one go the tables that stayed open in the finished rounds.
  router.post(
    "/:tournamentId/complete-open-tables",
    requireAuth,
    requireTournamentPermission("tournament.roundControl"),
    async (req, res, next) => {
      try {
        const answer = await completeTables({
          tournamentId: pathParam(req, "tournamentId"),
          tables: parseTablesToComplete(req.body),
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json(answer);
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables",
    requireAuth,
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const request = parseSaveTableRequest(req.params, req.body);
        await assertTournamentOpenForEditor(pathParam(req, "tournamentId"), accessOf(res));
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
    requireTournamentPermission("tournament.resetTable"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        const tableId = Number(pathParam(req, "tableId"));
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        const tournamentId = pathParam(req, "tournamentId");
        await resetTable({
          tournamentId,
          roundId,
          tableId,
          actor: actorFromToken(res.locals.auth),
        });
        // The admin app shows the empty table at once, so the answer carries it like a save does.
        res.status(200).json(await getTableWithHands({ tournamentId, roundId, tableId }));
      } catch (e) {
        next(e);
      }
    },
  );
}
