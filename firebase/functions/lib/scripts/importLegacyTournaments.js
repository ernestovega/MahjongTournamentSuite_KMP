"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const node_child_process_1 = require("node:child_process");
const node_fs_1 = require("node:fs");
const node_https_1 = require("node:https");
const projectId = "mahjong-tournament-suite";
const database = "(default)";
const ownerUid = "h0Hgasv0Y1VBOXDVnAoSq1X8RMQ2";
const sourceRoot = "/Users/ernestovega/Downloads/TournamentsSqlScripts";
const completeTournamentIds = [2, 3, 5];
const eventDates = {
    2: { start: "2017-09-23", end: "2017-09-24", shortName: "MCR2017" },
    3: { start: "2018-12-08", end: "2018-12-09", shortName: "MCR2018" },
    4: { start: "2019-11-30", end: "2019-12-01", shortName: "MCR2019" },
    5: { start: "2025-02-01", end: "2025-02-02", shortName: "MCR2025" },
};
const countryCodes = {
    Denmark: "DNK",
    France: "FRA",
    Germany: "DEU",
    Italy: "ITA",
    Netherlands: "NLD",
    Portugal: "PRT",
    Russia: "RUS",
    Spain: "ESP",
    Switzerland: "CHE",
};
function readSql(path) {
    const bytes = (0, node_fs_1.readFileSync)(path);
    return bytes[0] === 0xff && bytes[1] === 0xfe
        ? bytes.toString("utf16le").replace(/^\ufeff/, "")
        : bytes.toString("utf8");
}
function splitSqlList(value) {
    const values = [];
    let start = 0;
    let depth = 0;
    let quoted = false;
    for (let index = 0; index < value.length; index++) {
        const character = value[index];
        if (character === "'") {
            if (quoted && value[index + 1] === "'") {
                index++;
            }
            else {
                quoted = !quoted;
            }
        }
        else if (!quoted && character === "(") {
            depth++;
        }
        else if (!quoted && character === ")") {
            depth--;
        }
        else if (!quoted && depth === 0 && character === ",") {
            values.push(value.slice(start, index).trim());
            start = index + 1;
        }
    }
    values.push(value.slice(start).trim());
    return values;
}
function parseSqlValue(value) {
    const cast = value.match(/^CAST\(N'((?:''|[^'])*)'\s+AS\s+DateTime\)$/i);
    if (cast)
        return cast[1].replace(/''/g, "'").replace("T", " ");
    const text = value.match(/^N?'((?:''|[^'])*)'$/);
    if (text)
        return text[1].replace(/''/g, "'");
    const number = Number(value);
    if (Number.isFinite(number))
        return number;
    throw new Error(`Unsupported SQL value: ${value}`);
}
function parseInsertRows(path, expectedTable) {
    const rows = [];
    for (const line of readSql(path).split(/\r?\n/)) {
        const match = line.match(new RegExp(`^INSERT(?: INTO)? \\[dbo\\]\\.\\[${expectedTable}\\] \\((.*)\\) VALUES \\((.*)\\)$`));
        if (!match)
            continue;
        const columns = splitSqlList(match[1]).map((column) => column.replace(/^\[|\]$/g, ""));
        const values = splitSqlList(match[2]).map(parseSqlValue);
        if (columns.length !== values.length) {
            throw new Error(`${path}: ${columns.length} columns but ${values.length} values`);
        }
        rows.push(Object.fromEntries(columns.map((column, index) => [column, values[index]])));
    }
    if (rows.length === 0)
        throw new Error(`No ${expectedTable} rows found in ${path}`);
    return rows;
}
function stringValue(value) {
    return { stringValue: value };
}
function integerValue(value) {
    return { integerValue: String(value) };
}
function booleanValue(value) {
    return { booleanValue: value };
}
function timestampValue(value) {
    return { timestampValue: value };
}
function nullableStringValue(value) {
    return value == null ? { nullValue: "NULL_VALUE" } : stringValue(value);
}
function fieldMap(data) {
    return data;
}
function tournamentPath(id) {
    return `projects/${projectId}/databases/${database}/documents/tournaments/${id}`;
}
function subcollectionPath(tournamentId, collection) {
    return `${tournamentPath(tournamentId)}/${collection}`;
}
function docPath(tournamentId, collection, documentId) {
    return `${subcollectionPath(tournamentId, collection)}/${documentId}`;
}
function isoNow() {
    return new Date().toISOString();
}
function normalizeEmaId(value) {
    return String(value).trim().padStart(8, "0");
}
function sourceFiles(tournamentId) {
    if (tournamentId === 2) {
        return {
            tables: `${sourceRoot}/Scripts/9thSOMC_Round_6_DBTables.sql`,
            hands: `${sourceRoot}/Scripts/9thSOMC_Round_6_DBHands.sql`,
        };
    }
    if (tournamentId === 3) {
        return {
            tables: `${sourceRoot}/DBScripts Backups/Round7/dbo.DBTables.data.sql`,
            hands: `${sourceRoot}/DBScripts Backups/Round7/dbo.DBHands.data.sql`,
        };
    }
    return {
        tables: `${sourceRoot}/5thMMC2025Scripts/dbo.DBTables.Table.sql`,
        hands: `${sourceRoot}/5thMMC2025Scripts/dbo.DBHands.Table.sql`,
    };
}
function sourceTournamentId(tournamentId) {
    if (tournamentId === 2)
        return 1;
    if (tournamentId === 3)
        return 5;
    return tournamentId;
}
function buildDocuments(selectedTournamentIds) {
    const metadata = parseInsertRows(`${sourceRoot}/5thMMC2025Scripts/dbo.DBTournaments.Table.sql`, "DBTournaments");
    const players = parseInsertRows(`${sourceRoot}/5thMMC2025Scripts/dbo.DBPlayers.Table.sql`, "DBPlayers");
    const teams = parseInsertRows(`${sourceRoot}/5thMMC2025Scripts/dbo.DBTeams.Table.sql`, "DBTeams");
    const documents = [];
    const now = isoNow();
    for (const legacyId of selectedTournamentIds) {
        const tournament = metadata.find((row) => row.TournamentId === legacyId);
        if (!tournament)
            throw new Error(`Missing tournament ${legacyId}`);
        const date = eventDates[legacyId];
        const tournamentId = `legacy-madrid-${date.start.slice(0, 4)}`;
        const tournamentPlayers = players.filter((row) => row.PlayerTournamentId === legacyId);
        const tournamentTeams = teams.filter((row) => row.TeamTournamentId === legacyId);
        const files = sourceFiles(legacyId);
        const sourceId = sourceTournamentId(legacyId);
        const tables = parseInsertRows(files.tables, "DBTables")
            .filter((row) => row.TableTournamentId === sourceId);
        const hands = parseInsertRows(files.hands, "DBHands")
            .filter((row) => row.HandTournamentId === sourceId);
        if (legacyId !== 4 && tournamentPlayers.some((row) => String(row.PlayerEmaNumber ?? "").trim() === "")) {
            throw new Error(`Tournament ${legacyId} contains a player without an EMA number`);
        }
        const playerIds = new Set(tournamentPlayers.map((row) => Number(row.PlayerId)));
        if (tables.some((row) => ![1, 2, 3, 4].every((slot) => playerIds.has(Number(row[`Player${slot}Id`]))))) {
            throw new Error(`Tournament ${legacyId} contains a table with an unknown player`);
        }
        const completed = tables.length > 0 && tables.every((row) => Boolean(row.IsCompleted));
        documents.push({
            name: tournamentPath(tournamentId),
            fields: fieldMap({
                name: stringValue(String(tournament.TournamentName)),
                shortName: stringValue(date.shortName),
                primaryColor: stringValue("#02B16B"),
                associationLogoUrl: nullableStringValue(null),
                hostCountry: stringValue("ESP"),
                hostCity: stringValue("Madrid"),
                mers: integerValue(0),
                isTeams: booleanValue(Boolean(tournament.IsTeams)),
                numPlayers: integerValue(Number(tournament.NumPlayers)),
                numRounds: integerValue(Number(tournament.NumRounds)),
                numTries: integerValue(0),
                eventStartDate: stringValue(date.start),
                eventEndDate: stringValue(date.end),
                isCompleted: booleanValue(completed),
                createdByUid: stringValue(ownerUid),
                createdAt: timestampValue(now),
                updatedAt: timestampValue(now),
            }),
        });
        for (const player of tournamentPlayers) {
            const country = countryCodes[String(player.PlayerCountryName)] ?? "";
            const rawEmaId = String(player.PlayerEmaNumber ?? "").trim();
            const emaId = rawEmaId === "" ? "" : normalizeEmaId(rawEmaId);
            documents.push({
                name: docPath(tournamentId, "players", String(player.PlayerId)),
                fields: fieldMap({
                    id: integerValue(Number(player.PlayerId)),
                    name: stringValue(String(player.PlayerName)),
                    team: integerValue(Number(player.PlayerTeamId)),
                    country: stringValue(country),
                    assignedEmaId: nullableStringValue(emaId || null),
                    assignedCountry: stringValue(country),
                    nonMember: { nullValue: "NULL_VALUE" },
                    createdAt: timestampValue(now),
                    updatedAt: timestampValue(now),
                }),
            });
            if (emaId) {
                documents.push({
                    name: docPath(tournamentId, "emaPlayerAssignments", emaId),
                    fields: fieldMap({
                        playerId: integerValue(Number(player.PlayerId)),
                        updatedAt: timestampValue(now),
                    }),
                });
            }
        }
        for (const team of tournamentTeams) {
            const teamId = Number(team.TeamId);
            documents.push({
                name: docPath(tournamentId, "teams", String(teamId)),
                fields: fieldMap({
                    id: integerValue(teamId),
                    name: stringValue(String(team.TeamName)),
                    playerIds: { arrayValue: { values: tournamentPlayers
                                .filter((player) => Number(player.PlayerTeamId) === teamId)
                                .map((player) => ({ integerValue: String(player.PlayerId) })) } },
                    createdAt: timestampValue(now),
                    updatedAt: timestampValue(now),
                }),
            });
        }
        for (let roundId = 1; roundId <= Number(tournament.NumRounds); roundId++) {
            documents.push({
                name: docPath(tournamentId, "rounds", String(roundId)),
                fields: fieldMap({ roundId: integerValue(roundId), createdAt: timestampValue(now), updatedAt: timestampValue(now) }),
            });
        }
        for (const table of tables) {
            const tableId = Number(table.TableId);
            const roundId = Number(table.TableRoundId);
            const tableDocument = (key) => stringValue(String(table[key] ?? ""));
            documents.push({
                name: docPath(tournamentId, "tables", `${roundId}_${tableId}`),
                fields: fieldMap({
                    roundId: integerValue(roundId),
                    tableId: integerValue(tableId),
                    playerIds: { arrayValue: { values: [1, 2, 3, 4].map((slot) => ({ integerValue: String(table[`Player${slot}Id`]) })) } },
                    playerEastId: tableDocument("PlayerEastId"),
                    playerSouthId: tableDocument("PlayerSouthId"),
                    playerWestId: tableDocument("PlayerWestId"),
                    playerNorthId: tableDocument("PlayerNorthId"),
                    playerEastScore: tableDocument("PlayerEastScore"),
                    playerSouthScore: tableDocument("PlayerSouthScore"),
                    playerWestScore: tableDocument("PlayerWestScore"),
                    playerNorthScore: tableDocument("PlayerNorthScore"),
                    playerEastPoints: tableDocument("PlayerEastPoints"),
                    playerSouthPoints: tableDocument("PlayerSouthPoints"),
                    playerWestPoints: tableDocument("PlayerWestPoints"),
                    playerNorthPoints: tableDocument("PlayerNorthPoints"),
                    manualPlayerEastScore: stringValue(""),
                    manualPlayerSouthScore: stringValue(""),
                    manualPlayerWestScore: stringValue(""),
                    manualPlayerNorthScore: stringValue(""),
                    manualPlayerEastPoints: stringValue(""),
                    manualPlayerSouthPoints: stringValue(""),
                    manualPlayerWestPoints: stringValue(""),
                    manualPlayerNorthPoints: stringValue(""),
                    isCompleted: booleanValue(Boolean(table.IsCompleted)),
                    useTotalsOnly: booleanValue(table.UseTotalsOnly === undefined ? true : Boolean(table.UseTotalsOnly)),
                    usePointsCalculation: booleanValue(true),
                    createdAt: timestampValue(now),
                    updatedAt: timestampValue(now),
                }),
            });
        }
        for (const hand of hands) {
            const handScore = String(hand.HandScore ?? "");
            const winner = String(hand.PlayerWinnerId ?? "");
            const looser = String(hand.PlayerLooserId ?? "");
            const eastPenalty = String(hand.PlayerEastPenalty ?? "");
            const southPenalty = String(hand.PlayerSouthPenalty ?? "");
            const westPenalty = String(hand.PlayerWestPenalty ?? "");
            const northPenalty = String(hand.PlayerNorthPenalty ?? "");
            const isDone = winner !== "" || looser !== "" || [eastPenalty, southPenalty, westPenalty, northPenalty]
                .some((penalty) => penalty !== "") || (handScore !== "" && handScore !== "0");
            documents.push({
                name: `${subcollectionPath(tournamentId, "tables")}/${Number(hand.HandRoundId)}_${Number(hand.HandTableId)}/hands/${String(hand.HandId)}`,
                fields: fieldMap({
                    handId: integerValue(Number(hand.HandId)),
                    playerWinnerId: stringValue(winner),
                    playerLooserId: stringValue(looser),
                    handScore: stringValue(handScore),
                    isChickenHand: booleanValue(Boolean(hand.IsChickenHand)),
                    isDone: booleanValue(isDone),
                    playerEastPenalty: stringValue(eastPenalty),
                    playerSouthPenalty: stringValue(southPenalty),
                    playerWestPenalty: stringValue(westPenalty),
                    playerNorthPenalty: stringValue(northPenalty),
                    createdAt: timestampValue(now),
                    updatedAt: timestampValue(now),
                }),
            });
        }
    }
    return documents;
}
function accessToken() {
    return (0, node_child_process_1.execFileSync)("gcloud", ["auth", "print-access-token"], { encoding: "utf8" }).trim();
}
function firestoreRequest(method, path, token, body) {
    return new Promise((resolve, reject) => {
        const payload = body == null ? undefined : JSON.stringify(body);
        const req = (0, node_https_1.request)({
            hostname: "firestore.googleapis.com",
            path,
            method,
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json",
                ...(payload == null ? {} : { "Content-Length": Buffer.byteLength(payload) }),
            },
        }, (response) => {
            let responseBody = "";
            response.setEncoding("utf8");
            response.on("data", (chunk) => { responseBody += chunk; });
            response.on("end", () => resolve({ status: response.statusCode ?? 0, body: responseBody }));
        });
        req.on("error", reject);
        if (payload != null)
            req.write(payload);
        req.end();
    });
}
async function main() {
    const import2019 = process.argv.includes("--import-2019");
    const selectedTournamentIds = import2019 ? [4] : completeTournamentIds;
    const documents = buildDocuments(selectedTournamentIds);
    const duplicateNames = documents.map((document) => document.name)
        .filter((name, index, names) => names.indexOf(name) !== index);
    if (duplicateNames.length > 0)
        throw new Error(`Duplicate document paths: ${duplicateNames.slice(0, 5).join(", ")}`);
    const repairResults = process.argv.includes("--repair-results");
    const token = accessToken();
    const prefix = `/v1/projects/${projectId}/databases/${encodeURIComponent(database)}/documents`;
    const topLevel = documents.filter((document) => document.name.split("/documents/")[1].split("/").length === 2);
    for (const document of topLevel) {
        const path = `/v1/${document.name}`;
        const existing = await firestoreRequest("GET", path, token);
        if (existing.status === 200 && !repairResults)
            throw new Error(`Refusing to overwrite existing document ${document.name}`);
        if (existing.status !== 404 && !(repairResults && existing.status === 200)) {
            throw new Error(`Cannot inspect ${document.name}: ${existing.status} ${existing.body}`);
        }
    }
    const documentsToWrite = repairResults
        ? documents.filter((document) => /\/tables\//.test(document.name) &&
            (document.name.includes("legacy-madrid-2017") || document.name.includes("legacy-madrid-2018")))
        : documents;
    console.log(`Validated ${documents.length} documents for ${selectedTournamentIds.length} tournaments.`);
    if (process.argv.includes("--dry-run"))
        return;
    for (let index = 0; index < documentsToWrite.length; index += 450) {
        const writes = documentsToWrite.slice(index, index + 450).map((document) => ({
            update: document,
            currentDocument: { exists: false },
        }));
        const result = await firestoreRequest("POST", `${prefix}:commit`, token, { writes });
        if (result.status !== 200)
            throw new Error(`Commit failed: ${result.status} ${result.body}`);
        console.log(`Committed ${Math.min(index + 450, documentsToWrite.length)}/${documentsToWrite.length} documents.`);
    }
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
//# sourceMappingURL=importLegacyTournaments.js.map