import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";
import { globalVersionUpdate } from "../services/dataVersionsService";
import { handScoreTotals, tablePointsOf } from "../services/handScoring";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";
import {
  randomTable,
  seededRng,
  shuffled,
  sumChanges,
  type Rng,
  type SeatNumbers,
  type SynthHand,
} from "../services/handSynthesis";
import { calculateTableSummary, defaultHand, HANDS_PER_TABLE } from "../services/tableManagerService";

/**
 * Creates 10 test tournaments for edge cases and 1 ongoing tournament for the live features.
 *
 * Every tournament has a fixed id that starts with "test-". A new run deletes the old copy of the same id and
 * writes it again. The script never touches other tournaments.
 *
 * It previews by default. Add --apply to write. Add --only=<tournamentId> to run one tournament.
 */
const applyChanges = process.argv.includes("--apply");
const only = process.argv.find((arg) => arg.startsWith("--only="))?.split("=")[1];

const CREATED_BY_UID = "h0Hgasv0Y1VBOXDVnAoSq1X8RMQ2";
const TEST_EDITOR_UID = "RH9PioX5AvNDulLDVa2JX45SP7H3";

type Data = Record<string, unknown>;

// -- Specification ---------------------------------------------------------------------------

type Guest = { firstName: string; lastName: string; country: string };
/** A player slot. "ema" takes a member of the EMA registry, "open" stays unassigned. */
type PlayerSpec = "ema" | "open" | Guest;

type HandStyle =
  | "normal" | "draws" | "allDraws" | "penalties" | "tiePairs" | "tieTop3" | "tieBottom3"
  | "selfDraws" | "oneWinner" | "extreme";

type TableMode =
  | { kind: "empty" }
  | { kind: "seats"; seatCount?: 3 | 4 }
  | { kind: "full"; style?: HandStyle }
  | { kind: "fullOpen"; style?: HandStyle }
  | { kind: "partial"; done: number; extra?: "pendingHand" | "invalidHands" }
  | { kind: "totals"; manual?: boolean; badSum?: boolean }
  | { kind: "totalsInvalid" };

type Venue = { venueName: string; venueAddress: string; venueMapUrl: string };
type Agenda = { title: string; date: string; startTime: string };

type TournamentSpec = {
  id: string;
  name: string;
  shortName: string;
  primaryColor: string;
  startDate: string;
  endDate: string;
  hostCountry: string;
  hostCity: string;
  venue: Venue | null;
  contactEmail: string;
  isTeams: boolean;
  teamNames?: string[];
  numRounds: number;
  players: PlayerSpec[];
  countBestHands: boolean;
  countChickenHands: boolean;
  /** One entry for every round. A null entry keeps the date and the time empty. */
  schedule: Array<{ date: string; startTime: string } | null>;
  agenda: Agenda[];
  mode: (round: number, table: number, tablesPerRound: number) => TableMode;
  members?: string[];
};

