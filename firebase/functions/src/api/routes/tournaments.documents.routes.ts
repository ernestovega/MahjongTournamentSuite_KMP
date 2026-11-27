import { NextFunction, Request, Response, Router } from "express";
import { requireAuth } from "../middleware/requireAuth";
import { requirePermission, requireTournamentPermission } from "../middleware/requirePermission";
import { badRequest } from "../httpError";
import {
  buildIdCardProofPdf,
  generateTournamentIdCards,
  readIdCardStaffNames,
  saveIdCardStaffNames,
  idCardSheetGrid,
  MAX_EXTRA_SHEETS,
  MAX_STAFF_NAME_LENGTH,
  MAX_STAFF_NAMES,
  type IdCardSheetOptions,
} from "../../services/idCardsService";
import { generateTournamentIdList } from "../../services/idListService";
import { listIdCardStaffCandidates } from "../../services/idCardStaffCandidates";
import { generateTableQrCodes, readCardsPerSheet } from "../../services/tableQrService";
import { generateScoreSheets, readScoreSheetsOptions } from "../../services/scoreSheetsService";
import { generateEmaReport } from "../../services/emaReportService";
import { pathParam } from "../pathParam";
import { runTournamentIntegrityCheck } from "../../services/tournamentIntegrityService";

/** Reads the optional A4 sheet layout of the ID cards. No `layout=sheet` value means one card per page. */
function readExtraSheets(value: unknown, kind: string): number {
  const sheets = Number(value ?? 0);
  if (!Number.isInteger(sheets) || sheets < 0 || sheets > MAX_EXTRA_SHEETS) {
    throw badRequest(`Invalid number of ${kind} sheets`, { value });
  }
  return sheets;
}

function readStaffNames(value: unknown): string[] {
  const values = value == null ? [] : Array.isArray(value) ? value : [value];
  if (values.length > MAX_STAFF_NAMES) throw badRequest("Too many staff names", { count: values.length });
  return values.map((name) => {
    if (typeof name !== "string" || name.trim().length > MAX_STAFF_NAME_LENGTH) {
      throw badRequest("Invalid staff name", { value: name });
    }
    return name.trim();
  });
}

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
    backs: query.backs !== "false",
    backSameAsFront: query.backSameAsFront === "true",
    substituteSheets: readExtraSheets(query.substituteSheets, "substitute"),
    staffSheets: readExtraSheets(query.staffSheets, "staff"),
    staffNames: readStaffNames(query.staffName),
  };
  if (idCardSheetGrid(options) == null) throw badRequest("The card size does not fit on an A4 sheet", { scalePercent });
  return options;
}

/** The printed documents of a tournament: ID cards, table QR codes, ID list, EMA report and integrity check. Registered by tournamentsRouter, in its order. */
export function registerDocumentRoutes(router: Router): void {
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

  router.post("/id-card-proof", requireAuth, requirePermission("tools.idCards"), generateIdCardProof);
  // Keep the previous route working for already deployed web bundles.
  router.post("/id-card-preview", requireAuth, requirePermission("tools.idCards"), generateIdCardProof);

  // Reads the whole tournament and reports wrong or odd data. It changes nothing.
  router.get("/:tournamentId/integrity", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      res.setHeader("Cache-Control", "no-store");
      res.status(200).json(await runTournamentIntegrityCheck(pathParam(req, "tournamentId")));
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-cards", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const pdf = await generateTournamentIdCards(pathParam(req, "tournamentId"), readIdCardSheetOptions(req.query));
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-id-cards.pdf"`);
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  });

  // The cards of the tables with the QR code of the tournament, to print: 1, 2 or 4 per sheet (perSheet). See docs/table-qr.md.
  router.get("/:tournamentId/table-qr-codes", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const pdf = await generateTableQrCodes(
        pathParam(req, "tournamentId"),
        readCardsPerSheet(req.query.perSheet),
      );
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-table-qr-codes.pdf"`);
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  });

  // The score sheets to print, on A4: one blank sheet (mode=blank) or one for each table of each round with its round and
  // table (mode=tables); ema=false leaves out the logo of the EMA. See docs/score-sheets.md.
  router.get("/:tournamentId/score-sheets", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const options = readScoreSheetsOptions(req.query);
      const pdf = await generateScoreSheets(pathParam(req, "tournamentId"), options);
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-score-sheets-${options.mode}.pdf"`);
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-cards/staff-names", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      res.status(200).json({ names: await readIdCardStaffNames(pathParam(req, "tournamentId")) });
    } catch (e) {
      next(e);
    }
  });

  // The accounts of the app, by name only, to choose the staff of the ID cards.
  router.get("/:tournamentId/id-cards/staff-candidates", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      res.status(200).json({ candidates: await listIdCardStaffCandidates(pathParam(req, "tournamentId")) });
    } catch (e) {
      next(e);
    }
  });

  // Whoever creates the ID cards keeps the names of the staff for the next time.
  router.put("/:tournamentId/id-cards/staff-names", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const names = readStaffNames(Array.isArray(req.body?.names) ? req.body.names : null);
      res.status(200).json({ names: await saveIdCardStaffNames(pathParam(req, "tournamentId"), names) });
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-list", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const workbook = await generateTournamentIdList(pathParam(req, "tournamentId"));
      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-id-list.xlsx"`);
      res.status(200).send(workbook);
    } catch (e) {
      next(e);
    }
  });

  router.post("/:tournamentId/ema-report", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
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
}
