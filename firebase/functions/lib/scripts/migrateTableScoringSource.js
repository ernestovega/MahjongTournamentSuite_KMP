"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.convertForcedPointsTable = convertForcedPointsTable;
const promises_1 = require("node:fs/promises");
const node_path_1 = require("node:path");
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const SCORE_FIELDS = [
    "east", "south", "west", "north",
];
function text(value) {
    return String(value ?? "").trim();
}
function pointsForScores(scores) {
    if (scores.some((score) => score.length === 0))
        return null;
    const ranked = scores.map((score, index) => ({ index, score: Number(score) }));
    if (ranked.some((item) => !Number.isSafeInteger(item.score)))
        return null;
    const sorted = [...ranked].sort((left, right) => right.score - left.score);
    const values = (() => {
        const a = sorted.map((item) => item.score);
        if (a[0] === a[1] && a[1] === a[2] && a[2] === a[3])
            return ["1,75", "1,75", "1,75", "1,75"];
        if (a[0] === a[1] && a[1] === a[2])
            return ["2,33", "2,33", "2,33", "0"];
        if (a[1] === a[2] && a[2] === a[3])
            return ["4", "1", "1", "1"];
        if (a[0] === a[1] && a[2] === a[3])
            return ["3", "3", "0,5", "0,5"];
        if (a[0] === a[1])
            return ["3", "3", "1", "0"];
        if (a[1] === a[2])
            return ["4", "1,5", "1,5", "0"];
        if (a[2] === a[3])
            return ["4", "2", "0,5", "0,5"];
        return ["4", "2", "1", "0"];
    })();
    const bySeat = Array(4);
    sorted.forEach((item, index) => { bySeat[item.index] = values[index]; });
    return bySeat;
}
/** Converts one legacy Manual Points table without changing any hand document. */
function convertForcedPointsTable(data) {
    if (Boolean(data.usePointsCalculation ?? true))
        return { patch: null };
    const scores = SCORE_FIELDS.map((seat) => {
        const manual = text(data[`manualPlayer${seat[0].toUpperCase()}${seat.slice(1)}Score`]);
        return manual || text(data[`player${seat[0].toUpperCase()}${seat.slice(1)}Score`]);
    });
    const points = pointsForScores(scores);
    if (points == null) {
        return {
            patch: null,
            reason: "The table has no four integer scores. Manual Points were not changed.",
        };
    }
    const patch = {
        useTotalsOnly: true,
        usePointsCalculation: true,
    };
    SCORE_FIELDS.forEach((seat, index) => {
        const capitalized = `${seat[0].toUpperCase()}${seat.slice(1)}`;
        patch[`manualPlayer${capitalized}Score`] = scores[index];
        patch[`player${capitalized}Score`] = scores[index];
        patch[`manualPlayer${capitalized}Points`] = points[index];
        patch[`player${capitalized}Points`] = points[index];
    });
    return { patch };
}
function argumentValue(name) {
    const prefix = `--${name}=`;
    const inline = process.argv.find((argument) => argument.startsWith(prefix));
    if (inline)
        return inline.slice(prefix.length).trim();
    const index = process.argv.indexOf(`--${name}`);
    const value = index >= 0 ? process.argv[index + 1] : undefined;
    return value && !value.startsWith("--") ? value.trim() : null;
}
async function main() {
    const shouldApply = process.argv.includes("--apply");
    const project = argumentValue("project") ?? firebase_1.firebaseProjectId;
    if (shouldApply && argumentValue("confirm-project") !== project) {
        throw new Error(`Apply mode requires --confirm-project=${project}`);
    }
    const tournaments = await firebase_1.db.collection("tournaments").get();
    const entries = [];
    for (const tournament of tournaments.docs) {
        const tables = await tournament.ref.collection("tables").get();
        for (const table of tables.docs) {
            const before = table.data();
            if (Boolean(before.usePointsCalculation ?? true))
                continue;
            const converted = convertForcedPointsTable(before);
            const hands = (await table.ref.collection("hands").get()).docs.map((hand) => hand.data());
            entries.push({
                tournamentPath: tournament.ref.path,
                path: table.ref.path,
                roundId: Number(before.roundId),
                tableId: Number(before.tableId),
                action: converted.patch ? "convert" : "skip",
                reason: converted.reason,
                before,
                patch: converted.patch,
                hands,
            });
        }
    }
    const auditDirectory = (0, node_path_1.resolve)(process.cwd(), "migration-audits");
    await (0, promises_1.mkdir)(auditDirectory, { recursive: true });
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const auditPath = (0, node_path_1.resolve)(auditDirectory, `table-scoring-${project}-${timestamp}.json`);
    await (0, promises_1.writeFile)(auditPath, `${JSON.stringify({ project, createdAt: new Date().toISOString(), entries }, null, 2)}\n`, {
        encoding: "utf8",
        flag: "wx",
        mode: 0o600,
    });
    if (shouldApply) {
        let convertedCount = 0;
        const touchedTournaments = new Set();
        for (const entry of entries) {
            if (!entry.patch)
                continue;
            await firebase_1.db.doc(entry.path).update({
                ...entry.patch,
                hasValidManualTotals: true,
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
                version: firestore_1.FieldValue.increment(1),
            });
            convertedCount++;
            touchedTournaments.add(entry.tournamentPath);
        }
        for (const tournamentPath of touchedTournaments) {
            await firebase_1.db.doc(tournamentPath).update({
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
                "dataVersions.tables.revision": firestore_1.FieldValue.increment(1),
                "dataVersions.tables.changedAt": firestore_1.FieldValue.serverTimestamp(),
            });
        }
        console.log(`Applied ${convertedCount} table conversions. Audit: ${auditPath}`);
    }
    else {
        console.log(`Report only. Found ${entries.length} forced points tables. Audit: ${auditPath}`);
        console.log("Run with --apply --confirm-project=<project> to write the listed table patches.");
    }
}
if (process.argv[1]?.endsWith("migrateTableScoringSource.js")) {
    main().catch((error) => {
        console.error("Table scoring migration failed.", error);
        process.exitCode = 1;
    });
}
//# sourceMappingURL=migrateTableScoringSource.js.map