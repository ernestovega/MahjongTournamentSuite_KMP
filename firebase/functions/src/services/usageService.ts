import { auth, db } from "../firebase";
import { getGlobalUserRole } from "../models/globalRole";

type Data = Record<string, unknown>;

const DAY = 86_400_000;

/** Periods that the report accepts, in days. */
export const USAGE_PERIODS = [7, 30, 90] as const;
export const DEFAULT_USAGE_PERIOD = 30;

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

export type UsageActivity = {
  action: string;
  uid: string | null;
  name: string | null;
  tournamentId: string | null;
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
  hasAccount: boolean;
  /** True when the account of the slot has a password, so the person can sign in. */
  hasPassword: boolean;
  isAssigned: boolean;
};

export type UsageTournamentInfo = {
  id: string;
  name: string;
  country: string;
  startDate: string;
  endDate: string;
  timeZone: string;
  createdAt: number | null;
  players: number;
  isTeams: boolean;
  hasMatchTimer: boolean;
  hasAgenda: boolean;
  hasVenueMap: boolean;
  hasLogo: boolean;
};

export type UsageAccount = {
  uid: string;
  name: string | null;
  role: string;
  disabled: boolean;
  createdAt: number | null;
  lastSeenAt: number | null;
};

export type UserStatus = "ACTIVE" | "VISITOR" | "DORMANT" | "NEVER" | "DISABLED";

export type UserUsage = {
  uid: string;
  name: string | null;
  /** The highest role of the account: ADMIN, EDITOR or PLAYER. */
  role: string;
  /**
   * ACTIVE did work in the period. VISITOR signed in and did no work. DORMANT was seen before the period.
   * NEVER did not sign in. DISABLED cannot sign in.
   */
  status: UserStatus;
  saves: number;
  handChanges: number;
  /** Changes that are not table saves, like settings, players and invitations. */
  actions: number;
  tournaments: number;
  activeDays: number;
  lastActiveDaysAgo: number | null;
  lastSeenDaysAgo: number | null;
  createdDaysAgo: number | null;
};

export type PeriodSummary = {
  activeUsers: number;
  saves: number;
  handChanges: number;
  playerSaves: number;
  editorSaves: number;
  actions: number;
  activeTournaments: number;
  tournamentsCreated: number;
  newAccounts: number;
  conflicts: number;
  resets: number;
};

export type AccountsSummary = {
  total: number;
  admins: number;
  editors: number;
  players: number;
  disabled: number;
  active: number;
  visitors: number;
  dormant: number;
  neverSignedIn: number;
};

export type DailyUsage = { date: string; saves: number; actions: number; activeUsers: number };

export type FeatureUsage = {
  key: string;
  label: string;
  group: string;
  /** PERIOD counts uses in the period. ALL counts tournaments or tables over all time. */
  scope: "PERIOD" | "ALL";
  uses: number;
  /** Different users in the period. Null when the scope is ALL. */
  users: number | null;
  /** Share from 0 to 1 of the tournaments or tables that use the feature. Null when it does not apply. */
  adoption: number | null;
  lastUsedDaysAgo: number | null;
};

export type FunnelStep = { key: string; label: string; count: number };

export type CountryUsage = { country: string; tournaments: number; players: number; saves: number };

export type TournamentStatus = "UPCOMING" | "LIVE" | "DONE";

export type TournamentUsage = {
  tournamentId: string;
  name: string;
  country: string;
  startDate: string;
  endDate: string;
  status: TournamentStatus;
  tables: number;
  completedTables: number;
  handsTables: number;
  totalsOnlyTables: number;
  manualTotalsTables: number;
  saves: number;
  savesInPeriod: number;
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
  lastActivityDaysAgo: number | null;
  slots: number;
  assignedSlots: number;
  slotsWithAccount: number;
  slotsWithPassword: number;
};

export type UsageReport = {
  generatedAt: string;
  periodDays: number;
  current: PeriodSummary;
  previous: PeriodSummary;
  accounts: AccountsSummary;
  daily: DailyUsage[];
  /** Table saves by day of the week (0 is Monday) and hour of the day, in the time zone of the tournament. */
  heatmap: number[][];
  users: UserUsage[];
  features: FeatureUsage[];
  funnel: FunnelStep[];
  countries: CountryUsage[];
  tournaments: TournamentUsage[];
};

