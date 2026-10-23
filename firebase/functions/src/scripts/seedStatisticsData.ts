import { readFileSync } from "node:fs";

import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";
import {
  globalVersionUpdate,
  tournamentVersionUpdate,
} from "../services/dataVersionsService";
import {
  handScoreTotals,
  handSeatScores,
  seatIdsOf,
  tablePointsOf,
  type SeatTexts,
} from "../services/handScoring";
import {
  randomTable,
  seededRng,
  shuffled,
  synthesizeHands,
  type Rng,
  type SeatNumbers,
  type SynthHand,
} from "../services/handSynthesis";
import { calculateTableSummary, defaultHand, HANDS_PER_TABLE } from "../services/tableManagerService";

/**
 * Fills the test tournaments and the legacy tournaments with data for the statistics screens.
 *
 * - Test tournaments: tournament info, teams, players of the legacy tournaments, seats and 16 random hands for
 *   every table. Every table ends completed.
 * - Legacy tournaments: tables that have only totals get hands that add up to the same totals. Tables whose
 *   hands do not add up to the totals get the missing hands. When the hands match the totals, the table
 *   leaves the manual totals mode and becomes completed.
 *
 * It previews by default. Add --apply to write. Add --from-dump=<file> to preview from a saved dump.
 * Add --only=<tournamentId> to run one tournament.
 */
const applyChanges = process.argv.includes("--apply");
const dumpPath = process.argv.find((arg) => arg.startsWith("--from-dump="))?.split("=")[1];
const only = process.argv.find((arg) => arg.startsWith("--only="))?.split("=")[1];

const LEGACY_IDS = ["legacy-madrid-2017", "legacy-madrid-2018", "legacy-madrid-2025"];
const TEST_IDS = ["uaQXjPG0doBtjUeAMZXv", "wxgxoxdEPTny0l3sv4DV", "xWA1cM1oUkKssPRVLKr4"];

type Data = Record<string, unknown>;
type TableEntry = { table: Data; hands: Data[] };
type TournamentSnapshot = {
  id: string;
  tournament: Data;
  players: Record<string, Data>;
  teams: Record<string, Data>;
  tables: Record<string, TableEntry>;
};

type Operation = {
  path: string;
  kind: "set" | "update";
  data: Data;
};

const text = (value: unknown): string => String(value ?? "").trim();

// -- Loading ---------------------------------------------------------------------------------

async function loadTournament(id: string): Promise<TournamentSnapshot> {
  const ref = db.collection("tournaments").doc(id);
  const [tournament, players, teams, tables] = await Promise.all([
    ref.get(), ref.collection("players").get(), ref.collection("teams").get(), ref.collection("tables").get(),
  ]);
  const entries = await Promise.all(tables.docs.map(async (table) => {
    const hands = await table.ref.collection("hands").get();
    return [table.id, { table: table.data(), hands: hands.docs.map((hand) => hand.data()) }] as const;
  }));
  return {
    id,
    tournament: tournament.data() ?? {},
    players: Object.fromEntries(players.docs.map((doc) => [doc.id, doc.data()])),
    teams: Object.fromEntries(teams.docs.map((doc) => [doc.id, doc.data()])),
    tables: Object.fromEntries(entries),
  };
}

function loadFromDump(path: string, id: string): TournamentSnapshot {
  const dump = JSON.parse(readFileSync(path, "utf8")) as Record<string, Data>;
  const item = dump[id] as Data;
  return {
    id,
    tournament: item.tournament as Data,
    players: item.players as Record<string, Data>,
    teams: item.teams as Record<string, Data>,
    tables: item.tables as Record<string, TableEntry>,
  };
}

// -- Hands -----------------------------------------------------------------------------------

function penaltySum(value: unknown): number {
  return text(value).split(";").reduce((sum, part) => {
    const trimmed = part.trim();
    return sum + (/^-?\d+$/.test(trimmed) ? Number(trimmed) : 0);
  }, 0);
}

