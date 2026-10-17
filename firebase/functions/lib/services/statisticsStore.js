"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_STANDINGS_PER_REQUEST = exports.MIN_HANDS_FOR_LEADERBOARD = void 0;
exports.prepareTableStats = prepareTableStats;
exports.refreshTableStats = refreshTableStats;
exports.refreshTournamentStats = refreshTournamentStats;
exports.removeTournamentStats = removeTournamentStats;
exports.loadOverview = loadOverview;
exports.loadPersonStatistics = loadPersonStatistics;
exports.loadStandings = loadStandings;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const handScoring_1 = require("./handScoring");
const statisticsDigest_1 = require("./statisticsDigest");
/** A leaderboard shows a rate only for a player with at least this many hands. Same value as the domain module. */
exports.MIN_HANDS_FOR_LEADERBOARD = 8;
const GLOBAL_SHARDS = 8;
const LEADERBOARD_SIZE = 5;
const tablesCollection = () => firebase_1.db.collection("statsTables");
const playersCollection = () => firebase_1.db.collection("statsPlayers");
const tournamentsCollection = () => firebase_1.db.collection("statsTournaments");
const globalCollection = () => firebase_1.db.collection("statsGlobal");
function tableDocId(tournamentId, roundId, tableId) {
    return `${tournamentId}_${roundId}_${tableId}`;
}
function shardOf(id) {
    let hash = 0;
    for (let index = 0; index < id.length; index += 1)
        hash = (hash * 31 + id.charCodeAt(index)) >>> 0;
    return hash % GLOBAL_SHARDS;
}
const TOTAL_FIELDS = [
    "tables", "points", "score", "place1", "place2", "place3", "place4",
    "tablesWithHands", "handsPlayed", "handsWon", "selfDraws", "dealIns", "penaltyCount",
];
const emptyTotals = () => ({
    tables: 0, points: 0, score: 0, place1: 0, place2: 0, place3: 0, place4: 0,
    tablesWithHands: 0, handsPlayed: 0, handsWon: 0, selfDraws: 0, dealIns: 0, penaltyCount: 0,
});
const round6 = (value) => Math.round(value * 1e6) / 1e6;
function totalsOf(data) {
    const totals = emptyTotals();
    TOTAL_FIELDS.forEach((field) => { totals[field] = Number(data?.[field] ?? 0) || 0; });
    return totals;
}
/** Adds ([sign] 1) or removes ([sign] -1) the result of one table seat. */
function addResult(totals, result, sign) {
    totals.tables += sign;
    totals.points = round6(totals.points + sign * result.points);
    totals.score += sign * result.score;
    const place = `place${Math.min(Math.max(result.position, 1), 4)}`;
    totals[place] += sign;
    if (result.hasHands) {
        totals.tablesWithHands += sign;
        totals.handsPlayed += sign * result.handsPlayed;
        totals.handsWon += sign * result.handsWon;
        totals.selfDraws += sign * result.selfDraws;
        totals.dealIns += sign * result.dealIns;
        totals.penaltyCount += sign * result.penaltyCount;
    }
}
/** The stored player document: the sums and the fields that the leaderboard queries sort by. */
function playerDocument(emaId, totals) {
    const eligible = totals.handsPlayed >= exports.MIN_HANDS_FOR_LEADERBOARD;
    return {
        emaId,
        ...totals,
        // A field that is missing keeps the player out of the query of the leaderboard.
        ...(eligible ? {
            leaderWinRate: totals.handsWon / totals.handsPlayed,
            leaderDealInRate: totals.dealIns / totals.handsPlayed,
        } : {}),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    };
}
function stableJson(value) {
    if (Array.isArray(value))
        return `[${value.map(stableJson).join(",")}]`;
    if (value != null && typeof value === "object") {
        const record = value;
        return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${stableJson(record[key])}`).join(",")}}`;
    }
    return JSON.stringify(value) ?? "null";
}
const intOf = (value) => (/^-?\d+$/.test(value.trim()) ? Number(value.trim()) : null);
/**
 * Reads what the update needs and works out the writes. A transaction must finish all reads before the first write,
 * so the caller runs this before its own writes and then calls `commit`. It returns null when nothing changes.
 * A table that is not complete and has no stored statistics returns null after one read.
 */