const text = (value: unknown): string => String(value ?? "").trim();

const round1 = (value: number): number => Math.round(value * 10) / 10;

const dayKey = (at: number): string => new Date(at).toISOString().slice(0, 10);

const daysAgo = (now: number, at: number | null): number | null => (at == null ? null : Math.max(0, Math.floor((now - at) / DAY)));

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const formatters = new Map<string, Intl.DateTimeFormat>();

/** The weekday (0 is Monday) and the hour of a moment in a time zone. An unknown zone falls back to UTC. */
export function localSlot(at: number, timeZone: string): { weekday: number; hour: number } {
  let formatter = formatters.get(timeZone);
  if (formatter == null) {
    try {
      formatter = new Intl.DateTimeFormat("en-GB", { timeZone, weekday: "short", hour: "2-digit", hourCycle: "h23" });
    } catch {
      formatter = new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", weekday: "short", hour: "2-digit", hourCycle: "h23" });
    }
    formatters.set(timeZone, formatter);
  }
  const parts = formatter.formatToParts(new Date(at));
  const weekday = WEEKDAYS.indexOf(parts.find((part) => part.type === "weekday")?.value ?? "");
  const hour = Number(parts.find((part) => part.type === "hour")?.value ?? 0);
  return { weekday: weekday < 0 ? 0 : weekday, hour: Number.isFinite(hour) ? hour % 24 : 0 };
}

/** Activity actions that make one feature of the app. */
const ACTION_FEATURES: Array<{ key: string; label: string; group: string; actions: string[] }> = [
  { key: "invitations", label: "Player invitations", group: "Player accounts", actions: ["account.send", "account.resend"] },
  { key: "emails", label: "Player e-mails saved", group: "Player accounts", actions: ["account.saveEmails"] },
  { key: "revoke", label: "Invitations revoked", group: "Player accounts", actions: ["account.revoke"] },
  { key: "assign", label: "EMA numbers assigned", group: "Players", actions: ["player.assign", "player.import"] },
  { key: "substitution", label: "Player substitution", group: "Players", actions: ["player.substitute", "player.clearSubstitution"] },
  { key: "teamEditing", label: "Team editing", group: "Players", actions: ["team.update"] },
  { key: "registry", label: "EMA player registry", group: "Players", actions: ["registry.create", "registry.update", "registry.photo"] },
  { key: "settings", label: "Tournament settings changes", group: "Tournament setup", actions: ["tournament.update", "tournament.rename"] },
  { key: "editors", label: "Tournament editors", group: "Administration", actions: ["member.add", "member.remove"] },
  { key: "accounts", label: "Account management", group: "Administration", actions: ["user.create", "user.update", "user.disable", "user.enable", "user.photo", "user.photo.remove"] },
];

const WORK_ACTIONS = new Set(ACTION_FEATURES.flatMap((feature) => feature.actions).concat(["tournament.create", "tournament.delete"]));

