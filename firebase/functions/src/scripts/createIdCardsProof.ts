import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { mkdir } from "node:fs/promises";

import { buildIdCardsPdf } from "../services/idCardsService";

async function main(): Promise<void> {
  const outputPath = resolve(process.argv[2] ?? "../../../output/pdf/id-cards-proof.pdf");
  const logoPath = process.argv[3]?.trim();
  const variant = process.argv[4]?.trim() ?? "standard";
  const numberOfRounds = variant === "12-rounds" ? 12 : 7;
  const associationLogo = logoPath ? await readFile(resolve(logoPath)) : null;
  const pdf = await buildIdCardsPdf({
    tournamentShortName: "6th MMC",
    year: "2026",
    primaryColor: "#02B16B",
    associationLogo,
    numberOfRounds,
    players: [
      {
        playerId: 21,
        name: "Ernesto Vega de la Iglesia",
        country: "Europe",
        countryCode: "EUR",
        teamName: "Mahjong Madrid",
        tableNumbers: Array.from({ length: numberOfRounds }, (_, index) => ((index * 7) % 23) + 1),
      },
    ],
  });
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, pdf);
  process.stdout.write(`${outputPath}\n`);
}

void main();