async function prepareTableStats(transaction, params) {
    const ref = tablesCollection().doc(tableDocId(params.tournamentId, params.roundId, params.tableId));
    const oldSnapshot = await transaction.get(ref);
    const old = oldSnapshot.exists ? oldSnapshot.data() : null;
    const completed = params.table != null && Boolean(params.table.isCompleted);
    if (!completed && old == null)
        return null;
    let next = null;
    if (completed && params.table != null) {
        const slotIds = ((0, handScoring_1.seatIdsOf)(params.table) ?? []).map(intOf);
        const emaBySlot = new Map();
        if (slotIds.length === 4 && slotIds.every((id) => id != null)) {
            const slotSnapshots = await transaction.getAll(...slotIds.map((id) => firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("players").doc(String(id))));
            slotSnapshots.forEach((snapshot, index) => {
                const ema = snapshot.get("assignedEmaId");
                emaBySlot.set(slotIds[index], typeof ema === "string" ? ema.trim() : "");
            });
        }
        const digest = (0, statisticsDigest_1.buildTableDigest)({ tournamentId: params.tournamentId, table: params.table, hands: params.hands, emaBySlot });
        next = digest.results.length === 4 ? digest : null;
    }
    if (old == null && next == null)
        return null;
    if (old != null && next != null && old.version === statisticsDigest_1.DIGEST_VERSION
        && stableJson(old.results) === stableJson(next.results) && stableJson(old.summary) === stableJson(next.summary)) {
        return null;
    }
    const oldResults = old?.results ?? [];
    const newResults = next?.results ?? [];
    const emaIds = [...new Set([...oldResults, ...newResults].map((result) => result.emaId).filter((ema) => ema !== ""))];
    const playerSnapshots = emaIds.length === 0
        ? []
        : await transaction.getAll(...emaIds.map((ema) => playersCollection().doc(ema)));
    const players = new Map(playerSnapshots.map((snapshot, index) => [emaIds[index], totalsOf(snapshot.data())]));
    oldResults.filter((result) => result.emaId !== "").forEach((result) => addResult(players.get(result.emaId), result, -1));
    newResults.filter((result) => result.emaId !== "").forEach((result) => addResult(players.get(result.emaId), result, 1));
    const slotDelta = new Map();
    const addSlot = (result, sign) => {
        const entry = slotDelta.get(result.slotId) ?? { points: 0, score: 0 };
        entry.points += sign * result.points;
        entry.score += sign * result.score;
        slotDelta.set(result.slotId, entry);
    };
    oldResults.forEach((result) => addSlot(result, -1));
    newResults.forEach((result) => addSlot(result, 1));
    const global = { tablesCompleted: 0, tablesWithHands: 0, handsPlayed: 0, drawnHands: 0, winningHands: 0, selfDraws: 0, winningScoreSum: 0, chickenHands: 0, penaltyCount: 0 };
    const addGlobal = (stored, sign) => {
        if (stored == null)
            return;
        global.tablesCompleted += sign;
        if (!stored.summary.hasHands)
            return;
        global.tablesWithHands += sign;
        global.handsPlayed += sign * stored.summary.handsPlayed;
        global.drawnHands += sign * stored.summary.drawnHands;
        global.winningHands += sign * stored.summary.winningHands;
        // A table stored before this field existed has no value. The backfill stores the table again with the value.
        global.selfDraws += sign * (stored.summary.selfDraws ?? 0);
        global.winningScoreSum += sign * stored.summary.winningScoreSum;
        global.chickenHands += sign * stored.summary.chickenHands;
        global.penaltyCount += sign * stored.results.reduce((sum, result) => sum + result.penaltyCount, 0);
    };
    addGlobal(old, -1);
    addGlobal(next, 1);
    return {
        commit: (tx) => {
            if (next == null) {
                tx.delete(ref);
            }
            else {
                const stored = {
                    tournamentId: params.tournamentId,
                    roundId: params.roundId,
                    tableId: params.tableId,
                    version: statisticsDigest_1.DIGEST_VERSION,
                    emaIds: [...new Set(next.results.map((result) => result.emaId).filter((ema) => ema !== ""))],
                    bestHand: next.summary.bestHand,
                    summary: next.summary,
                    results: next.results,
                };
                tx.set(ref, { ...stored, updatedAt: firestore_1.FieldValue.serverTimestamp() });
            }
            players.forEach((totals, ema) => tx.set(playersCollection().doc(ema), playerDocument(ema, totals)));
            if (slotDelta.size > 0) {
                const totals = {};
                slotDelta.forEach((delta, slotId) => {
                    totals[String(slotId)] = { points: firestore_1.FieldValue.increment(round6(delta.points)), score: firestore_1.FieldValue.increment(delta.score) };
                });
                tx.set(tournamentsCollection().doc(params.tournamentId), { totals }, { merge: true });
            }
            tx.set(globalCollection().doc(`overview-${shardOf(ref.id)}`), {
                tablesCompleted: firestore_1.FieldValue.increment(global.tablesCompleted),
                tablesWithHands: firestore_1.FieldValue.increment(global.tablesWithHands),
                handsPlayed: firestore_1.FieldValue.increment(global.handsPlayed),
                drawnHands: firestore_1.FieldValue.increment(global.drawnHands),
                winningHands: firestore_1.FieldValue.increment(global.winningHands),
                selfDraws: firestore_1.FieldValue.increment(global.selfDraws),
                winningScoreSum: firestore_1.FieldValue.increment(global.winningScoreSum),
                chickenHands: firestore_1.FieldValue.increment(global.chickenHands),
                penaltyCount: firestore_1.FieldValue.increment(global.penaltyCount),
            }, { merge: true });
        },
    };
}
/** Works out the statistics of one table from the stored table and its hands. Used to repair and to backfill. */
async function refreshTableStats(tournamentId, tableDocument) {
    const tableRef = firebase_1.db.collection("tournaments").doc(tournamentId).collection("tables").doc(tableDocument);
    await firebase_1.db.runTransaction(async (transaction) => {
        const [tableSnapshot, handsSnapshot] = await Promise.all([transaction.get(tableRef), transaction.get(tableRef.collection("hands"))]);
        const data = tableSnapshot.exists ? (tableSnapshot.data() ?? {}) : null;
        const [roundId, tableId] = tableDocument.split("_").map(Number);
        const plan = await prepareTableStats(transaction, {
            tournamentId,
            roundId,
            tableId,
            table: data == null ? null : { ...data, roundId, tableId },
            hands: handsSnapshot.docs.map((doc) => doc.data()),
        });
        plan?.commit(transaction);
    });
}
/** Works out the statistics of every table of a tournament, one table at a time. */
async function refreshTournamentStats(tournamentId) {
    const tables = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("tables").select().get();
    for (const table of tables.docs)
        await refreshTableStats(tournamentId, table.id);
    return tables.size;
}
/** Removes the statistics of a tournament and takes its numbers out of the sums. */
async function removeTournamentStats(tournamentId) {
    const stored = await tablesCollection().where("tournamentId", "==", tournamentId).select("roundId", "tableId").get();
    for (const doc of stored.docs) {
        await firebase_1.db.runTransaction(async (transaction) => {
            const plan = await prepareTableStats(transaction, {
                tournamentId,
                roundId: Number(doc.get("roundId")),
                tableId: Number(doc.get("tableId")),
                table: null,
                hands: [],
            });
            plan?.commit(transaction);
        });
    }
    await tournamentsCollection().doc(tournamentId).delete();
}
/** The statistics of all tournaments. It reads a fixed number of documents, whatever the number of tournaments. */
async function loadOverview() {
    const [shards, highest, tournamentCount, playerCount, winRate, dealInRate, selfDraws] = await Promise.all([
        globalCollection().get(),
        tablesCollection().orderBy("bestHand", "desc").limit(1).select("bestHand").get(),
        tournamentsCollection().count().get(),
        playersCollection().count().get(),
        playersCollection().orderBy("leaderWinRate", "desc").limit(LEADERBOARD_SIZE).get(),
        playersCollection().orderBy("leaderDealInRate", "asc").limit(LEADERBOARD_SIZE).get(),
        playersCollection().where("selfDraws", ">", 0).orderBy("selfDraws", "desc").limit(LEADERBOARD_SIZE).get(),
    ]);
    const sum = (field) => shards.docs.reduce((total, doc) => total + (Number(doc.get(field) ?? 0) || 0), 0);
    const players = new Map();
    [winRate, dealInRate, selfDraws].forEach((snapshot) => snapshot.docs.forEach((doc) => {
        players.set(doc.id, { emaId: doc.id, ...totalsOf(doc.data()) });
    }));
    return {
        tournaments: tournamentCount.data().count,
        tablesCompleted: sum("tablesCompleted"),
        tablesWithHands: sum("tablesWithHands"),
        handsPlayed: sum("handsPlayed"),
        drawnHands: sum("drawnHands"),
        winningHands: sum("winningHands"),
        selfDraws: sum("selfDraws"),
        winningScoreSum: sum("winningScoreSum"),
        chickenHands: sum("chickenHands"),
        penaltyCount: sum("penaltyCount"),
        highestHand: Number(highest.docs[0]?.get("bestHand") ?? 0) || 0,
        playerCount: playerCount.data().count,
        players: [...players.values()],
    };
}
/**
 * The results of one player in all tournaments. [allowedTournamentIds] limits the tournaments, or null for all.
 * It reads only the tables of this player. The final places come from [loadStandings], for the tournaments on screen.
 */