/** Builds the report from plain records. It does no reads, so tests can call it. */
export function buildUsageReport(params: {
  now: Date;
  days: number;
  tournaments: UsageTournamentInfo[];
  changes: UsageChange[];
  activities: UsageActivity[];
  tables: UsageTable[];
  slots: UsageSlot[];
  accounts: UsageAccount[];
}): UsageReport {
  const now = params.now.getTime();
  const days = params.days;
  const start = now - days * DAY;
  const previousStart = start - days * DAY;
  const inPeriod = (at: number | null): boolean => at != null && at >= start && at <= now;
  const inPrevious = (at: number | null): boolean => at != null && at >= previousStart && at < start;
  const today = params.now.toISOString().slice(0, 10);

  const info = new Map(params.tournaments.map((tournament) => [tournament.id, tournament]));
  const emptySummary = (): PeriodSummary => ({
    activeUsers: 0, saves: 0, handChanges: 0, playerSaves: 0, editorSaves: 0, actions: 0, activeTournaments: 0,
    tournamentsCreated: 0, newAccounts: 0, conflicts: 0, resets: 0,
  });
  const summaries = { current: emptySummary(), previous: emptySummary() };
  const activeUserSets = { current: new Set<string>(), previous: new Set<string>() };
  const activeTournamentSets = { current: new Set<string>(), previous: new Set<string>() };
  const periodOf = (at: number | null): "current" | "previous" | null => (inPeriod(at) ? "current" : inPrevious(at) ? "previous" : null);

  // Tournaments and accounts that started in each period.
  for (const tournament of params.tournaments) {
    const period = periodOf(tournament.createdAt);
    if (period != null) summaries[period].tournamentsCreated += 1;
  }
  for (const account of params.accounts) {
    const period = periodOf(account.createdAt);
    if (period != null) summaries[period].newAccounts += 1;
  }

  type UserState = {
    uid: string; name: string | null; saves: number; handChanges: number; actions: number;
    tournaments: Set<string>; days: Set<string>; lastActiveAt: number | null;
  };
  const userStates = new Map<string, UserState>();
  const stateOf = (uid: string, name: string | null): UserState => {
    let state = userStates.get(uid);
    if (state == null) {
      state = { uid, name, saves: 0, handChanges: 0, actions: 0, tournaments: new Set(), days: new Set(), lastActiveAt: null };
      userStates.set(uid, state);
    }
    state.name = name ?? state.name;
    return state;
  };
  const touchUser = (state: UserState, at: number | null) => {
    if (at != null) state.lastActiveAt = Math.max(state.lastActiveAt ?? at, at);
  };

  const daily = new Map<string, { saves: number; actions: number; users: Set<string> }>();
  const dailyOf = (at: number) => {
    const key = dayKey(at);
    let item = daily.get(key);
    if (item == null) {
      item = { saves: 0, actions: 0, users: new Set() };
      daily.set(key, item);
    }
    return item;
  };
  const heatmap = Array.from({ length: 7 }, () => new Array<number>(24).fill(0));

  const perTournament = new Map<string, TournamentUsage>();
  const touched = new Map<string, Set<string>>();
  const firstAt = new Map<string, number>();
  const lastAt = new Map<string, number>();
  const usageOf = (id: string): TournamentUsage => {
    let item = perTournament.get(id);
    if (item == null) {
      const known = info.get(id);
      item = {
        tournamentId: id, name: known?.name ?? id, country: known?.country ?? "", startDate: known?.startDate ?? "",
        endDate: known?.endDate ?? "", status: "DONE", tables: 0, completedTables: 0, handsTables: 0, totalsOnlyTables: 0,
        manualTotalsTables: 0, saves: 0, savesInPeriod: 0, playerSaves: 0, editorSaves: 0, resets: 0, conflicts: 0,
        tablesTouched: 0, savesPerTouchedTable: null, activeHours: null, lastActivityDaysAgo: null, slots: 0,
        assignedSlots: 0, slotsWithAccount: 0, slotsWithPassword: 0,
      };
      perTournament.set(id, item);
    }
    return item;
  };
  params.tournaments.forEach((tournament) => usageOf(tournament.id));

  let tablesWithData = 0;
  let handsTables = 0;
  let totalsOnlyTables = 0;
  let manualTotalsTables = 0;
  for (const table of params.tables) {
    const item = usageOf(table.tournamentId);
    item.tables += 1;
    if (table.isCompleted) item.completedTables += 1;
    if (table.useTotalsOnly) item.totalsOnlyTables += 1;
    else if (table.hasProgress || table.isCompleted) item.handsTables += 1;
    if (table.hasValidManualTotals) item.manualTotalsTables += 1;
    if (table.hasProgress || table.isCompleted) tablesWithData += 1;
    if (table.useTotalsOnly) totalsOnlyTables += 1;
    else if (table.hasProgress || table.isCompleted) handsTables += 1;
    if (table.hasValidManualTotals) manualTotalsTables += 1;
  }
  for (const slot of params.slots) {
    const item = usageOf(slot.tournamentId);
    item.slots += 1;
    if (slot.isAssigned) item.assignedSlots += 1;
    if (slot.hasAccount) item.slotsWithAccount += 1;
    if (slot.hasPassword) item.slotsWithPassword += 1;
  }

  const lastActivityOf = new Map<string, number>();
  const bumpActivity = (tournamentId: string, at: number | null) => {
    if (at != null) lastActivityOf.set(tournamentId, Math.max(lastActivityOf.get(tournamentId) ?? at, at));
  };
  let playerSavesInPeriod = 0;
  let resetsInPeriod = 0;
  let lastPlayerSaveAt: number | null = null;
  let lastResetAt: number | null = null;

  for (const change of params.changes) {
    const item = usageOf(change.tournamentId);
    const period = periodOf(change.at);
    // The server closes tables by itself when the time of the match is over. That is not work of a user.
    if (change.source === "system") continue;
    if (change.kind === "conflict") {
      item.conflicts += 1;
      if (period != null) summaries[period].conflicts += 1;
    } else if (change.kind === "reset") {
      item.resets += 1;
      if (period != null) summaries[period].resets += 1;
      if (period === "current") resetsInPeriod += 1;
      if (change.at != null) lastResetAt = Math.max(lastResetAt ?? change.at, change.at);
    } else {
      item.saves += 1;
      const source = change.source ?? "editor";
      if (source === "player") item.playerSaves += 1;
      else item.editorSaves += 1;
      const key = `${change.roundId}_${change.tableId}`;
      const set = touched.get(change.tournamentId) ?? new Set<string>();
      set.add(key);
      touched.set(change.tournamentId, set);
      if (change.at != null) {
        firstAt.set(change.tournamentId, Math.min(firstAt.get(change.tournamentId) ?? change.at, change.at));
        lastAt.set(change.tournamentId, Math.max(lastAt.get(change.tournamentId) ?? change.at, change.at));
      }
      if (source === "player") {
        if (period === "current") playerSavesInPeriod += 1;
        if (change.at != null) lastPlayerSaveAt = Math.max(lastPlayerSaveAt ?? change.at, change.at);
      }
      if (period != null) {
        const summary = summaries[period];
        summary.saves += 1;
        summary.handChanges += change.handChangeCount;
        if (source === "player") summary.playerSaves += 1;
        else summary.editorSaves += 1;
        activeTournamentSets[period].add(change.tournamentId);
      }
      if (period === "current" && change.at != null) {
        item.savesInPeriod += 1;
        dailyOf(change.at).saves += 1;
        const slot = localSlot(change.at, info.get(change.tournamentId)?.timeZone || "UTC");
        heatmap[slot.weekday][slot.hour] += 1;
      }
    }
    bumpActivity(change.tournamentId, change.at);
    if (change.uid != null) {
      const state = stateOf(change.uid, change.name);
      touchUser(state, change.at);
      if (period != null) activeUserSets[period].add(change.uid);
      if (period === "current" && change.at != null) {
        dailyOf(change.at).users.add(change.uid);
        state.days.add(dayKey(change.at));
        state.tournaments.add(change.tournamentId);
        if (change.kind !== "conflict") {
          state.saves += 1;
          state.handChanges += change.handChangeCount;
        }
      }
    }
  }

  const actionUses = new Map<string, { uses: number; users: Set<string>; lastAt: number | null }>();
  const actionToFeature = new Map<string, string>();
  ACTION_FEATURES.forEach((feature) => {
    actionUses.set(feature.key, { uses: 0, users: new Set(), lastAt: null });
    feature.actions.forEach((action) => actionToFeature.set(action, feature.key));
  });
  for (const activity of params.activities) {
    const period = periodOf(activity.at);
    if (activity.tournamentId != null) bumpActivity(activity.tournamentId, activity.at);
    const featureKey = actionToFeature.get(activity.action);
    if (featureKey != null) {
      const use = actionUses.get(featureKey)!;
      if (activity.at != null) use.lastAt = Math.max(use.lastAt ?? activity.at, activity.at);
      if (period === "current") {
        use.uses += 1;
        if (activity.uid != null) use.users.add(activity.uid);
      }
    }
    if (!WORK_ACTIONS.has(activity.action)) continue;
    if (period != null) {
      summaries[period].actions += 1;
      if (activity.uid != null) activeUserSets[period].add(activity.uid);
      if (activity.tournamentId != null) activeTournamentSets[period].add(activity.tournamentId);
    }
    if (activity.uid != null) {
      const state = stateOf(activity.uid, activity.name);
      touchUser(state, activity.at);
      if (period === "current" && activity.at != null) {
        state.actions += 1;
        state.days.add(dayKey(activity.at));
        if (activity.tournamentId != null) state.tournaments.add(activity.tournamentId);
        dailyOf(activity.at).actions += 1;
        dailyOf(activity.at).users.add(activity.uid);
      }
    }
  }
  for (const period of ["current", "previous"] as const) {
    summaries[period].activeUsers = activeUserSets[period].size;
    summaries[period].activeTournaments = activeTournamentSets[period].size;
  }

  // Users: every account, plus people who left traces and have no account any more.
  const users: UserUsage[] = [];
  const seen = new Set<string>();
  const statusOf = (disabled: boolean, state: UserState | undefined, lastSeenAt: number | null): UserStatus => {
    if (disabled) return "DISABLED";
    if (state != null && (state.saves > 0 || state.actions > 0)) return "ACTIVE";
    if (lastSeenAt == null && state?.lastActiveAt == null) return "NEVER";
    const lastSign = Math.max(lastSeenAt ?? 0, state?.lastActiveAt ?? 0);
    return lastSign >= start ? "VISITOR" : "DORMANT";
  };
  const accounts: AccountsSummary = {
    total: 0, admins: 0, editors: 0, players: 0, disabled: 0, active: 0, visitors: 0, dormant: 0, neverSignedIn: 0,
  };
  for (const account of params.accounts) {
    seen.add(account.uid);
    const state = userStates.get(account.uid);
    const status = statusOf(account.disabled, state, account.lastSeenAt);
    accounts.total += 1;
    if (account.role === "ADMIN") accounts.admins += 1;
    else if (account.role === "EDITOR") accounts.editors += 1;
    else accounts.players += 1;
    if (status === "DISABLED") accounts.disabled += 1;
    else if (status === "ACTIVE") accounts.active += 1;
    else if (status === "VISITOR") accounts.visitors += 1;
    else if (status === "DORMANT") accounts.dormant += 1;
    else accounts.neverSignedIn += 1;
    const lastActive = state?.lastActiveAt ?? null;
    users.push({
      uid: account.uid,
      name: account.name ?? state?.name ?? null,
      role: account.role,
      status,
      saves: state?.saves ?? 0,
      handChanges: state?.handChanges ?? 0,
      actions: state?.actions ?? 0,
      tournaments: state?.tournaments.size ?? 0,
      activeDays: state?.days.size ?? 0,
      lastActiveDaysAgo: daysAgo(now, lastActive),
      lastSeenDaysAgo: daysAgo(now, Math.max(account.lastSeenAt ?? 0, lastActive ?? 0) || null),
      createdDaysAgo: daysAgo(now, account.createdAt),
    });
  }
  for (const state of userStates.values()) {
    if (seen.has(state.uid)) continue;
    users.push({
      uid: state.uid, name: state.name, role: "PLAYER", status: statusOf(false, state, null), saves: state.saves,
      handChanges: state.handChanges, actions: state.actions, tournaments: state.tournaments.size,
      activeDays: state.days.size, lastActiveDaysAgo: daysAgo(now, state.lastActiveAt),
      lastSeenDaysAgo: daysAgo(now, state.lastActiveAt), createdDaysAgo: null,
    });
  }
  const statusRank: Record<UserStatus, number> = { ACTIVE: 0, VISITOR: 1, DORMANT: 2, NEVER: 3, DISABLED: 4 };
  users.sort((a, b) =>
    statusRank[a.status] - statusRank[b.status]
    || (b.saves + b.actions) - (a.saves + a.actions)
    || (a.lastSeenDaysAgo ?? 9999) - (b.lastSeenDaysAgo ?? 9999));

  // Tournaments.
  const tournaments = [...perTournament.values()].map((item): TournamentUsage => {
    const touchedCount = touched.get(item.tournamentId)?.size ?? 0;
    const first = firstAt.get(item.tournamentId);
    const last = lastAt.get(item.tournamentId);
    const known = info.get(item.tournamentId);
    const status: TournamentStatus = known == null
      ? "DONE"
      : known.startDate > today ? "UPCOMING" : known.endDate !== "" && known.endDate < today ? "DONE" : "LIVE";
    return {
      ...item,
      status,
      tablesTouched: touchedCount,
      savesPerTouchedTable: touchedCount > 0 ? round1(item.saves / touchedCount) : null,
      activeHours: first != null && last != null ? round1((last - first) / 3_600_000) : null,
      lastActivityDaysAgo: daysAgo(now, lastActivityOf.get(item.tournamentId) ?? null),
    };
  }).sort((a, b) => b.savesInPeriod - a.savesInPeriod || b.saves - a.saves);

  // Features.
  const features: FeatureUsage[] = [];
  const total = params.tournaments.length;
  const setup = (key: string, label: string, test: (tournament: UsageTournamentInfo) => boolean) => {
    const using = params.tournaments.filter(test);
    const newest = using.reduce<number | null>((best, tournament) =>
      tournament.createdAt == null ? best : Math.max(best ?? tournament.createdAt, tournament.createdAt), null);
    features.push({
      key, label, group: "Tournament setup", scope: "ALL", uses: using.length, users: null,
      adoption: total > 0 ? using.length / total : null, lastUsedDaysAgo: daysAgo(now, newest),
    });
  };
  setup("teams", "Team tournaments", (tournament) => tournament.isTeams);
  setup("matchTimer", "Match timer", (tournament) => tournament.hasMatchTimer);
  setup("agenda", "Agenda", (tournament) => tournament.hasAgenda);
  setup("venueMap", "Venue map link", (tournament) => tournament.hasVenueMap);
  setup("logo", "Association logo", (tournament) => tournament.hasLogo);
  const byTables = (key: string, label: string, uses: number) => features.push({
    key, label, group: "Data entry", scope: "ALL", uses, users: null,
    adoption: tablesWithData > 0 ? uses / tablesWithData : null, lastUsedDaysAgo: null,
  });
  byTables("handEntry", "Hand-by-hand entry", handsTables);
  byTables("totalsOnly", "Totals-only entry", totalsOnlyTables);
  byTables("manualTotals", "Manual totals", manualTotalsTables);
  features.push({
    key: "playerApp", label: "Entry from the player app", group: "Data entry", scope: "PERIOD", uses: playerSavesInPeriod,
    users: [...userStates.values()].filter((state) => state.saves > 0 && users.find((user) => user.uid === state.uid)?.role === "PLAYER").length,
    adoption: null, lastUsedDaysAgo: daysAgo(now, lastPlayerSaveAt),
  });
  features.push({
    key: "resets", label: "Table resets", group: "Data entry", scope: "PERIOD", uses: resetsInPeriod, users: null,
    adoption: null, lastUsedDaysAgo: daysAgo(now, lastResetAt),
  });
  for (const feature of ACTION_FEATURES) {
    const use = actionUses.get(feature.key)!;
    features.push({
      key: feature.key, label: feature.label, group: feature.group, scope: "PERIOD", uses: use.uses,
      users: use.users.size, adoption: null, lastUsedDaysAgo: daysAgo(now, use.lastAt),
    });
  }

  // Funnel over all tournaments.
  const rows = tournaments.filter((item) => info.has(item.tournamentId));
  const funnel: FunnelStep[] = [
    { key: "created", label: "Tournaments created", count: rows.length },
    { key: "assigned", label: "With players assigned", count: rows.filter((item) => item.assignedSlots > 0).length },
    { key: "accounts", label: "With player accounts", count: rows.filter((item) => item.slotsWithAccount > 0).length },
    { key: "data", label: "With match data", count: rows.filter((item) => item.saves > 0 || item.tablesTouched > 0).length },
    { key: "completed", label: "Fully completed", count: rows.filter((item) => item.tables > 0 && item.completedTables === item.tables).length },
  ];

  // Countries.
  const countryMap = new Map<string, CountryUsage>();
  for (const tournament of params.tournaments) {
    const code = tournament.country || "–";
    const item = countryMap.get(code) ?? { country: code, tournaments: 0, players: 0, saves: 0 };
    item.tournaments += 1;
    item.players += tournament.players;
    item.saves += perTournament.get(tournament.id)?.saves ?? 0;
    countryMap.set(code, item);
  }
  const countries = [...countryMap.values()].sort((a, b) => b.tournaments - a.tournaments || b.saves - a.saves);

  const series: DailyUsage[] = [];
  for (let offset = days - 1; offset >= 0; offset--) {
    const date = dayKey(now - offset * DAY);
    const item = daily.get(date);
    series.push({ date, saves: item?.saves ?? 0, actions: item?.actions ?? 0, activeUsers: item?.users.size ?? 0 });
  }

  return {
    generatedAt: params.now.toISOString(),
    periodDays: days,
    current: summaries.current,
    previous: summaries.previous,
    accounts,
    daily: series,
    heatmap,
    users,
    features,
    funnel,
    countries,
    tournaments,
  };
}

