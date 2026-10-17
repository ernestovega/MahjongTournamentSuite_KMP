"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildUsageReport = buildUsageReport;
exports.loadUsageReport = loadUsageReport;
const firebase_1 = require("../firebase");
const text = (value) => String(value ?? "").trim();
/** Builds the report from plain records. It does no reads, so tests can call it. */
function buildUsageReport(params) {
    const users = new Map();
    const savesByHour = new Array(24).fill(0);
    const perTournament = new Map();
    const touched = new Map();
    const firstAt = new Map();
    const lastAt = new Map();
    const usageOf = (id, name) => {
        let item = perTournament.get(id);
        if (item == null) {
            item = {
                tournamentId: id, name, tables: 0, completedTables: 0, handsTables: 0, totalsOnlyTables: 0,
                manualTotalsTables: 0, saves: 0, playerSaves: 0, editorSaves: 0, resets: 0, conflicts: 0,
                tablesTouched: 0, savesPerTouchedTable: null, activeHours: null, slots: 0, assignedSlots: 0,
                slotsWithAccount: 0, invitationsAccepted: 0, invitationsPending: 0,
            };
            perTournament.set(id, item);
        }
        return item;
    };
    params.tournaments.forEach((tournament) => usageOf(tournament.id, tournament.name));
    for (const table of params.tables) {
        const item = usageOf(table.tournamentId, table.tournamentId);
        item.tables += 1;
        if (table.isCompleted)
            item.completedTables += 1;
        if (table.useTotalsOnly)
            item.totalsOnlyTables += 1;
        else if (table.hasProgress || table.isCompleted)
            item.handsTables += 1;
        if (table.hasValidManualTotals)
            item.manualTotalsTables += 1;
    }
    for (const slot of params.slots) {
        const item = usageOf(slot.tournamentId, slot.tournamentId);
        item.slots += 1;
        if (slot.isAssigned)
            item.assignedSlots += 1;
        if (slot.hasAccount)
            item.slotsWithAccount += 1;
        if (slot.invitationStatus === "ACCEPTED")
            item.invitationsAccepted += 1;
        if (slot.invitationStatus === "INVITED" || slot.invitationStatus === "PENDING")
            item.invitationsPending += 1;
    }
    for (const change of params.changes) {
        const item = usageOf(change.tournamentId, change.tournamentId);
        if (change.kind === "conflict") {
            item.conflicts += 1;
            continue;
        }
        if (change.kind === "reset") {
            item.resets += 1;
            continue;
        }
        item.saves += 1;
        const source = change.source ?? "editor";
        if (source === "player")
            item.playerSaves += 1;
        else
            item.editorSaves += 1;
        const key = `${change.roundId}_${change.tableId}`;
        const set = touched.get(change.tournamentId) ?? new Set();
        set.add(key);
        touched.set(change.tournamentId, set);
        if (change.at != null) {
            savesByHour[new Date(change.at).getUTCHours()] += 1;
            firstAt.set(change.tournamentId, Math.min(firstAt.get(change.tournamentId) ?? change.at, change.at));
            lastAt.set(change.tournamentId, Math.max(lastAt.get(change.tournamentId) ?? change.at, change.at));
        }
        if (change.uid != null) {
            const user = users.get(change.uid) ?? { uid: change.uid, name: change.name, source, saves: 0, handChanges: 0 };
            user.saves += 1;
            user.handChanges += change.handChangeCount;
            user.name = change.name ?? user.name;
            users.set(change.uid, user);
        }
    }
    const tournaments = [...perTournament.values()].map((item) => {
        const touchedCount = touched.get(item.tournamentId)?.size ?? 0;
        const first = firstAt.get(item.tournamentId);
        const last = lastAt.get(item.tournamentId);
        return {
            ...item,
            tablesTouched: touchedCount,
            savesPerTouchedTable: touchedCount > 0 ? Math.round((item.saves / touchedCount) * 10) / 10 : null,
            activeHours: first != null && last != null ? Math.round(((last - first) / 3600000) * 10) / 10 : null,
        };
    }).sort((a, b) => b.saves - a.saves);
    const saves = tournaments.reduce((sum, item) => sum + item.saves, 0);
    const playerSaves = tournaments.reduce((sum, item) => sum + item.playerSaves, 0);
    const conflicts = tournaments.reduce((sum, item) => sum + item.conflicts, 0);
    return {
        generatedAt: params.now.toISOString(),
        totals: {
            tournaments: tournaments.length,
            saves,
            playerSaves,
            editorSaves: saves - playerSaves,
            resets: tournaments.reduce((sum, item) => sum + item.resets, 0),
            conflicts,
            handChanges: [...users.values()].reduce((sum, user) => sum + user.handChanges, 0),
            playerShare: saves > 0 ? playerSaves / saves : null,
            conflictRate: saves + conflicts > 0 ? conflicts / (saves + conflicts) : null,
        },
        users: [...users.values()].sort((a, b) => b.saves - a.saves),
        tournaments,
        savesByHour,
    };
}
function millis(value) {
    const stamp = value;
    return typeof stamp?.toMillis === "function" ? stamp.toMillis() : null;
}
/** Reads the change log, the tables and the player slots of every tournament and builds the usage report. */
async function loadUsageReport(now = new Date()) {
    const [tournaments, changes, tables, slots] = await Promise.all([
        firebase_1.db.collection("tournaments").select("name").get(),
        firebase_1.db.collectionGroup("changes").select("kind", "source", "uid", "name", "roundId", "tableId", "handChangeCount", "at").get(),
        firebase_1.db.collectionGroup("tables").select("isCompleted", "useTotalsOnly", "hasValidManualTotals", "hasProgress").get(),
        firebase_1.db.collectionGroup("players").select("invitationStatus", "accountUid", "assignedEmaId", "nonMember").get(),
    ]);
    const tournamentIdOf = (path) => {
        const parts = path.split("/");
        return parts[0] === "tournaments" && parts.length >= 4 ? parts[1] : null;
    };
    const read = (docs, map) => docs.flatMap((doc) => {
        const id = tournamentIdOf(doc.ref.path);
        return id == null ? [] : [map(id, doc.data())];
    });
    return buildUsageReport({
        now,
        tournaments: tournaments.docs.map((doc) => ({ id: doc.id, name: text(doc.get("name")) || doc.id })),
        changes: read(changes.docs, (tournamentId, data) => ({
            tournamentId,
            kind: text(data.kind) || "save",
            source: text(data.source) || null,
            uid: text(data.uid) || null,
            name: text(data.name) || null,
            roundId: Number(data.roundId ?? 0),
            tableId: Number(data.tableId ?? 0),
            handChangeCount: Number(data.handChangeCount ?? 0),
            at: millis(data.at),
        })),
        tables: read(tables.docs, (tournamentId, data) => ({
            tournamentId,
            isCompleted: Boolean(data.isCompleted),
            useTotalsOnly: Boolean(data.useTotalsOnly),
            hasValidManualTotals: Boolean(data.hasValidManualTotals),
            hasProgress: Boolean(data.hasProgress),
        })),
        slots: read(slots.docs, (tournamentId, data) => ({
            tournamentId,
            invitationStatus: text(data.invitationStatus) || "NONE",
            hasAccount: text(data.accountUid) !== "",
            isAssigned: text(data.assignedEmaId) !== "" || data.nonMember != null,
        })),
    });
}
//# sourceMappingURL=usageService.js.map