const ESKALERRIA: Venue = {
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

/** Rounds are spread over the days. [perDay] rounds happen on one day. */
function spread(numRounds: number, startDate: string, perDay = DAY_TIMES.length): Array<{ date: string; startTime: string }> {
  return Array.from({ length: numRounds }, (_, index) => ({
    date: addDays(startDate, Math.floor(index / perDay)),
    startTime: DAY_TIMES[index % perDay % DAY_TIMES.length],
  }));
}

function standardAgenda(start: string, end: string): Agenda[] {
  return [
    { title: "Registration and breakfast", date: start, startTime: "08:00" },
    { title: "Lunch", date: start, startTime: "13:20" },
    { title: "Awards ceremony", date: end, startTime: "18:00" },
  ];
}

const GENERIC_GUESTS: Guest[] = [
  { firstName: "Anna", lastName: "ROSSI", country: "ITA" },
  { firstName: "Peter", lastName: "MULLER", country: "GER" },
  { firstName: "Sofia", lastName: "ANDERSSON", country: "SWE" },
  { firstName: "Jan", lastName: "KOWALSKI", country: "POL" },
  { firstName: "Maria", lastName: "SILVA", country: "POR" },
  { firstName: "Tom", lastName: "SMITH", country: "GBR" },
  { firstName: "Elena", lastName: "POPESCU", country: "ROU" },
  { firstName: "Luc", lastName: "DUBOIS", country: "FRA" },
  { firstName: "Marta", lastName: "NOVAK", country: "CZE" },
  { firstName: "Ivan", lastName: "HORVAT", country: "AUT" },
];

/** A list of slots where the slots at [guestEvery] steps are guests and the others are EMA members. */
function mixedPlayers(count: number, guestEvery: number): PlayerSpec[] {
  let guest = 0;
  return Array.from({ length: count }, (_, index) => {
    if ((index + 1) % guestEvery !== 0) return "ema" as const;
    const base = GENERIC_GUESTS[guest % GENERIC_GUESTS.length];
    guest += 1;
    return { ...base, lastName: guest > GENERIC_GUESTS.length ? `${base.lastName} ${guest}` : base.lastName };
  });
}

const UNICODE_GUESTS: Guest[] = [
  { firstName: "Ñandú", lastName: "IBÁÑEZ-O'BRIEN", country: "ESP" },
  { firstName: "小明", lastName: "王", country: "FIN" },
  { firstName: "Åsa", lastName: "ÖSTERGÅRD", country: "SWE" },
  { firstName: "Дмитрий", lastName: "ИВАНОВ", country: "RUS" },
  { firstName: "Μαρία", lastName: "ΠΑΠΑΔΟΠΟΥΛΟΥ", country: "SVK" },
  { firstName: "Zoë", lastName: "O'CONNOR-SMITH", country: "IRL" },
  { firstName: "Jean-Baptiste Emmanuel Maximilien", lastName: "DE LA FONTAINE-DUPONT DE MONTMORENCY", country: "FRA" },
  { firstName: "Li", lastName: "NG", country: "NOR" },
  { firstName: "José María", lastName: "GARCÍA", country: "ESP" },
  { firstName: "Łukasz", lastName: "ŻÓŁTOWSKI", country: "POL" },
  { firstName: "Hiroshi", lastName: "TANAKA", country: "DEN" },
  { firstName: "Hiroshi", lastName: "TANAKA", country: "LAT" },
];

function cycle<T>(items: T[], index: number): T {
  return items[index % items.length];
}

const TOURNAMENTS: TournamentSpec[] = [
  {
    id: "test-min-4",
    name: "Test - Minimum 4 players",
    shortName: "TEST MIN4",
    primaryColor: "#02B16B",
    startDate: "2026-06-13", endDate: "2026-06-13",
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-min@example.com",
    isTeams: false, numRounds: 1,
    players: ["ema", "ema", "ema", "ema"],
    countBestHands: true, countChickenHands: true,
    schedule: [{ date: "2026-06-13", startTime: "10:00" }],
    agenda: [{ title: "Only game", date: "2026-06-13", startTime: "10:00" }],
    mode: () => ({ kind: "full" }),
  },
  {
    id: "test-draws-penalties",
    name: "Test - Draws and penalties",
    shortName: "DRAWS-PEN",
    primaryColor: "#E53935",
    startDate: "2026-07-04", endDate: "2026-07-04",
    hostCountry: "ES", hostCity: "Barcelona",
    venue: { venueName: "Casal de Gràcia", venueAddress: "Plaça del Raspall 1, Barcelona", venueMapUrl: "" },
    contactEmail: "test-draws@example.com",
    isTeams: false, numRounds: 3,
    players: mixedPlayers(16, 4),
    countBestHands: true, countChickenHands: true,
    schedule: spread(3, "2026-07-04"),
    agenda: standardAgenda("2026-07-04", "2026-07-04"),
    mode: (round, table) => ({ kind: "full", style: cycle<HandStyle>(["draws", "allDraws", "penalties", "normal"], round + table) }),
  },
  {
    id: "test-ties",
    name: "Test - Ties in tables and ranking",
    shortName: "TEST TIES",
    primaryColor: "#8E24AA",
    startDate: "2026-07-18", endDate: "2026-07-19",
    hostCountry: "ES", hostCity: "Sevilla",
    venue: ESKALERRIA, contactEmail: "test-ties@example.com",
    isTeams: false, numRounds: 4,
    players: mixedPlayers(8, 2),
    countBestHands: true, countChickenHands: true,
    schedule: spread(4, "2026-07-18", 2),
    agenda: standardAgenda("2026-07-18", "2026-07-19"),
    // Round 1 has only tied tables, so the ranking starts fully tied.
    mode: (round, table) => (round === 1
      ? { kind: "full", style: "allDraws" }
      : { kind: "full", style: cycle<HandStyle>(["tiePairs", "tieTop3", "tieBottom3", "normal"], round + table) }),
  },
  {
    id: "test-guests-unicode",
    name: "Test - Guests with special names",
    shortName: "GUESTS",
    primaryColor: "#00897B",
    startDate: "2026-08-01", endDate: "2026-08-02",
    hostCountry: "ES", hostCity: "Valencia",
    venue: ESKALERRIA, contactEmail: "test-guests@example.com",
    isTeams: false, numRounds: 4,
    players: UNICODE_GUESTS,
    countBestHands: true, countChickenHands: true,
    schedule: spread(4, "2026-08-01", 2),
    agenda: standardAgenda("2026-08-01", "2026-08-02"),
    mode: (round, table) => ({ kind: "full", style: cycle<HandStyle>(["normal", "selfDraws"], round + table) }),
  },
  {
    id: "test-teams-partial",
    name: "Test - Teams with missing players",
    shortName: "TEAMS-GAP",
    primaryColor: "#F57C00",
    startDate: "2026-08-22", endDate: "2026-08-23",
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-teams@example.com",
    isTeams: true,
    teamNames: [
      "Los Dragones de Madrid y Alrededores",
      "Equipo B",
      "日本チーム",
      "Ünïcödé Tèam",
      "Team 5",
      "The Last Standing Wind",
    ],
    numRounds: 5,
    // Team 1 is full, team 2 has one open slot, team 3 has two, team 4 has three, team 5 is empty, team 6 has guests.
    players: [
      "ema", "ema", "ema", "ema",
      "ema", "ema", "ema", "open",
      "ema", "ema", "open", "open",
      "open", "open", "open", "ema",
      "open", "open", "open", "open",
      { firstName: "Sara", lastName: "LOPEZ", country: "ESP" }, "ema",
      { firstName: "Mike", lastName: "BROWN", country: "BEL" }, "ema",
    ],
    countBestHands: true, countChickenHands: true,
    schedule: spread(5, "2026-08-22", 3),
    agenda: standardAgenda("2026-08-22", "2026-08-23"),
    mode: (round, table) => (round === 2 && table === 1 ? { kind: "full", style: "oneWinner" } : { kind: "full" }),
  },
  {
    id: "test-totals-only",
    name: "Test - Tables with only totals",
    shortName: "TOTALS",
    primaryColor: "#5D4037",
    startDate: "2026-09-05", endDate: "2026-09-06",
    hostCountry: "ES", hostCity: "Bilbao",
    venue: ESKALERRIA, contactEmail: "test-totals@example.com",
    isTeams: true, numRounds: 4,
    players: mixedPlayers(16, 5),
    countBestHands: true, countChickenHands: true,
    schedule: spread(4, "2026-09-05", 2),
    agenda: standardAgenda("2026-09-05", "2026-09-06"),
    mode: (round, table) => {
      if (round === 1) return { kind: "totals" };
      if (round === 2) {
        return cycle<TableMode>([
          { kind: "totals", manual: true }, { kind: "totals", badSum: true }, { kind: "full" }, { kind: "totals" },
        ], table - 1);
      }
      return { kind: "full" };
    },
  },
  {
    id: "test-no-extras",
    name: "Test - Without best hands, chicken hands, venue or dates",
    shortName: "BARE",
    primaryColor: "#455A64",
    startDate: "2026-09-12", endDate: "2026-09-13",
    hostCountry: "ES", hostCity: "Valencia",
    venue: null, contactEmail: "",
    isTeams: false, numRounds: 3,
    players: mixedPlayers(20, 7),
    countBestHands: false, countChickenHands: false,
    schedule: [null, null, null],
    agenda: [],
    mode: () => ({ kind: "full" }),
  },
  {
    id: "test-incomplete-states",
    name: "Test - Every incomplete table state",
    shortName: "INCOMPL",
    primaryColor: "#C2185B",
    startDate: "2026-09-26", endDate: "2026-09-27",
    hostCountry: "ES", hostCity: "Zaragoza",
    venue: ESKALERRIA, contactEmail: "test-incomplete@example.com",
    isTeams: true, numRounds: 4,
    players: mixedPlayers(16, 4),
    countBestHands: true, countChickenHands: true,
    schedule: spread(4, "2026-09-26", 2),
    agenda: standardAgenda("2026-09-26", "2026-09-27"),
    mode: (round, table) => {
      if (round === 1) return { kind: "full" };
      if (round === 2) {
        return cycle<TableMode>([
          { kind: "fullOpen" }, { kind: "partial", done: 9 }, { kind: "seats" }, { kind: "empty" },
        ], table - 1);
      }
      if (round === 3) {
        return cycle<TableMode>([
          { kind: "totalsInvalid" }, { kind: "partial", done: 6, extra: "invalidHands" },
          { kind: "seats", seatCount: 3 }, { kind: "partial", done: 0, extra: "pendingHand" },
        ], table - 1);
      }
      return { kind: "empty" };
    },
  },
  {
    id: "test-registration-open",
    name: "Test - Registered, not started",
    shortName: "UPCOMING",
    primaryColor: "#1565C0",
    startDate: "2027-03-06", endDate: "2027-03-07",
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-upcoming@example.com",
    isTeams: true, numRounds: 6,
    players: mixedPlayers(24, 6),
    countBestHands: true, countChickenHands: true,
    schedule: spread(6, "2027-03-06", 4),
    agenda: standardAgenda("2027-03-06", "2027-03-07"),
    mode: () => ({ kind: "empty" }),
  },
  {
    id: "test-extremes",
    name: "Test - Extreme scores, penalties and a very long tournament name to check wrapping everywhere",
    shortName: "EXTREMES10",
    primaryColor: "#FF00AA",
    startDate: "2026-05-29", endDate: "2026-05-31",
    hostCountry: "ES", hostCity: "Santiago de Compostela",
    venue: {
      venueName: "Centro Sociocultural Ágora de la Ciudad Vieja",
      venueAddress: "Rúa do Franco 123, 2º piso, 15705 Santiago de Compostela, A Coruña, España",
      venueMapUrl: "https://www.google.com/maps/search/?api=1&query=Santiago%20de%20Compostela",
    },
    contactEmail: "test-extremes@example.com",
    isTeams: false, numRounds: 8,
    players: mixedPlayers(32, 8),
    countBestHands: true, countChickenHands: true,
    schedule: [
      { date: "2026-05-29", startTime: "16:00" }, { date: "2026-05-29", startTime: "18:30" },
      { date: "2026-05-30", startTime: "09:00" }, { date: "2026-05-30", startTime: "11:20" },
      { date: "2026-05-30", startTime: "15:00" }, { date: "2026-05-30", startTime: "17:20" },
      { date: "2026-05-31", startTime: "09:00" }, { date: "2026-05-31", startTime: "11:20" },
    ],
    agenda: [
      { title: "Welcome dinner the day before the first round with a deliberately long agenda title", date: "2026-05-28", startTime: "21:00" },
      { title: "Registration", date: "2026-05-29", startTime: "15:00" },
      { title: "Lunch", date: "2026-05-30", startTime: "13:20" },
      { title: "Lunch", date: "2026-05-31", startTime: "13:20" },
      { title: "Awards ceremony and farewell party", date: "2026-05-31", startTime: "18:00" },
      { title: "Sightseeing the day after the event", date: "2026-06-01", startTime: "10:30" },
    ],
    mode: (round, table) => ({
      kind: "full",
      style: cycle<HandStyle>(["extreme", "oneWinner", "selfDraws", "penalties", "tiePairs", "draws", "extreme", "normal"], round + table),
    }),
  },
  {
    id: "test-live-ongoing",
    name: "Test - Live ongoing tournament",
    shortName: "LIVE",
    primaryColor: "#D81B60",
    startDate: "2026-10-16", endDate: "2026-10-17",
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-live@example.com",
    isTeams: true,
    teamNames: ["Aces", "Bamboo", "Circles", "Dragons", "East Wind", "Flowers", "Green Dragon", "Honors", "Imperial", "Jade"],
    numRounds: 6,
    players: mixedPlayers(40, 4),
    countBestHands: true, countChickenHands: true,
    schedule: spread(6, "2026-10-16", 4),
    agenda: standardAgenda("2026-10-16", "2026-10-17"),
    members: [TEST_EDITOR_UID],
    mode: (round, table) => {
      if (round === 1) return { kind: "full" };
      if (round === 2) return table === 5 ? { kind: "totals" } : { kind: "full" };
      if (round === 3) return { kind: "full" };
      if (round === 4) {
        return cycle<TableMode>([
          { kind: "full" }, { kind: "full" }, { kind: "full" }, { kind: "full" },
          { kind: "partial", done: 11 }, { kind: "partial", done: 4 },
          { kind: "fullOpen" }, { kind: "seats" }, { kind: "empty" }, { kind: "empty" },
        ], table - 1);
      }
      return { kind: "empty" };
    },
  },
];

// -- Text helpers ----------------------------------------------------------------------------

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

function titleCase(value: string): string {
  return value.toLowerCase().replace(/(^|[\s-])(\p{L})/gu, (_match, before: string, letter: string) => before + letter.toUpperCase());
}

// -- Hands -----------------------------------------------------------------------------------

const ZERO: SeatNumbers = [0, 0, 0, 0];
const pickInt = (rng: Rng, min: number, max: number) => min + Math.floor(rng() * (max - min + 1));

const draw = (penalties: SeatNumbers = ZERO): SynthHand => ({ winner: null, loser: null, score: 0, isChicken: false, penalties });

function win(rng: Rng, score: number, options: { self?: boolean; chicken?: boolean } = {}): SynthHand {
  const winner = pickInt(rng, 0, 3);
  let loser: number | null = null;
  if (!options.self) {
    loser = pickInt(rng, 0, 2);
    if (loser >= winner) loser += 1;
  }
  return { winner, loser, score, isChicken: options.chicken ?? false, penalties: ZERO };
}

function selfDrawBy(winner: number, score: number): SynthHand {
  return { winner, loser: null, score, isChicken: false, penalties: ZERO };
}

function styledHands(style: HandStyle, strength: SeatNumbers, rng: Rng): SynthHand[] {
  const base = randomTable(strength, rng);
  switch (style) {
    case "normal":
      return base;
    case "draws":
      return base.map((hand, index) => (index % 4 === 0 ? hand : draw(hand.penalties)));
    case "allDraws":
      return Array.from({ length: HANDS_PER_TABLE }, () => draw());
    case "penalties":
      return base.map((hand, index) => {
        if (index % 2 !== 0) return hand;
        const penalties: SeatNumbers = [...hand.penalties];
        penalties[pickInt(rng, 0, 3)] += -10 * pickInt(rng, 1, 4);
        return { ...hand, penalties };
      });
    case "selfDraws":
      return base.map((hand) => (hand.winner == null ? hand : { ...hand, loser: null }));
    case "oneWinner":
      return base.map((_hand, index) => {
        const loser = pickInt(rng, 1, 3);
        return index % 5 === 4
          ? { winner: 0, loser: null, score: pickInt(rng, 8, 30), isChicken: false, penalties: ZERO }
          : { winner: 0, loser, score: pickInt(rng, 8, 30), isChicken: false, penalties: ZERO };
      });
    case "tiePairs": {
      const score = pickInt(rng, 10, 30);
      return shuffled([selfDrawBy(0, score), selfDrawBy(1, score), ...Array.from({ length: 14 }, () => draw())], rng);
    }
    case "tieTop3": {
      const score = pickInt(rng, 10, 30);
      return shuffled([selfDrawBy(0, score), selfDrawBy(1, score), selfDrawBy(2, score), ...Array.from({ length: 13 }, () => draw())], rng);
    }
    case "tieBottom3": {
      const score = pickInt(rng, 10, 30);
      return shuffled([selfDrawBy(3, score), ...Array.from({ length: 15 }, () => draw())], rng);
    }
    case "extreme": {
      const fixed: SynthHand[] = [
        win(rng, 8), win(rng, 8, { self: true }),
        win(rng, 12, { chicken: true }), win(rng, 12, { chicken: true, self: true }),
        win(rng, 13),
        win(rng, 120, { self: true }), win(rng, 120),
        win(rng, 60), win(rng, 60), win(rng, 60),
        draw([-100, 0, 0, 0]),
        draw([-10, -20, -30, -40]),
        draw([0, 10, 0, 0]),
      ];
      return shuffled([...fixed, ...randomTable(strength, rng, HANDS_PER_TABLE - fixed.length)], rng);
    }
  }
}

/** A penalty of -20 or less is saved as two penalties, so the app reads the "a;b" format. */
function penaltyText(value: number, compound: boolean): string {
  if (value === 0) return "";
  if (compound && value <= -20) return `-10;${value + 10}`;
  return String(value);
}

function handDoc(handId: number, hand: SynthHand, seats: string[], compound: boolean): Data {
  return {
    handId,
    playerWinnerId: hand.winner == null ? "" : seats[hand.winner],
    playerLooserId: hand.loser == null ? "" : seats[hand.loser],
    handScore: hand.winner == null ? "0" : String(hand.score),
    isChickenHand: hand.isChicken,
    isDone: true,
    playerEastPenalty: penaltyText(hand.penalties[0], compound),
    playerSouthPenalty: penaltyText(hand.penalties[1], compound),
    playerWestPenalty: penaltyText(hand.penalties[2], compound),
    playerNorthPenalty: penaltyText(hand.penalties[3], compound),
  };
}

// -- Tables ----------------------------------------------------------------------------------

type BuiltTable = { id: string; table: Data; hands: Data[] };

function emptyTable(round: number, tableId: number, playerIds: number[]): Data {
  return {
    roundId: round, tableId, playerIds,
    playerEastId: "", playerSouthId: "", playerWestId: "", playerNorthId: "",
    playerEastScore: "", playerSouthScore: "", playerWestScore: "", playerNorthScore: "",
    playerEastPoints: "", playerSouthPoints: "", playerWestPoints: "", playerNorthPoints: "",
    manualPlayerEastScore: "", manualPlayerSouthScore: "", manualPlayerWestScore: "", manualPlayerNorthScore: "",
    isCompleted: false,
    useTotalsOnly: false,
  };
}

function withScores(table: Data, scores: string[]): Data {
  const merged = {
    ...table,
    playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
  };
  return { ...merged, ...tablePointsOf(merged) };
}

function buildTable(params: {
  round: number;
  tableId: number;
  playerIds: number[];
  mode: TableMode;
  rng: Rng;
  strengthOf: (playerId: number) => number;
}): BuiltTable {
  const { round, tableId, playerIds, mode, rng } = params;
  const id = `${round}_${tableId}`;
  let table = emptyTable(round, tableId, playerIds);
  let hands: Data[] = Array.from({ length: HANDS_PER_TABLE }, (_, index) => ({ ...defaultHand(index + 1) }));
  let version = 0;

  if (mode.kind === "empty") {
    return { id, table: { ...table, ...calculateTableSummary(table, hands), version }, hands };
  }

  const seatIds = shuffled(playerIds.map(String), rng);
  const strength = seatIds.map((seat) => params.strengthOf(Number(seat))) as SeatNumbers;
  const seatCount = mode.kind === "seats" ? mode.seatCount ?? 4 : 4;
  const shownSeats = seatIds.map((seat, index) => (index < seatCount ? seat : ""));
  table = {
    ...table,
    playerEastId: shownSeats[0], playerSouthId: shownSeats[1], playerWestId: shownSeats[2], playerNorthId: shownSeats[3],
  };

  if (mode.kind === "seats") {
    version = 1;
  } else if (mode.kind === "full" || mode.kind === "fullOpen") {
    const style = mode.style ?? "normal";
    const synth = styledHands(style, strength, rng);
    hands = synth.map((hand, index) => handDoc(index + 1, hand, seatIds, style === "penalties"));
    const isCompleted = mode.kind === "full";
    table = { ...table, isCompleted };
    const totals = handScoreTotals(table, hands);
    table = withScores(table, totals == null ? ["", "", "", ""] : totals.map(String));
    version = isCompleted ? 1 : 3;
  } else if (mode.kind === "partial") {
    const synth = randomTable(strength, rng, HANDS_PER_TABLE);
    for (let index = 0; index < mode.done; index += 1) hands[index] = handDoc(index + 1, synth[index], seatIds, false);
    if (mode.extra === "pendingHand") {
      // The winner is chosen, but the score is not typed yet.
      hands[mode.done] = { ...defaultHand(mode.done + 1), playerWinnerId: seatIds[pickInt(rng, 0, 3)] };
    } else if (mode.extra === "invalidHands") {
      // A chicken hand above the chicken limit, and a hand with a discarder and no winner. Neither one counts.
      hands[mode.done] = {
        ...defaultHand(mode.done + 1), playerWinnerId: seatIds[0], playerLooserId: seatIds[1],
        handScore: "13", isChickenHand: true, isDone: true,
      };
      hands[mode.done + 1] = { ...defaultHand(mode.done + 2), playerLooserId: seatIds[2], isDone: true };
    }
    const totals = handScoreTotals(table, hands);
    table = withScores(table, totals == null ? ["", "", "", ""] : totals.map(String));
    version = Math.max(1, mode.done);
  } else if (mode.kind === "totals") {
    const scores = sumChanges(randomTable(strength, rng, HANDS_PER_TABLE)).map(String);
    if (mode.badSum) scores[0] = String(Number(scores[0]) + 7);
    table = {
      ...table,
      useTotalsOnly: true,
      isCompleted: true,
      ...(mode.manual ? {
        manualPlayerEastScore: scores[0], manualPlayerSouthScore: scores[1],
        manualPlayerWestScore: scores[2], manualPlayerNorthScore: scores[3],
      } : {}),
    };
    table = withScores(table, scores);
    version = 1;
  } else {
    // Totals mode with only three valid scores: the table is incomplete.
    table = {
      ...table,
      useTotalsOnly: true,
      manualPlayerEastScore: "120", manualPlayerSouthScore: "-40", manualPlayerWestScore: "-80", manualPlayerNorthScore: "",
    };
    table = { ...table, ...tablePointsOf(table) };
    version = 1;
  }

  const summary = calculateTableSummary(table, hands);
  return { id, table: { ...table, ...summary, version }, hands };
}

// -- Seating ---------------------------------------------------------------------------------

/** Builds the schedule. Players avoid repeat opponents, and teammates avoid the same table. */
function seatingFor(
  numPlayers: number,
  numRounds: number,
  teamOf: (playerId: number) => number | null,
  rng: Rng,
): number[][][] {
  const ids = Array.from({ length: numPlayers }, (_, index) => index + 1);
  const pairs = new Map<string, number>();
  const key = (a: number, b: number) => (a < b ? `${a}-${b}` : `${b}-${a}`);
  const rounds: number[][][] = [];
  for (let round = 0; round < numRounds; round += 1) {
    let best: number[][] | null = null;
    let bestCost = Infinity;
    for (let attempt = 0; attempt < 400; attempt += 1) {
      const order = shuffled(ids, rng);
      const tables = Array.from({ length: numPlayers / 4 }, (_, index) => order.slice(index * 4, index * 4 + 4));
      let cost = 0;
      tables.forEach((group) => {
        for (let a = 0; a < 4; a += 1) {
          for (let b = a + 1; b < 4; b += 1) {
            cost += (pairs.get(key(group[a], group[b])) ?? 0) * 10;
            const teamA = teamOf(group[a]);
            if (teamA != null && teamA === teamOf(group[b])) cost += 4;
          }
        }
      });
      if (cost < bestCost) {
        bestCost = cost;
        best = tables;
      }
    }
    best!.forEach((group) => {
      for (let a = 0; a < 4; a += 1) {
        for (let b = a + 1; b < 4; b += 1) pairs.set(key(group[a], group[b]), (pairs.get(key(group[a], group[b])) ?? 0) + 1);
      }
    });
    rounds.push(best!.map((group) => group.sort((a, b) => a - b)));
  }
  return rounds;
}

// -- Build one tournament --------------------------------------------------------------------

type Registry = Array<{ id: string; firstName: string; lastName: string; country: string }>;

type Write = { path: string; data: Data };
type BuiltTournament = {
  id: string;
  writes: Write[];
  tournament: Data;
  summary: { tables: Record<string, number>; players: number; ema: number; guests: number; open: number; completed: boolean };
};

function buildTournament(spec: TournamentSpec, registry: Registry): BuiltTournament {
  const rng = seededRng(hashText(spec.id));
  const numPlayers = spec.players.length;
  if (numPlayers % 4 !== 0) throw new Error(`${spec.id}: players must be a multiple of 4`);
  const root = `tournaments/${spec.id}`;
  const stamp = () => ({ createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  const writes: Write[] = [];

  const pool = shuffled(registry, rng);
  const strengthKeys = new Map<number, string>();
  let ema = 0;
  let guests = 0;
  let open = 0;
  spec.players.forEach((entry, index) => {
    const playerId = index + 1;
    const team = Math.ceil(playerId / 4);
    let data: Data;
    if (entry === "ema") {
      const member = pool.shift()!;
      ema += 1;
      strengthKeys.set(playerId, member.id);
      const name = `${titleCase(member.firstName)} ${member.lastName}`;
      data = { name, team, country: member.country, assignedEmaId: member.id, assignedCountry: member.country, nonMember: null };
      writes.push({ path: `${root}/emaPlayerAssignments/${member.id}`, data: { playerId, updatedAt: FieldValue.serverTimestamp() } });
    } else if (entry === "open") {
      open += 1;
      strengthKeys.set(playerId, `open-${spec.id}-${playerId}`);
      data = { name: `Player ${playerId}`, team, country: "", assignedCountry: "" };
    } else {
      guests += 1;
      strengthKeys.set(playerId, `guest-${entry.firstName}-${entry.lastName}-${entry.country}`);
      data = {
        name: `${entry.firstName} ${entry.lastName}`, team, country: entry.country, assignedCountry: entry.country,
        nonMember: { firstName: entry.firstName, lastName: entry.lastName, country: entry.country },
      };
    }
    writes.push({ path: `${root}/players/${playerId}`, data: { id: playerId, ...data, ...stamp() } });
  });

  if (spec.isTeams) {
    const teamCount = numPlayers / 4;
    for (let team = 1; team <= teamCount; team += 1) {
      writes.push({
        path: `${root}/teams/${team}`,
        data: {
          id: team,
          name: spec.teamNames?.[team - 1] ?? `Team ${team}`,
          playerIds: [4 * team - 3, 4 * team - 2, 4 * team - 1, 4 * team],
          ...stamp(),
        },
      });
    }
  }

  for (let round = 1; round <= spec.numRounds; round += 1) {
    writes.push({ path: `${root}/rounds/${round}`, data: { roundId: round, ...stamp() } });
  }

  const seating = seatingFor(numPlayers, spec.numRounds, (id) => (spec.isTeams ? Math.ceil(id / 4) : null), rng);
  const tablesPerRound = numPlayers / 4;
  const kinds: Record<string, number> = {};
  const tableVersions: Record<string, number> = {};
  let progress = 0;
  let allCompleted = true;
  let maxVersion = 0;
  seating.forEach((tables, roundIndex) => {
    tables.forEach((playerIds, tableIndex) => {
      const mode = spec.mode(roundIndex + 1, tableIndex + 1, tablesPerRound);
      const built = buildTable({
        round: roundIndex + 1, tableId: tableIndex + 1, playerIds, mode, rng,
        strengthOf: (playerId) => strengthOf(strengthKeys.get(playerId) ?? String(playerId)),
      });
      const status = String(built.table.completionStatus);
      kinds[`${mode.kind}:${status}`] = (kinds[`${mode.kind}:${status}`] ?? 0) + 1;
      if (built.table.hasProgress) progress += 1;
      if (!built.table.isCompleted) allCompleted = false;
      tableVersions[built.id] = Number(built.table.version);
      maxVersion += Number(built.table.version);
      writes.push({ path: `${root}/tables/${built.id}`, data: { ...built.table, ...stamp() } });
      built.hands.forEach((hand) => {
        writes.push({ path: `${root}/tables/${built.id}/hands/${hand.handId}`, data: { ...hand, ...stamp() } });
      });
    });
  });

  (spec.members ?? []).forEach((uid) => {
    writes.push({ path: `${root}/members/${uid}`, data: { uid, ...stamp() } });
  });

  const version = (revision: number) => ({ revision, changedAt: revision > 0 ? FieldValue.serverTimestamp() : null });
  const tournament: Data = {
    name: spec.name,
    shortName: spec.shortName,
    primaryColor: spec.primaryColor,
    associationLogoUrl: null,
    associationLogoPath: null,
    eventStartDate: spec.startDate,
    eventEndDate: spec.endDate,
    hostCountry: spec.hostCountry,
    hostCity: spec.hostCity,
    venueName: spec.venue?.venueName ?? "",
    venueAddress: spec.venue?.venueAddress ?? "",
    venueMapUrl: spec.venue?.venueMapUrl ?? "",
    contactEmail: spec.contactEmail,
    isTeams: spec.isTeams,
    countBestHands: spec.countBestHands,
    countChickenHands: spec.countChickenHands,
    numPlayers,
    numRounds: spec.numRounds,
    roundSchedules: spec.schedule.map((entry, index) => ({
      roundId: index + 1, date: entry?.date ?? null, startTime: entry?.startTime ?? null,
    })),
    agendaItems: spec.agenda,
    numTries: 200 + Math.floor(rng() * 5000),
    isCompleted: allCompleted,
    tablesWithProgress: progress,
    createdByUid: CREATED_BY_UID,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    dataVersions: {
      members: version(spec.members?.length ? 1 : 0),
      players: version(1),
      teams: version(spec.isTeams ? 1 : 0),
      rounds: version(1),
      tables: version(1 + maxVersion),
      tableVersions,
    },
  };
  return { id: spec.id, writes, tournament, summary: { tables: kinds, players: numPlayers, ema, guests, open, completed: allCompleted } };
}

// -- Run -------------------------------------------------------------------------------------

async function loadRegistry(): Promise<Registry> {
  const snapshot = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).select("firstName", "lastName", "country").get();
  return snapshot.docs
    .map((doc) => ({
      id: doc.id,
      firstName: String(doc.get("firstName") ?? "").trim(),
      lastName: String(doc.get("lastName") ?? "").trim(),
      country: String(doc.get("country") ?? "").trim().toUpperCase(),
    }))
    .filter((player) => player.firstName && player.lastName && player.country);
}

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} test tournaments for ${firebaseProjectId}.`);
  const registry = await loadRegistry();
  console.log(`EMA registry: ${registry.length} players.`);
  const specs = TOURNAMENTS.filter((spec) => only == null || spec.id === only);
  if (specs.some((spec) => !spec.id.startsWith("test-"))) throw new Error("Test tournament ids must start with test-");
  let created = 0;

  for (const spec of specs) {
    const built = buildTournament(spec, registry);
    console.log(`\n${spec.id} (${spec.name})`);
    console.log(`  players ${built.summary.players}: ${built.summary.ema} EMA, ${built.summary.guests} guests, ${built.summary.open} open`);
    console.log(`  tables ${JSON.stringify(built.summary.tables)}`);
    console.log(`  completed: ${built.summary.completed}; documents: ${built.writes.length + 1}`);
    if (!applyChanges) continue;

    const ref = db.collection("tournaments").doc(spec.id);
    await db.recursiveDelete(ref);
    const writer = db.bulkWriter();
    built.writes.forEach((write) => { void writer.set(db.doc(write.path), write.data); });
    await writer.close();
    // The tournament document comes last. A tournament without its document is invisible.
    await ref.set(built.tournament);
    created += 1;
    console.log("  written");
  }
  if (applyChanges && created > 0) {
    await db.collection("_meta").doc("dataVersions").set(globalVersionUpdate("tournaments"), { merge: true });
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
