import { createHash } from "node:crypto";
import { NextFunction, Request, Response, Router } from "express";

import { auth } from "../../firebase";
import { LANGUAGES, parseAcceptLanguage, type Language } from "../../services/i18n";
import {
  addOwnTeamMember,
  cancelOwnRegistration,
  confirmRegistration,
  createOwnTeam,
  leaveOwnTeam,
  ownRegistration,
  register,
  removeOwnTeamMember,
  renameOwnTeam,
  requestWithoutEmaNumber,
  updateOwnRegistration,
} from "../../services/registrationsService";
import { assertSafeDocumentId, unauthenticated } from "../httpError";
import { rateLimit } from "../middleware/rateLimit";
import { pathParam } from "../pathParam";

/**
 * Registration emails that one instance of the API sends in an hour, for all the people together. The opening of the
 * registrations of a big tournament (about 240 people) fits, and a script that sends emails to anybody stops there.
 */
export const REGISTRATION_MAILS_PER_HOUR = 300;

/** The header with the token of the links of the registration emails. */
export const REGISTRATION_TOKEN_HEADER = "x-registration-token";

/**
 * The routes of a person who registers in a tournament. They work with an account (the bearer token) or without one
 * (the token of the links of the emails, in [REGISTRATION_TOKEN_HEADER]). See docs/registrations.md.
 */
export function registrationsRouter(): Router {
  const router = Router();
  // Registering sends an email, so one address registers a few people at most (a family, a club laptop).
  const registerLimit = rateLimit({ windowMs: 15 * 60_000, max: 20 });
  const messageLimit = rateLimit({ windowMs: 15 * 60_000, max: 3 });
  // Each registration and each message sends an email from the mailbox of the club. The limits above count one
  // address, so these also count the person and all the senders together: a flood must not burn the mailbox.
  const personMailLimit = rateLimit({
    windowMs: 60 * 60_000,
    max: 5,
    // The EMA number of the body; an account that registers itself sends none, so its token counts instead.
    key: (req) => {
      const emaId = String((req.body as { emaId?: unknown } | undefined)?.emaId ?? "").trim();
      const account = createHash("sha256").update(req.header("authorization") ?? "").digest("base64url");
      return `${String(req.params.tournamentId ?? "")}|${emaId || `account:${account}`}`;
    },
  });
  const allMailLimit = rateLimit({ windowMs: 60 * 60_000, max: REGISTRATION_MAILS_PER_HOUR, key: () => "all" });

  router.param("tournamentId", (_req, _res, next, value: string) => {
    try {
      assertSafeDocumentId(value, "tournament id");
      next();
    } catch (error) {
      next(error);
    }
  });

  type Caller = { tournamentId: string; uid: string | null; token: string | null; language: Language };
  const handle = (handler: (req: Request, res: Response, caller: Caller) => Promise<void>) =>
    async (req: Request, res: Response, next: NextFunction) => {
      try {
        const header = req.header("authorization") ?? "";
        const bearer = header.match(/^Bearer\s+(.+)$/i)?.[1] ?? null;
        let uid: string | null = null;
        if (bearer != null) {
          try {
            uid = (await auth.verifyIdToken(bearer, true)).uid;
          } catch {
            throw unauthenticated("Invalid or expired token");
          }
        }
        const body = (req.body ?? {}) as Record<string, unknown>;
        const asked = String(body.language ?? "");
        const language = (LANGUAGES as readonly string[]).includes(asked) ? asked as Language : parseAcceptLanguage(req.header("accept-language"));
        const token = req.header(REGISTRATION_TOKEN_HEADER)?.trim() || null;
        await handler(req, res, { tournamentId: pathParam(req, "tournamentId"), uid, token, language });
      } catch (error) {
        next(error);
      }
    };
  const body = (req: Request) => (req.body ?? {}) as Record<string, unknown>;

  router.post("/:tournamentId", registerLimit, personMailLimit, allMailLimit, handle(async (req, res, caller) => {
    const input = body(req);
    res.status(200).json(await register({
      tournamentId: caller.tournamentId,
      uid: caller.uid,
      emaId: input.emaId,
      email: input.email,
      foodRestrictions: input.foodRestrictions,
      consent: input.consent === true,
      language: caller.language,
    }));
  }));

  router.post("/:tournamentId/confirm", registerLimit, handle(async (req, res, caller) => {
    const token = String(body(req).token ?? caller.token ?? "");
    res.status(200).json(await confirmRegistration({ tournamentId: caller.tournamentId, token }));
  }));

  router.get("/:tournamentId/me", handle(async (_req, res, caller) => {
    res.status(200).json(await ownRegistration(caller));
  }));

  router.put("/:tournamentId/me", handle(async (req, res, caller) => {
    const input = body(req);
    res.status(200).json(await updateOwnRegistration({ ...caller, foodRestrictions: input.foodRestrictions, consent: input.consent === true }));
  }));

  // Leaving sends an email to the person and one to the organization.
  router.delete("/:tournamentId/me", allMailLimit, handle(async (_req, res, caller) => {
    await cancelOwnRegistration(caller);
    res.status(200).json({ ok: true });
  }));

  router.post("/:tournamentId/me/team", handle(async (req, res, caller) => {
    res.status(200).json(await createOwnTeam({ ...caller, name: body(req).name }));
  }));

  router.put("/:tournamentId/me/team", handle(async (req, res, caller) => {
    res.status(200).json(await renameOwnTeam({ ...caller, name: body(req).name }));
  }));

  router.delete("/:tournamentId/me/team", handle(async (_req, res, caller) => {
    res.status(200).json(await leaveOwnTeam(caller));
  }));

  // Each member that the captain adds gets an email. Adding and removing the same person again and again must not
  // flood its inbox, so the limit of emails of one person counts here too (the EMA number of the body).
  router.post("/:tournamentId/me/team/members", personMailLimit, allMailLimit, handle(async (req, res, caller) => {
    res.status(200).json(await addOwnTeamMember({ ...caller, emaId: body(req).emaId }));
  }));

  router.delete("/:tournamentId/me/team/members/:emaId", handle(async (req, res, caller) => {
    res.status(200).json(await removeOwnTeamMember({ ...caller, emaId: pathParam(req, "emaId") }));
  }));

  router.post("/:tournamentId/no-ema", messageLimit, allMailLimit, handle(async (req, res, caller) => {
    const input = body(req);
    await requestWithoutEmaNumber({
      tournamentId: caller.tournamentId, name: input.name, country: input.country, email: input.email, message: input.message,
    });
    res.status(200).json({ ok: true });
  }));

  return router;
}
