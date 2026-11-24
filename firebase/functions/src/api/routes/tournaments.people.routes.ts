import { Router } from "express";
import { recordActivity } from "../../services/activityLog";
import { auth } from "../../firebase";
import { requireAuth } from "../middleware/requireAuth";
import { accessOf, requireTournamentPermission } from "../middleware/requirePermission";
import { badRequest } from "../httpError";
import { canInTournament } from "../../services/accessService";
import { listTournamentMembers, removeTournamentMember, upsertTournamentMember } from "../../services/membersService";
import { readSubstitution } from "../../services/playerSubstitution";
import {
  assignTournamentPlayer,
  setPlayerSubstitution,
  listTournamentPlayers,
  listTournamentTeams,
  updateTournamentTeam,
} from "../../services/tournamentContentService";
import { playerExists, validateEmaId } from "../../services/playersService";
import { actorFromToken } from "../../services/changeLog";
import { getUserProfile } from "../../services/usersService";
import { pathParam } from "../pathParam";

/** The people of a tournament: members, players (assignment and substitution) and teams. Registered by tournamentsRouter, in its order. */
export function registerPeopleRoutes(router: Router): void {
  router.get(
    "/:tournamentId/users/lookup",
    requireAuth,
    requireTournamentPermission("tournament.members"),
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
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.edit"),
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
    requireTournamentPermission("tournament.edit"),
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
    requireTournamentPermission("tournament.edit"),
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
    requireTournamentPermission("tournament.members"),
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
    requireTournamentPermission("tournament.members"),
    async (req, res, next) => {
      try {
        const result = await removeTournamentMember({
          tournamentId: pathParam(req, "tournamentId"),
          uid: pathParam(req, "uid"),
        });
        await recordActivity({
          action: "member.remove", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `user ${pathParam(req, "uid")}`,
        });
        res.status(200).json({ ok: true, result });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/players",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        // Editors get the names, so the tables do not need the whole EMA registry.
        const players = await listTournamentPlayers(pathParam(req, "tournamentId"), { withNames: true });
        // Emails go to the accounts that manage the tournament. The others see whether a slot has an account.
        const withEmails = await canInTournament(accessOf(res), "tournament.playerEmails", pathParam(req, "tournamentId"));
        res.status(200).json({ players: players.map((player) => (withEmails ? player : { ...player, email: null })) });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/teams",
    requireAuth,
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.edit"),
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
}
