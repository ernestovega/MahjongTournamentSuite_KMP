"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const promises_1 = require("node:fs/promises");
const node_path_1 = require("node:path");
const promises_2 = require("node:fs/promises");
const idCardsService_1 = require("../services/idCardsService");
async function main() {
    const outputPath = (0, node_path_1.resolve)(process.argv[2] ?? "../../../output/pdf/id-cards-proof.pdf");
    const logoPath = process.argv[3]?.trim();
    const associationLogo = logoPath ? await (0, promises_1.readFile)((0, node_path_1.resolve)(logoPath)) : null;
    const pdf = await (0, idCardsService_1.buildIdCardsPdf)({
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
    await (0, promises_2.mkdir)((0, node_path_1.dirname)(outputPath), { recursive: true });
    await (0, promises_1.writeFile)(outputPath, pdf);
    process.stdout.write(`${outputPath}\n`);
}
void main();
//# sourceMappingURL=createIdCardsProof.js.map