/** Reads a stored hand as a synthesis hand, or null when the hand has no counted result. */
function toSynth(hand: Data, seatIds: SeatTexts): SynthHand | null {
  if (handSeatScores(hand, seatIds) == null) return null;
  const winnerText = text(hand.playerWinnerId);
  const loserText = text(hand.playerLooserId) === "-" ? "" : text(hand.playerLooserId);
  return {
    winner: winnerText === "" ? null : seatIds.indexOf(winnerText),
    loser: loserText === "" ? null : seatIds.indexOf(loserText),
    score: winnerText === "" ? 0 : Number(text(hand.handScore)),
    isChicken: Boolean(hand.isChickenHand),
    penalties: [
      penaltySum(hand.playerEastPenalty), penaltySum(hand.playerSouthPenalty),
      penaltySum(hand.playerWestPenalty), penaltySum(hand.playerNorthPenalty),
    ],
  };
}

function handDocument(handId: number, hand: SynthHand | null, seatIds: SeatTexts): Data {
  if (hand == null) return { ...defaultHand(handId) };
  const penalty = (seat: number) => (hand.penalties[seat] === 0 ? "" : String(hand.penalties[seat]));
  return {
    handId,
    playerWinnerId: hand.winner == null ? "" : seatIds[hand.winner],
    playerLooserId: hand.loser == null ? "" : seatIds[hand.loser],
    handScore: hand.winner == null ? "0" : String(hand.score),
    isChickenHand: hand.isChicken,
    isDone: true,
    playerEastPenalty: penalty(0),
    playerSouthPenalty: penalty(1),
    playerWestPenalty: penalty(2),
    playerNorthPenalty: penalty(3),
  };
}

// -- Tables ----------------------------------------------------------------------------------

type TablePlan = {
  operations: Operation[];
  /** Hand documents of the table after the plan. */
  hands: Data[];
  table: Data;
  kind: "generated" | "matched" | "filled" | "failed" | "skipped";
  note?: string;
  penaltyAmount?: number;
  bestScoresBroken?: boolean;
};

function scoreTexts(values: SeatNumbers | SeatTexts): SeatTexts {
  return values.map(String) as SeatTexts;
}

/**
 * Plans one table. [fill] gives hands for slots that have no counted hand, with the kept hands as input.
 * Returns the table patch and the hand writes. The caller adds the version values.
 */
function finishTable(params: {
  tournamentId: string;
  tableDocId: string;
  entry: TableEntry;
  seatIds: SeatTexts;
  keptHandIds: Set<number>;
  generated: Map<number, SynthHand>;
  target: SeatNumbers | null;
}): TablePlan {
  const { entry, seatIds } = params;
  const tablePath = `tournaments/${params.tournamentId}/tables/${params.tableDocId}`;
  const existing = new Map(entry.hands.map((hand) => [Number(hand.handId), hand]));
  const operations: Operation[] = [];
  const finalHands: Data[] = [];
  for (let handId = 1; handId <= HANDS_PER_TABLE; handId += 1) {
    const current = existing.get(handId);
    let document: Data;
    if (params.keptHandIds.has(handId) && current != null) {
      document = { ...current, isDone: true };
      if (current.isDone !== true) operations.push({ path: `${tablePath}/hands/${handId}`, kind: "set", data: { handId, isDone: true, updatedAt: FieldValue.serverTimestamp() } });
    } else {
      document = handDocument(handId, params.generated.get(handId) ?? null, seatIds);
      operations.push({
        path: `${tablePath}/hands/${handId}`,
        kind: "set",
        data: { ...document, ...(current == null ? { createdAt: FieldValue.serverTimestamp() } : {}), updatedAt: FieldValue.serverTimestamp() },
      });
    }
    finalHands.push(document);
  }
  const base: Data = {
    ...entry.table,
    playerEastId: seatIds[0], playerSouthId: seatIds[1], playerWestId: seatIds[2], playerNorthId: seatIds[3],
    useTotalsOnly: false,
    isCompleted: true,
    manualPlayerEastScore: "", manualPlayerSouthScore: "", manualPlayerWestScore: "", manualPlayerNorthScore: "",
  };
  const totals = handScoreTotals(base, finalHands.map((hand) => ({ ...hand, isDone: true })));
  if (totals == null) throw new Error(`${tablePath}: no hand counts`);
  if (params.target != null && totals.some((value, seat) => value !== params.target![seat])) {
    throw new Error(`${tablePath}: hands give ${totals.join(",")} but the target is ${params.target.join(",")}`);
  }
  const scores = scoreTexts(totals as SeatNumbers);
  const withScores: Data = {
    ...base,
    playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
  };
  const points = tablePointsOf(withScores);
  const merged: Data = { ...withScores, ...points };
  const summary = calculateTableSummary(merged, finalHands);
  const version = Number(entry.table.version ?? 0) + 1;
  const patch: Data = {
    playerEastId: seatIds[0], playerSouthId: seatIds[1], playerWestId: seatIds[2], playerNorthId: seatIds[3],
    playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
    ...points,
    manualPlayerEastScore: "", manualPlayerSouthScore: "", manualPlayerWestScore: "", manualPlayerNorthScore: "",
    useTotalsOnly: false,
    isCompleted: true,
    ...summary,
    version,
  };
  operations.push({
    path: tablePath,
    kind: "update",
    data: { ...patch, updatedAt: FieldValue.serverTimestamp() },
  });
  return { operations, hands: finalHands, table: { ...merged, ...summary, version }, kind: "generated" };
}