function millis(value: unknown): number | null {
  const stamp = value as { toMillis?: () => number } | null | undefined;
  return typeof stamp?.toMillis === "function" ? stamp.toMillis() : null;
}

function parseMillis(value: string | undefined): number | null {
  if (value == null || value === "") return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

/** Reads the logs, the tournaments and the accounts and builds the usage report for the last [days] days. */
export async function loadUsageReport(days = DEFAULT_USAGE_PERIOD, now = new Date()): Promise<UsageReport> {
  const [tournaments, changes, activities, tables, slots, emaIdIndex, profiles] = await Promise.all([
    db.collection("tournaments").select(
      "name", "hostCountry", "eventStartDate", "eventEndDate", "timeZone", "createdAt", "numPlayers", "isTeams",
      "matchDurationMinutes", "agendaItems", "venueMapUrl", "associationLogoUrl",
    ).get(),
    db.collectionGroup("changes").select("kind", "source", "uid", "name", "roundId", "tableId", "handChangeCount", "at").get(),
    db.collection("activity").select("action", "uid", "name", "tournamentId", "at").get(),
    db.collectionGroup("tables").select("isCompleted", "useTotalsOnly", "hasValidManualTotals", "hasProgress").get(),
    db.collectionGroup("players").select("assignedEmaId").get(),
    db.collection("emaIdIndex").select("uid").get(),
    db.collection("users").select("alias").get(),
  ]);
  const uidByEmaId = new Map(emaIdIndex.docs.map((doc) => [doc.id, text(doc.get("uid"))]));
  const aliasByUid = new Map(profiles.docs.map((doc) => [doc.id, text(doc.get("alias"))]));
  const accounts: UsageAccount[] = [];
  const withPasswordUids = new Set<string>();
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => {
      if (user.providerData.some((provider) => provider.providerId === "password")) withPasswordUids.add(user.uid);
      accounts.push({
      uid: user.uid,
      name: aliasByUid.get(user.uid) || text(user.displayName) || text(user.email) || null,
      role: getGlobalUserRole(user),
      disabled: user.disabled,
      createdAt: parseMillis(user.metadata.creationTime),
      lastSeenAt: parseMillis(user.metadata.lastRefreshTime ?? undefined) ?? parseMillis(user.metadata.lastSignInTime),
      });
    });
    pageToken = page.pageToken;
  } while (pageToken);

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
    days,
    tournaments: tournaments.docs.map((doc) => ({
      id: doc.id,
      name: text(doc.get("name")) || doc.id,
      country: text(doc.get("hostCountry")).toUpperCase(),
      startDate: text(doc.get("eventStartDate")),
      endDate: text(doc.get("eventEndDate")),
      timeZone: text(doc.get("timeZone")) || "UTC",
      createdAt: millis(doc.get("createdAt")),
      players: Number(doc.get("numPlayers") ?? 0),
      isTeams: doc.get("isTeams") === true,
      hasMatchTimer: Number(doc.get("matchDurationMinutes") ?? 0) > 0,
      hasAgenda: Array.isArray(doc.get("agendaItems")) && (doc.get("agendaItems") as unknown[]).length > 0,
      hasVenueMap: text(doc.get("venueMapUrl")) !== "",
      hasLogo: text(doc.get("associationLogoUrl")) !== "",
    })),
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
    activities: activities.docs.map((doc) => ({
      action: text(doc.get("action")),
      uid: text(doc.get("uid")) || null,
      name: text(doc.get("name")) || null,
      tournamentId: text(doc.get("tournamentId")) || null,
      at: millis(doc.get("at")),
    })),
    tables: read(tables.docs, (tournamentId, data) => ({
      tournamentId,
      isCompleted: Boolean(data.isCompleted),
      useTotalsOnly: Boolean(data.useTotalsOnly),
      hasValidManualTotals: Boolean(data.hasValidManualTotals),
      hasProgress: Boolean(data.hasProgress),
    })),
    slots: read(slots.docs, (tournamentId, data) => {
      // The account of a slot is the account that owns the EMA id of the slot.
      const accountUid = uidByEmaId.get(text(data.assignedEmaId)) ?? "";
      return {
        tournamentId,
        hasAccount: accountUid !== "",
        hasPassword: withPasswordUids.has(accountUid),
        isAssigned: text(data.assignedEmaId) !== "",
      };
    }),
    accounts,
  });
}
