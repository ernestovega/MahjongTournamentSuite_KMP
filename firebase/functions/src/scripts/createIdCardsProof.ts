import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { mkdir } from "node:fs/promises";

import { buildIdCardsPdf } from "../services/idCardsService";

async function main(): Promise<void> {
  const outputPath = resolve(process.argv[2] ?? "../../../output/pdf/id-cards-proof.pdf");
  const logoPath = process.argv[3]?.trim();
  const associationLogo = logoPath ? await readFile(resolve(logoPath)) : null;
  const pdf = await buildIdCardsPdf({
    tournamentShortName: "6th MMC",
    year: "2026",
    primaryColor: "#7C3AED",
    associationLogo,
    players: [
      {
        playerId: 21,
        name: "ÁLVARO DE LA TORRE",
        country: "Spain",
        teamName: "Madrid Dragons",
        tableNumbers: [11, 4, 21, 19, 7, 12, 3],
      },
    ],
  });
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, pdf);
  process.stdout.write(`${outputPath}\n`);
}

void main();