function seatTargets(table: Data): SeatNumbers | null {
  const read = (fields: string[]) => {
    const values = fields.map((field) => text(table[field]));
    return values.every((value) => /^-?\d+$/.test(value)) ? values.map(Number) as SeatNumbers : null;
  };
  return read(["manualPlayerEastScore", "manualPlayerSouthScore", "manualPlayerWestScore", "manualPlayerNorthScore"])
    ?? read(["playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore"]);
}

function planLegacyTable(snapshot: TournamentSnapshot, tableDocId: string, rng: Rng): TablePlan {
  const entry = snapshot.tables[tableDocId];
  const seatIds = seatIdsOf(entry.table);
  const target = seatTargets(entry.table);
  const tableLabel = `${snapshot.id}/${tableDocId}`;
  if (seatIds == null || target == null) {
    return { operations: [], hands: entry.hands, table: entry.table, kind: "skipped", note: `${tableLabel}: no seats or no totals` };
  }
  const kept = new Map<number, SynthHand>();
  entry.hands.forEach((hand) => {
    const synth = toSynth(hand, seatIds);
    if (synth != null) kept.set(Number(hand.handId), synth);
  });
  const keptTotals = handScoreTotals(
    { ...entry.table, isCompleted: true },
    entry.hands.filter((hand) => kept.has(Number(hand.handId))).map((hand) => ({ ...hand, isDone: true })),
  );
  if (keptTotals != null && keptTotals.every((value, seat) => value === target[seat])) {
    const plan = finishTable({
      tournamentId: snapshot.id, tableDocId, entry, seatIds,
      keptHandIds: new Set(kept.keys()), generated: new Map(), target,
    });
    return { ...plan, kind: "matched" };
  }
  const freeSlots = Array.from({ length: HANDS_PER_TABLE }, (_, index) => index + 1).filter((id) => !kept.has(id));
  const bestScores = Array.isArray(entry.table.bestHandScores) ? (entry.table.bestHandScores as unknown[]).map(Number) : [];
  const chickenCount = Number(entry.table.chickenHandCount ?? 0) || 0;
  const keptList = [...kept.values()];
  const result = synthesizeHands({
    target,
    kept: keptList,
    slots: freeSlots.length,
    bestScores: kept.size === 0 ? bestScores : [],
    chickenCount: kept.size === 0 ? chickenCount : 0,
    handCount: kept.size === 0 ? 13 + Math.floor(rng() * 4) : kept.size,
    rng,
  });
  if (result == null) {
    return { operations: [], hands: entry.hands, table: entry.table, kind: "failed", note: `${tableLabel}: no hands fit ${target.join(",")} with ${kept.size} kept hands` };
  }
  const order = shuffled(result.hands, rng);
  const generated = new Map<number, SynthHand>();
  order.forEach((hand, index) => generated.set(freeSlots[index], hand));
  // Slots without a new hand are cleared, so a broken half-entered hand does not stay.
  const plan = finishTable({
    tournamentId: snapshot.id, tableDocId, entry, seatIds,
    keptHandIds: new Set(kept.keys()), generated, target,
  });
  return {
    ...plan,
    kind: kept.size === 0 ? "generated" : "filled",
    penaltyAmount: result.penaltyAmount,
    bestScoresBroken: result.bestScoresBroken,
  };
}