async function loadPersonStatistics(emaId, allowedTournamentIds) {
    const snapshot = await tablesCollection().where("emaIds", "array-contains", emaId).get();
    const byTournament = new Map();
    snapshot.docs.forEach((doc) => {
        const stored = doc.data();
        if (allowedTournamentIds != null && !allowedTournamentIds.has(stored.tournamentId))
            return;
        const mine = stored.results.filter((result) => result.emaId === emaId);
        byTournament.set(stored.tournamentId, [...(byTournament.get(stored.tournamentId) ?? []), ...mine]);
    });
    return {
        emaId,
        tournaments: [...byTournament.entries()].map(([tournamentId, results]) => ({
            tournamentId,
            results: results.sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId),
        })),
    };
}
/** The most tournaments that one standings request can ask for. */
exports.MAX_STANDINGS_PER_REQUEST = 25;
/** The final place of one player in each of the given tournaments. A tournament without a result of the player is left out. */
async function loadStandings(emaId, tournamentIds) {
    const ids = [...new Set(tournamentIds)].slice(0, exports.MAX_STANDINGS_PER_REQUEST);
    const standings = await Promise.all(ids.map(async (tournamentId) => {
        const [slots, totalsSnapshot] = await Promise.all([
            firebase_1.db.collection("tournaments").doc(tournamentId).collection("players").where("assignedEmaId", "==", emaId).limit(1).get(),
            tournamentsCollection().doc(tournamentId).get(),
        ]);
        if (slots.empty)
            return null;
        const totals = (totalsSnapshot.get("totals") ?? {});
        const rows = Object.entries(totals).map(([slot, value]) => ({
            slot: Number(slot), points: round6(Number(value.points ?? 0)), score: Number(value.score ?? 0),
        }));
        const mine = rows.find((row) => row.slot === Number(slots.docs[0].id));
        if (mine == null)
            return null;
        const better = rows.filter((row) => row.points > mine.points || (row.points === mine.points && row.score > mine.score)).length;
        return { tournamentId, rank: better + 1, of: rows.length };
    }));
    return standings.filter((standing) => standing != null);
}
//# sourceMappingURL=statisticsStore.js.map