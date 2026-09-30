import ExcelJS = require("exceljs");
import sharp from "sharp";

import { flagSvgFor, IdCardsDocument, loadIdCardsDocument, MISSING_FLAG_SVG, splitPlayerName } from "./idCardsService";

export const ID_LIST_BASE_HEADERS = [
  "Tournament short name",
  "Year",
  "Player ID",
  "Name",
  "Given name",
  "Surname",
  "Country",
  "Flag",
  "Team name",
] as const;

export async function buildIdList(model: IdCardsDocument): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = "MahjongTournamentSuite";

  const sheet = workbook.addWorksheet("ID list", { views: [{ state: "frozen", ySplit: 1 }] });
  const roundCount = Math.max(
    model.numberOfRounds ?? 0,
    ...model.players.map((player) => player.tableNumbers.length),
  );
  const roundHeaders = Array.from({ length: roundCount }, (_, index) => `Round ${index + 1} table`);
  const headers = [...ID_LIST_BASE_HEADERS, ...roundHeaders];
  sheet.addRow(headers);

  const flagImageIds = new Map<string, number>();
  for (const player of model.players.slice().sort((left, right) => left.playerId - right.playerId)) {
    const { givenName, surname } = splitPlayerName(player.name);
    const row = sheet.addRow([
      model.tournamentShortName,
      model.year,
      player.playerId,
      player.name,
      givenName.toLocaleUpperCase(),
      surname,
      player.country,
      "",
      player.teamName ?? "",
      ...Array.from({ length: roundCount }, (_, index) => {
        const tableNumber = player.tableNumbers[index] ?? 0;
        return tableNumber > 0 ? tableNumber : "-";
      }),
    ]);
    row.height = 24;

    const countryCode = player.countryCode?.trim().toUpperCase() ?? "";
    const flagSvg = flagSvgFor(countryCode);
    const imageKey = flagSvg == null ? "missing" : countryCode;
    let imageId = flagImageIds.get(imageKey);
    if (imageId == null) {
      const png = await sharp(Buffer.from(flagSvg ?? MISSING_FLAG_SVG)).resize(36, 27, { fit: "fill" }).png().toBuffer();
      imageId = workbook.addImage({ buffer: png as unknown as ExcelJS.Buffer, extension: "png" });
      flagImageIds.set(imageKey, imageId);
    }
    sheet.addImage(imageId, {
      tl: { col: 7.15, row: row.number - 0.95 },
      ext: { width: 36, height: 27 },
      editAs: "oneCell",
    });
  }

  const header = sheet.getRow(1);
  header.font = { bold: true, color: { argb: "FFFFFFFF" } };
  header.alignment = { horizontal: "center", vertical: "middle", wrapText: true };
  header.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FF1F4E78" } };
  header.height = 30;
  sheet.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: headers.length } };

  sheet.eachRow((row) => row.eachCell((cell) => {
    cell.border = {
      top: { style: "thin", color: { argb: "FFD9E2F3" } },
      left: { style: "thin", color: { argb: "FFD9E2F3" } },
      bottom: { style: "thin", color: { argb: "FFD9E2F3" } },
      right: { style: "thin", color: { argb: "FFD9E2F3" } },
    };
  }));

  const widths = [24, 10, 12, 28, 20, 22, 22, 8, 24];
  sheet.columns.forEach((column, index) => {
    column.width = widths[index] ?? 15;
  });
  sheet.getColumn(2).numFmt = "@";

  return Buffer.from(await workbook.xlsx.writeBuffer());
}

export async function generateTournamentIdList(tournamentId: string): Promise<Buffer> {
  return buildIdList(await loadIdCardsDocument(tournamentId));
}