function hashText(value: string): number {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/** A skill between 0.55 and 1.8 for a person. The same person has the same skill in every tournament. */
function strengthOf(key: string): number {
  return 0.55 + (hashText(key) % 1000) / 1000 * 1.25;
}

function planTestTable(
  snapshot: TournamentSnapshot,
  tableDocId: string,
  rng: Rng,
): TablePlan {
  const entry = snapshot.tables[tableDocId];
  const playerIds = ((entry.table.playerIds as unknown[] | undefined) ?? []).map(String);
  const currentSeats = seatIdsOf(entry.table);
  const seatIds = (currentSeats ?? shuffled(playerIds, rng)) as SeatTexts;
  if (seatIds.length !== 4) {
    return { operations: [], hands: entry.hands, table: entry.table, kind: "skipped", note: `${snapshot.id}/${tableDocId}: no four players` };
  }
  const strength = seatIds.map((id) => strengthOf(text(snapshot.players[id]?.assignedEmaId) || id)) as SeatNumbers;
  const kept = new Map<number, SynthHand>();
  entry.hands.forEach((hand) => {
    const synth = toSynth(hand, seatIds);
    if (synth != null) kept.set(Number(hand.handId), synth);
  });
  const freeSlots = Array.from({ length: HANDS_PER_TABLE }, (_, index) => index + 1).filter((id) => !kept.has(id));
  const hands = randomTable(strength, rng, freeSlots.length);
  const generated = new Map<number, SynthHand>();
  hands.forEach((hand, index) => generated.set(freeSlots[index], hand));
  const plan = finishTable({
    tournamentId: snapshot.id, tableDocId, entry, seatIds,
    keptHandIds: new Set(kept.keys()), generated, target: null,
  });
  return { ...plan, kind: "generated" };
}

// -- Tournament info, players and teams --------------------------------------------------------

type Venue = { venueName: string; venueAddress: string; venueMapUrl: string };

const VENUE: Venue = {
  venueName: "Eskalerria",
  venueAddress: "Calle Cipriano Sancho 36, Madrid",
  venueMapUrl: "https://www.google.com/maps/search/?api=1&query=Eskalerria%2C%20Calle%20Cipriano%20Sancho%2036%2C%20Madrid",
};

const DAY_TIMES = ["09:00", "11:20", "15:00", "17:20"];

function addDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

/** Rounds are spread over the days, four at most on one day. */
function scheduleFor(numRounds: number, startDate: string): Data[] {
  return Array.from({ length: numRounds }, (_, index) => {
    const day = Math.floor(index / DAY_TIMES.length);
    return { roundId: index + 1, date: addDays(startDate, day), startTime: DAY_TIMES[index % DAY_TIMES.length] };
  });
}

function planTournamentInfo(snapshot: TournamentSnapshot): Data {
  const numRounds = Number(snapshot.tournament.numRounds);
  const days = Math.max(2, Math.ceil(numRounds / DAY_TIMES.length));
  const startDate = text(snapshot.tournament.eventStartDate);
  // The tests ran in the past, so the dates must not lie in the future.
  const start = startDate >= "2026-10-15" || startDate === "" ? "2026-10-02" : startDate;
  const end = addDays(start, days - 1);
  return {
    ...VENUE,
    contactEmail: "test-statistics@example.com",
    hostCountry: "ES",
    hostCity: "Madrid",
    eventStartDate: start,
    eventEndDate: end,
    roundSchedules: scheduleFor(numRounds, start),
    agendaItems: [
      { title: "Registration and breakfast", date: start, startTime: "08:00" },
      { title: "Lunch", date: start, startTime: "13:20" },
      ...(days > 1 ? [{ title: "Lunch", date: addDays(start, 1), startTime: "13:20" }] : []),
      { title: "Awards ceremony", date: end, startTime: "18:00" },
    ],
    countBestHands: true,
    countChickenHands: true,
  };
}

type LegacyPerson = { name: string; country: string; assignedCountry: string };

function legacyPeople(snapshots: TournamentSnapshot[]): Map<string, LegacyPerson> {
  const people = new Map<string, LegacyPerson>();
  snapshots.forEach((snapshot) => Object.values(snapshot.players).forEach((player) => {
    const ema = text(player.assignedEmaId);
    if (ema === "" || people.has(ema)) return;
    people.set(ema, {
      name: text(player.name),
      country: text(player.country),
      assignedCountry: text(player.assignedCountry) || text(player.country),
    });
  }));
  return people;
}

/** Gives the slots of a test tournament as many legacy players as possible. Every EMA number stays unique. */
function planPlayers(snapshot: TournamentSnapshot, people: Map<string, LegacyPerson>, rng: Rng): Operation[] {
  const slots = Object.entries(snapshot.players).sort((a, b) => Number(a[0]) - Number(b[0]));
  const used = new Set<string>();
  const keep = new Set<string>();
  slots.forEach(([id, slot]) => {
    const ema = text(slot.assignedEmaId);
    if (people.has(ema) && !used.has(ema)) {
      used.add(ema);
      keep.add(id);
    }
  });
  const pool = shuffled([...people.keys()].filter((ema) => !used.has(ema)), rng);
  const operations: Operation[] = [];
  slots.forEach(([id, slot]) => {
    if (keep.has(id)) return;
    const next = pool.shift();
    if (next == null) return;
    const person = people.get(next)!;
    operations.push({
      path: `tournaments/${snapshot.id}/players/${id}`,
      kind: "update",
      data: {
        assignedEmaId: next,
        assignedCountry: person.assignedCountry,
        name: person.name,
        country: person.country,
        nonMember: null,
        updatedAt: FieldValue.serverTimestamp(),
      },
    });
    slot.assignedEmaId = next;
    slot.name = person.name;
  });
  return operations;
}

function planTeams(snapshot: TournamentSnapshot): Operation[] {
  if (!snapshot.tournament.isTeams) return [];
  const byTeam = new Map<number, number[]>();
  Object.values(snapshot.players).forEach((slot) => {
    const team = Number(slot.team);
    byTeam.set(team, [...(byTeam.get(team) ?? []), Number(slot.id)]);
  });
  const operations: Operation[] = [];
  [...byTeam.entries()].sort((a, b) => a[0] - b[0]).forEach(([team, playerIds]) => {
    const current = snapshot.teams[String(team)];
    operations.push({
      path: `tournaments/${snapshot.id}/teams/${team}`,
      kind: "set",
      data: {
        id: team,
        name: text(current?.name) || `Team ${team}`,
        playerIds: playerIds.sort((a, b) => a - b),
        ...(current == null ? { createdAt: FieldValue.serverTimestamp() } : {}),
        updatedAt: FieldValue.serverTimestamp(),
      },
    });
  });
  return operations;
}

// -- Run -------------------------------------------------------------------------------------

async function commit(operations: Operation[]): Promise<void> {
  for (let index = 0; index < operations.length; index += 400) {
    const batch = db.batch();
    operations.slice(index, index + 400).forEach((operation) => {
      const ref = db.doc(operation.path);
      if (operation.kind === "set") batch.set(ref, operation.data, { merge: true });
      else batch.update(ref, operation.data);
    });
    // eslint-disable-next-line no-await-in-loop
    await batch.commit();
  }
}

type Summary = { kinds: Record<string, number>; notes: string[]; penalties: string[]; bestBroken: number };

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} statistics seed data for ${firebaseProjectId}.`);
  const ids = [...LEGACY_IDS, ...TEST_IDS].filter((id) => only == null || id === only);
  const snapshots = new Map<string, TournamentSnapshot>();
  const every = [...LEGACY_IDS, ...TEST_IDS];
  for (const id of every) {
    // eslint-disable-next-line no-await-in-loop
    snapshots.set(id, dumpPath != null ? loadFromDump(dumpPath, id) : await loadTournament(id));
  }
  const people = legacyPeople(LEGACY_IDS.map((id) => snapshots.get(id)!));
  const rng = seededRng(20261016);
  let completionChanged = false;

  for (const id of ids) {
    const snapshot = snapshots.get(id)!;
    const isLegacy = LEGACY_IDS.includes(id);
    const operations: Operation[] = [];
    const plans: TablePlan[] = [];
    const summary: Summary = { kinds: {}, notes: [], penalties: [], bestBroken: 0 };
    const tournamentUpdate: Data = {};

    if (!isLegacy) {
      Object.assign(tournamentUpdate, planTournamentInfo(snapshot));
      const playerOps = planPlayers(snapshot, people, seededRng(hashText(id)));
      operations.push(...playerOps, ...planTeams(snapshot));
      if (playerOps.length > 0) Object.assign(tournamentUpdate, tournamentVersionUpdate("players"));
      if (snapshot.tournament.isTeams) Object.assign(tournamentUpdate, tournamentVersionUpdate("teams"));
    }

    const tableIds = Object.keys(snapshot.tables).sort((a, b) => {
      const [ra, ta] = a.split("_").map(Number);
      const [rb, tb] = b.split("_").map(Number);
      return ra - rb || ta - tb;
    });
    for (const tableDocId of tableIds) {
      const plan = isLegacy ? planLegacyTable(snapshot, tableDocId, rng) : planTestTable(snapshot, tableDocId, rng);
      plans.push(plan);
      operations.push(...plan.operations);
      summary.kinds[plan.kind] = (summary.kinds[plan.kind] ?? 0) + 1;
      if (plan.note) summary.notes.push(plan.note);
      if (plan.penaltyAmount) summary.penalties.push(`${tableDocId}: ${plan.penaltyAmount}`);
      if (plan.bestScoresBroken) summary.bestBroken += 1;
    }

    // Tournament counters and versions.
    const changedTables = plans.filter((plan) => plan.kind !== "skipped" && plan.kind !== "failed");
    const finalTables = plans.map((plan, index) => ({ plan, entry: snapshot.tables[tableIds[index]] }));
    const completed = finalTables.every(({ plan, entry }) => (
      plan.kind === "skipped" || plan.kind === "failed" ? Boolean(entry.table.isCompleted) : Boolean(plan.table.isCompleted)
    ));
    const progress = finalTables.filter(({ plan, entry }) => (
      calculateTableSummary(plan.kind === "skipped" || plan.kind === "failed" ? entry.table : plan.table, plan.hands).hasProgress
    )).length;
    const wasComplete = Boolean(snapshot.tournament.isCompleted);
    if (changedTables.length > 0) {
      Object.assign(tournamentUpdate, tournamentVersionUpdate("tables"));
      tableIds.forEach((tableDocId, index) => {
        const plan = plans[index];
        if (plan.kind === "skipped" || plan.kind === "failed") return;
        tournamentUpdate[`dataVersions.tableVersions.${tableDocId}`] = Number(plan.table.version);
      });
    }
    tournamentUpdate.isCompleted = completed;
    tournamentUpdate.tablesWithProgress = progress;
    tournamentUpdate.updatedAt = FieldValue.serverTimestamp();
    if (completed !== wasComplete) completionChanged = true;
    operations.push({ path: `tournaments/${id}`, kind: "update", data: tournamentUpdate });

    console.log(`\n${id} (${text(snapshot.tournament.name)}): ${operations.length} writes`);
    console.log(`  tables: ${JSON.stringify(summary.kinds)}; completed tournament: ${wasComplete} -> ${completed}; tables with progress: ${progress}`);
    if (summary.penalties.length) console.log(`  tables with a penalty to fix the totals (${summary.penalties.length}): ${summary.penalties.join(", ")}`);
    if (summary.bestBroken) console.log(`  tables where the old best hand scores could not be kept: ${summary.bestBroken}`);
    summary.notes.slice(0, 25).forEach((note) => console.log(`  note: ${note}`));
    if (applyChanges) {
      // eslint-disable-next-line no-await-in-loop
      await commit(operations);
      console.log("  written");
    }
  }
  if (applyChanges && completionChanged) {
    await db.collection("_meta").doc("dataVersions").set(globalVersionUpdate("tournaments"), { merge: true });
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
