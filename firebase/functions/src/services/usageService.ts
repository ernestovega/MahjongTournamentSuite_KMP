import { db } from "../firebase";

type Data = Record<string, unknown>;

export type UsageChange = {
  tournamentId: string;
  kind: string;
  source: string | null;
  uid: string | null;
  name: string | null;
  roundId: number;
  tableId: number;
  handChangeCount: number;
  at: number | null;
};

export type UsageTable = {
  tournamentId: string;
  isCompleted: boolean;
  useTotalsOnly: boolean;
  hasValidManualTotals: boolean;
  hasProgress: boolean;
};

export type UsageSlot = {
  tournamentId: string;
  invitationStatus: string;
  hasAccount: boolean;
  isAssigned: boolean;
};

export type UserUsage = { uid: string; name: string | null; source: string; saves: number; handChanges: number };

export type TournamentUsage = {
  tournamentId: string;
  name: string;
  tables: number;
  completedTables: number;
  handsTables: number;
  totalsOnlyTables: number;
  manualTotalsTables: number;
  saves: number;
  playerSaves: number;
  editorSaves: number;
  resets: number;
  conflicts: number;
  /** Tables with at least one save. */
  tablesTouched: number;
  /** Average number of saves for each touched table. A high value points to corrections. */
  savesPerTouchedTable: number | null;
  /** Hours between the first and the last save of the tournament. */
  activeHours: number | null;
  slots: number;
  assignedSlots: number;
  slotsWithAccount: number;
  invitationsAccepted: number;
  invitationsPending: number;
};

export type UsageReport = {
  generatedAt: string;
  totals: {
    tournaments: number;
    saves: number;
    playerSaves: number;
    editorSaves: number;
    resets: number;
    conflicts: number;
    handChanges: number;
    /** Share of the saves that came from players, from 0 to 1. Null when there are no saves. */
    playerShare: number | null;
    /** Conflicts divided by the saves and the conflicts. A high value points to editors and players on the same table. */
    conflictRate: number | null;
  };
  users: UserUsage[];
  tournaments: TournamentUsage[];
  /** Saves by hour of the day, in UTC, from 0 to 23. */
  savesByHour: number[];
};

const text = (value: unknown): string => String(value ?? "").trim();

/** Builds the report from plain records. It does no reads, so tests can call it. */
export function buildUsageReport(params: {
  now: Date;
  tournaments: Array<{ id: string; name: string }>;
  changes: UsageChange[];
  tables: UsageTable[];
  slots: UsageSlot[];
}): UsageReport {
  const users = new Map<string, UserUsage>();
  const savesByHour = new Array<number>(24).fill(0);
  const perTournament = new Map<string, TournamentUsage>();
  const touched = new Map<string, Set<string>>();
  const firstAt = new Map<string, number>();
  const lastAt = new Map<string, number>();

  const usageOf = (id: string, name: string): TournamentUsage => {
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
    if (table.isCompleted) item.completedTables += 1;
    if (table.useTotalsOnly) item.totalsOnlyTables += 1;
    else if (table.hasProgress || table.isCompleted) item.handsTables += 1;
    if (table.hasValidManualTotals) item.manualTotalsTables += 1;
  }
  for (const slot of params.slots) {
    const item = usageOf(slot.tournamentId, slot.tournamentId);
    item.slots += 1;
    if (slot.isAssigned) item.assignedSlots += 1;
    if (slot.hasAccount) item.slotsWithAccount += 1;
    if (slot.invitationStatus === "ACCEPTED") item.invitationsAccepted += 1;
    if (slot.invitationStatus === "INVITED" || slot.invitationStatus === "PENDING") item.invitationsPending += 1;
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
    // The server closes tables by itself when the time of the match is over. That is not work of a user.
    if (change.source === "system") continue;
    item.saves += 1;
    const source = change.source ?? "editor";
    if (source === "player") item.playerSaves += 1;
    else item.editorSaves += 1;
    const key = `${change.roundId}_${change.tableId}`;
    const set = touched.get(change.tournamentId) ?? new Set<string>();
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
      activeHours: first != null && last != null ? Math.round(((last - first) / 3_600_000) * 10) / 10 : null,
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

function millis(value: unknown): number | null {
  const stamp = value as { toMillis?: () => number } | null | undefined;
  return typeof stamp?.toMillis === "function" ? stamp.toMillis() : null;
}

/** Reads the change log, the tables and the player slots of every tournament and builds the usage report. */
export async function loadUsageReport(now = new Date()): Promise<UsageReport> {
  const [tournaments, changes, tables, slots] = await Promise.all([
    db.collection("tournaments").select("name").get(),
    db.collectionGroup("changes").select("kind", "source", "uid", "name", "roundId", "tableId", "handChangeCount", "at").get(),
    db.collectionGroup("tables").select("isCompleted", "useTotalsOnly", "hasValidManualTotals", "hasProgress").get(),
    db.collectionGroup("players").select("invitationStatus", "accountUid", "assignedEmaId", "nonMember").get(),
  ]);
  const tournamentIdOf = (path: string): string | null => {
    const parts = path.split("/");
    return parts[0] === "tournaments" && parts.length >= 4 ? parts[1] : null;
  };
  const read = <T>(
    docs: FirebaseFirestore.QueryDocumentSnapshot[],
    map: (id: string, data: Data) => T,
  ): T[] => docs.flatMap((doc) => {
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
