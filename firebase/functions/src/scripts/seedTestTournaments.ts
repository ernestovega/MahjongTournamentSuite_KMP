import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";
import { globalVersionUpdate } from "../services/dataVersionsService";
import { handScoreTotals, tablePointsOf } from "../services/handScoring";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";
import { drawProblem } from "../services/tournamentDraw";
import { isValidPlayerCount, MIN_TOURNAMENT_PLAYERS } from "../services/tournamentContentRules";
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
 * It previews by default. Add --apply to write. Add --only=<id>,<id> to run some tournaments.
 *
 * The tester tournaments (flag "testers" in the spec) are not part of the default run, so the emulator seed stays the
 * same. Add --testers to write only them. They are ongoing for 6 days, with 3 rounds a day between 19:00 and 01:00 (Madrid). The upcoming ones start later and have no hands.
 */
const applyChanges = process.argv.includes("--apply");
const only = process.argv.find((arg) => arg.startsWith("--only="))?.split("=")[1]?.split(",");
const onlyTesters = process.argv.includes("--testers");

const CREATED_BY_UID = "h0Hgasv0Y1VBOXDVnAoSq1X8RMQ2";
const TEST_EDITOR_UID = "RH9PioX5AvNDulLDVa2JX45SP7H3";

type Data = Record<string, unknown>;

// -- Specification ---------------------------------------------------------------------------

type Guest = { firstName: string; lastName: string; country: string };
/** A player slot. "ema" takes a member of the EMA registry, "open" stays unassigned. */
type PlayerSpec = "ema" | "open" | Guest | { emaId: string };

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
  /** An address that does not load, to check how the screens look without the logo. */
  logoUrl?: string;
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
  /** Marks a tournament for the people who test the apps. It runs only with --testers or --only. */
  testers?: boolean;
  /** EMA ids that the random fill of "ema" slots must not use. */
  avoidEmaIds?: string[];
  /** Match time settings. Without it, the tournament uses the defaults (120 minutes, the table stops). */
  match?: { durationMinutes: number; endMode: "STOP" | "FINISH_HAND"; extraMinutes: number; timeZone: string };
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

const pad2 = (value: number): string => String(value).padStart(2, "0");

/** The date and the start time of a moment in the time zone of this computer, as the schedule stores them. */
function localSlot(at: Date): { date: string; startTime: string } {
  return {
    date: `${at.getFullYear()}-${pad2(at.getMonth() + 1)}-${pad2(at.getDate())}`,
    startTime: `${pad2(at.getHours())}:${pad2(at.getMinutes())}`,
  };
}

/**
 * The live tournament has round 4 running at the time of the seed run: it started 15 minutes ago. The apps allow
 * hands only while a round plays (120 minutes by default) and treat a table as finished after that. Rounds 1 to 3
 * start 160 minutes apart before it. Rounds 5 and 6 start two days later. Run the seed again to get a fresh window.
 * The emulators must use the time zone of this computer.
 */
const ROUND4_START = new Date(Date.now() - 15 * 60 * 1000);
const LIVE_PAST_ROUNDS = [3, 2, 1, 0].map((back) => localSlot(new Date(ROUND4_START.getTime() - back * 160 * 60 * 1000)));
const TODAY = LIVE_PAST_ROUNDS[0].date;

/** The time zone of this computer. The seed writes round times in it. */
const LOCAL_ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone;

/**
 * The match time tournaments. Round 1 started 40 minutes before the seed run and a match lasts 30 minutes, so the
 * time is over. In the first tournament the players can still finish the hand for 120 minutes (extra time).
 * In the second tournament the tables stop, so the players cannot change them.
 */
const MATCH_OVER_START = localSlot(new Date(Date.now() - 40 * 60 * 1000));

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
    id: "test-min-16",
    name: "Test - Minimum 16 players",
    shortName: "TEST MIN16",
    primaryColor: "#02B16B",
    startDate: "2026-06-13", endDate: "2026-06-13",
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-min@example.com",
    isTeams: false, numRounds: 1,
    players: Array.from({ length: 16 }, () => "ema" as const),
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
    players: mixedPlayers(16, 2),
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
    // The special names fill the first slots. EMA players fill the rest up to the minimum of 16.
    players: [...UNICODE_GUESTS, ...Array.from({ length: 16 - UNICODE_GUESTS.length }, () => "ema" as const)],
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
    numRounds: 4,
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
    schedule: spread(4, "2026-08-22", 3),
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
    name: "Test - Without best hands, chicken hands or dates",
    shortName: "BARE",
    primaryColor: "#455A64",
    startDate: "2026-09-12", endDate: "2026-09-13",
    hostCountry: "ES", hostCity: "Valencia",
    venue: ESKALERRIA, contactEmail: "test-bare@example.com",
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
    isTeams: true, numRounds: 5,
    players: mixedPlayers(28, 6),
    countBestHands: true, countChickenHands: true,
    schedule: spread(5, "2027-03-06", 4),
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
    isTeams: false, numRounds: 7,
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
    id: "test-large",
    name: "Test - Large team tournament",
    shortName: "LARGE",
    primaryColor: "#006064",
    startDate: "2026-10-03", endDate: "2026-10-04",
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-large@example.com",
    isTeams: true, numRounds: 3,
    players: mixedPlayers(64, 8),
    countBestHands: true, countChickenHands: true,
    schedule: spread(3, "2026-10-03", 2),
    agenda: standardAgenda("2026-10-03", "2026-10-04"),
    // Sixteen tables per round. The last round is a mix of finished, running and empty tables.
    mode: (round, table) => {
      if (round < 3) return { kind: "full" };
      return cycle<TableMode>([{ kind: "full" }, { kind: "partial", done: 7 }, { kind: "seats" }, { kind: "empty" }], table - 1);
    },
  },
  {
    id: "test-match-extra-time",
    name: "Test - Match time over, extra time running",
    shortName: "EXTRA-TIME",
    primaryColor: "#6A1B9A",
    startDate: MATCH_OVER_START.date, endDate: MATCH_OVER_START.date,
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-extra@example.com",
    isTeams: false, numRounds: 1,
    players: Array.from({ length: 16 }, () => "ema" as const),
    countBestHands: true, countChickenHands: true,
    schedule: [MATCH_OVER_START],
    agenda: [{ title: "Only game", date: MATCH_OVER_START.date, startTime: MATCH_OVER_START.startTime }],
    // Table 1 has 7 hands, table 2 has seats and no hands, table 3 is empty, table 4 is finished.
    mode: (_round, table) => cycle<TableMode>([{ kind: "partial", done: 7 }, { kind: "seats" }, { kind: "empty" }, { kind: "full" }], table - 1),
    match: { durationMinutes: 30, endMode: "FINISH_HAND", extraMinutes: 120, timeZone: LOCAL_ZONE },
  },
  {
    id: "test-match-over",
    name: "Test - Match time over, tables stopped",
    shortName: "TIME-OVER",
    primaryColor: "#B71C1C",
    startDate: MATCH_OVER_START.date, endDate: MATCH_OVER_START.date,
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-over@example.com",
    isTeams: false, numRounds: 1,
    players: Array.from({ length: 16 }, () => "ema" as const),
    countBestHands: true, countChickenHands: true,
    schedule: [MATCH_OVER_START],
    agenda: [{ title: "Only game", date: MATCH_OVER_START.date, startTime: MATCH_OVER_START.startTime }],
    mode: (_round, table) => cycle<TableMode>([{ kind: "partial", done: 7 }, { kind: "seats" }, { kind: "empty" }, { kind: "full" }], table - 1),
    match: { durationMinutes: 30, endMode: "STOP", extraMinutes: 15, timeZone: LOCAL_ZONE },
  },
  {
    id: "test-last-table",
    name: "Test - One table left, broken logo, chicken hands off",
    shortName: "LASTTABLE",
    primaryColor: "#33691E",
    startDate: "2026-10-10", endDate: "2026-10-10",
    hostCountry: "ES", hostCity: "Granada",
    venue: ESKALERRIA, contactEmail: "test-last@example.com",
    logoUrl: "https://invalid.example.test/logo.png",
    isTeams: false, numRounds: 3,
    players: mixedPlayers(24, 6),
    countBestHands: true, countChickenHands: false,
    schedule: spread(3, "2026-10-10"),
    agenda: standardAgenda("2026-10-10", "2026-10-10"),
    // Only table 6 of round 3 has one hand left. Writing it completes the tournament.
    mode: (round, table) => (round === 3 && table === 6 ? { kind: "partial", done: 15 } : { kind: "full" }),
  },
  {
    id: "test-live-ongoing",
    name: "Test - Live ongoing tournament",
    shortName: "LIVE",
    primaryColor: "#D81B60",
    startDate: TODAY, endDate: addDays(LIVE_PAST_ROUNDS[3].date, 2),
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: "test-live@example.com",
    isTeams: true,
    teamNames: ["Aces", "Bamboo", "Circles", "Dragons", "East Wind", "Flowers", "Green Dragon", "Honors", "Imperial", "Jade"],
    numRounds: 6,
    players: mixedPlayers(40, 4),
    countBestHands: true, countChickenHands: true,
    schedule: [...LIVE_PAST_ROUNDS, ...spread(2, addDays(LIVE_PAST_ROUNDS[3].date, 2), 4)],
    agenda: standardAgenda(TODAY, addDays(LIVE_PAST_ROUNDS[3].date, 2)),
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

// -- Tester tournaments ----------------------------------------------------------------------

/** The people who test the apps. The first four have an account. The last three have none, to test invitations. */
const TESTER_ACCOUNTS: Record<string, string> = {
  "10990026": "takw7aMVudQWwoM4M20iG5argmr1", // Edgar
  "10990020": "UMpmHUF1kXNHwoQS7hFU2tMrWw63", // Edu
  "10990018": "4hHTIL9w9bV8slRrA8N1R50YM3N2", // Héctor
  "10990039": "h0Hgasv0Y1VBOXDVnAoSq1X8RMQ2", // Ernesto
};
const TESTER_NO_ACCOUNT = ["10990043", "10990133", "10990051"]; // Raúl Ríos Navarro, Cintia Horcajo Jerez, Marcos San Antonio Serrano
const TESTER_EMA_IDS = [...Object.keys(TESTER_ACCOUNTS), ...TESTER_NO_ACCOUNT];

const TESTER_START = localSlot(new Date()).date;
const TESTER_END = addDays(TESTER_START, 5);

/** [mixedPlayers] with the given slots (counted from 1) replaced by the testers. */
function withTesters(count: number, placement: Record<number, string>): PlayerSpec[] {
  return mixedPlayers(count, 6).map((entry, index) => {
    const emaId = placement[index + 1];
    return emaId ? { emaId } : entry;
  });
}

/** Every round has two running tables and one table with seats. The other tables have no hands, so the testers can start them. */
function testerMode(_round: number, table: number): TableMode {
  if (table > 3) return { kind: "empty" };
  return cycle<TableMode>([{ kind: "partial", done: 4 }, { kind: "partial", done: 9 }, { kind: "seats" }], table - 1);
}

/**
 * Two players cannot meet twice in a tournament, so 24 players allow 5 rounds at most.
 * With teams, 24 players (6 teams) cannot play 5 rounds with teammates apart, so a teams tournament uses 28 players (7 teams).
 */
const TESTER_ROUNDS_PER_PART = 5;

/** The rounds follow the time of the venue (Madrid), so the match time rules apply. */
const TESTER_BASE = {
  testers: true,
  startDate: TESTER_START, endDate: TESTER_END,
  hostCountry: "ES", hostCity: "Madrid",
  venue: ESKALERRIA,
  countBestHands: true, countChickenHands: true,
  numRounds: TESTER_ROUNDS_PER_PART,
  schedule: [] as Array<{ date: string; startTime: string }>,
  match: { durationMinutes: 120, endMode: "FINISH_HAND" as const, extraMinutes: 15, timeZone: "Europe/Madrid" },
  agenda: standardAgenda(TESTER_START, TESTER_END),
  mode: (round: number, table: number): TableMode => testerMode(round, table),
};

/** An ongoing tournament in three parts. */
const ONGOING_TESTER_TOURNAMENTS: TournamentSpec[] = [
  {
    ...TESTER_BASE,
    id: "test-ongoing-individual-in",
    name: "Test - Ongoing individual, testers play",
    shortName: "IND-IN",
    primaryColor: "#2E7D32",
    contactEmail: "test-ind-in@example.com",
    isTeams: false,
    // Slots 1 to 4 have accounts. Slots 5, 6 and 12 have no account yet.
    players: withTesters(24, { 1: "10990026", 2: "10990020", 3: "10990018", 4: "10990039", 5: "10990043", 6: "10990133", 12: "10990051" }),
    avoidEmaIds: TESTER_EMA_IDS,
  },
  {
    ...TESTER_BASE,
    id: "test-ongoing-teams-in",
    name: "Test - Ongoing teams, testers play",
    shortName: "TEAMS-IN",
    primaryColor: "#1565C0",
    contactEmail: "test-teams-in@example.com",
    isTeams: true,
    // Edgar and Edu share team 1. Héctor, Raúl, Ernesto, Marcos and Cintia sit in teams 2 to 4.
    players: withTesters(28, { 1: "10990026", 2: "10990020", 5: "10990018", 6: "10990043", 9: "10990039", 12: "10990051", 13: "10990133" }),
    avoidEmaIds: TESTER_EMA_IDS,
  },
  {
    ...TESTER_BASE,
    id: "test-ongoing-individual-out",
    name: "Test - Ongoing individual, testers not in it",
    shortName: "IND-OUT",
    primaryColor: "#EF6C00",
    contactEmail: "test-ind-out@example.com",
    isTeams: false,
    players: mixedPlayers(24, 6),
    avoidEmaIds: TESTER_EMA_IDS,
  },
  {
    ...TESTER_BASE,
    id: "test-ongoing-teams-out",
    name: "Test - Ongoing teams, testers not in it",
    shortName: "TEAMS-OUT",
    primaryColor: "#AD1457",
    contactEmail: "test-teams-out@example.com",
    isTeams: true,
    players: mixedPlayers(28, 6),
    avoidEmaIds: TESTER_EMA_IDS,
  },
];

/**
 * Each ongoing tournament has 3 parts, one round a day. Part 1 plays at 19:00, part 2 at 21:00 and part 3 at 23:00.
 * A match lasts 120 minutes, so every part has a round playing in its time window.
 */
const TESTER_PARTS = 3;
const TESTER_TOURNAMENTS: TournamentSpec[] = [
  ...ONGOING_TESTER_TOURNAMENTS.flatMap((spec) => Array.from({ length: TESTER_PARTS }, (_, part): TournamentSpec => ({
    ...spec,
    id: `${spec.id}-${part + 1}`,
    name: `${spec.name} (${part + 1}/${TESTER_PARTS})`,
    shortName: `${spec.shortName} ${part + 1}`,
    contactEmail: spec.contactEmail?.replace("@", `-${part + 1}@`),
    numRounds: TESTER_ROUNDS_PER_PART,
    schedule: Array.from({ length: TESTER_ROUNDS_PER_PART }, (_, day) => ({
      date: addDays(TESTER_START, day),
      startTime: ["19:00", "21:00", "23:00"][part],
    })),
  }))),

  // Upcoming tournaments: no round started, no hands. Some have the testers and some do not.
  ...[
    { slug: "individual-in", name: "Test - Upcoming individual, testers play", short: "UP-IND-IN", color: "#00838F", start: "2026-11-14", teams: false, rounds: 5, testers: true },
    { slug: "teams-in", name: "Test - Upcoming teams, testers play", short: "UP-TEAMS-IN", color: "#6A1B9A", start: "2026-11-28", teams: true, rounds: 5, testers: true },
    { slug: "individual-out", name: "Test - Upcoming individual, testers not in it", short: "UP-IND-OUT", color: "#827717", start: "2026-12-12", teams: false, rounds: 4, testers: false },
    { slug: "teams-out", name: "Test - Upcoming teams, testers not in it", short: "UP-TEAMS-OUT", color: "#BF360C", start: "2027-01-16", teams: true, rounds: 5, testers: false },
  ].map((item): TournamentSpec => ({
    id: `test-upcoming-${item.slug}`,
    name: item.name,
    shortName: item.short,
    primaryColor: item.color,
    startDate: item.start, endDate: addDays(item.start, 1),
    hostCountry: "ES", hostCity: "Madrid",
    venue: ESKALERRIA, contactEmail: `test-upcoming-${item.slug}@example.com`,
    isTeams: item.teams, numRounds: item.rounds,
    players: item.testers
      ? withTesters(item.teams ? 28 : 24, item.teams
        ? { 1: "10990026", 2: "10990020", 5: "10990018", 6: "10990043", 9: "10990039", 12: "10990051", 13: "10990133" }
        : { 1: "10990026", 2: "10990020", 3: "10990018", 4: "10990039", 5: "10990043", 6: "10990133", 12: "10990051" })
      : mixedPlayers(item.teams ? 28 : 24, 6),
    countBestHands: true, countChickenHands: true,
    schedule: spread(item.rounds, item.start, 3),
    agenda: standardAgenda(item.start, addDays(item.start, 1)),
    mode: () => ({ kind: "empty" }),
    testers: true,
    avoidEmaIds: TESTER_EMA_IDS,
    match: { durationMinutes: 120, endMode: "FINISH_HAND", extraMinutes: 15, timeZone: "Europe/Madrid" },
  })),
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

/**
 * Builds the schedule. No two players sit together twice. In a teams tournament, teammates never share a table.
 * It throws when the round count is too high for that, so a test tournament never has repeat opponents
 * and a teams tournament never seats teammates together.
 */
function seatingFor(
  numPlayers: number,
  numRounds: number,
  teamOf: (playerId: number) => number | null,
  rng: Rng,
): number[][][] {
  const isTeams = teamOf(1) != null;
  // Without teams mode the team number has no meaning, so the first pass is the only one.
  for (let attempt = 0; attempt < (isTeams ? 20000 : 300); attempt += 1) {
    const rounds = tryNoRepeatSeating(numPlayers, numRounds, teamOf, rng);
    if (rounds != null) return rounds;
  }
  throw new Error(`No schedule found for ${numPlayers} players and ${numRounds} rounds${isTeams ? " with teammates apart" : ""}`);
}

function tryNoRepeatSeating(
  numPlayers: number,
  numRounds: number,
  teamOf: (playerId: number) => number | null,
  rng: Rng,
): number[][][] | null {
  const met = new Set<string>();
  const key = (a: number, b: number) => (a < b ? `${a}-${b}` : `${b}-${a}`);
  const canSit = (a: number, b: number) => {
    const team = teamOf(a);
    return !met.has(key(a, b)) && (team == null || team !== teamOf(b));
  };
  const rounds: number[][][] = [];
  for (let round = 0; round < numRounds; round += 1) {
    let groups: number[][] | null = null;
    // Depth-first search with a step limit, so a dead end does not run for long.
    let steps = 0;
    const search = (left: number[], done: number[][]): number[][] | null => {
      if (left.length === 0) return done;
      if ((steps += 1) > 300000) return null;
      const [first, ...others] = left;
      const options = shuffled(others.filter((other) => canSit(first, other)), rng);
      for (let i = 0; i < options.length; i += 1) {
        for (let j = i + 1; j < options.length; j += 1) {
          if (!canSit(options[i], options[j])) continue;
          for (let k = j + 1; k < options.length; k += 1) {
            if (!canSit(options[i], options[k]) || !canSit(options[j], options[k])) continue;
            const group = [first, options[i], options[j], options[k]];
            const rest = left.filter((id) => !group.includes(id));
            const found = search(rest, [...done, group]);
            if (found != null) return found;
          }
        }
      }
      return null;
    };
    groups = search(shuffled(Array.from({ length: numPlayers }, (_, index) => index + 1), rng), []);
    if (groups == null) return null;
    groups.forEach((group) => {
      for (let a = 0; a < 4; a += 1) {
        for (let b = a + 1; b < 4; b += 1) met.add(key(group[a], group[b]));
      }
    });
    rounds.push(shuffled(groups, rng).map((group) => group.sort((x, y) => x - y)));
  }
  return rounds;
}

// -- Build one tournament --------------------------------------------------------------------

function isFixedEma(entry: PlayerSpec): entry is { emaId: string } {
  return typeof entry === "object" && "emaId" in entry;
}

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

  const avoid = new Set(spec.avoidEmaIds ?? []);
  const pool = shuffled(registry.filter((member) => !avoid.has(member.id)), rng);
  const strengthKeys = new Map<number, string>();
  let ema = 0;
  let guests = 0;
  let open = 0;
  spec.players.forEach((entry, index) => {
    const playerId = index + 1;
    const team = Math.ceil(playerId / 4);
    let data: Data;
    if (entry === "ema" || isFixedEma(entry)) {
      const member = entry === "ema" ? pool.shift()! : registry.find((candidate) => candidate.id === entry.emaId);
      if (!member) throw new Error(`${spec.id}: EMA id ${entry === "ema" ? "" : entry.emaId} is not in the registry`);
      ema += 1;
      strengthKeys.set(playerId, member.id);
      const name = `${titleCase(member.firstName)} ${member.lastName}`;
      data = { name, team, country: member.country, assignedEmaId: member.id, assignedCountry: member.country, nonMember: null };
      const accountUid = TESTER_ACCOUNTS[member.id];
      if (spec.testers && accountUid) {
        // Same result as linking a known account: the slot points to the account and the account lists the tournament.
        data = { ...data, accountUid, invitationStatus: "ACCEPTED" };
        writes.push({
          path: `users/${accountUid}/playerTournaments/${spec.id}`,
          data: { playerId, emaId: member.id, tournamentId: spec.id, createdAt: FieldValue.serverTimestamp() },
        });
      }
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
  const problem = drawProblem(
    Array.from({ length: numPlayers }, (_, index) => ({ id: index + 1, team: Math.ceil((index + 1) / 4) })),
    seating.flatMap((tables, roundIndex) => tables.map((playerIds, tableIndex) => ({ roundId: roundIndex + 1, tableId: tableIndex + 1, playerIds }))),
    spec.numRounds, tablesPerRound, spec.isTeams,
  );
  if (problem) throw new Error(`${spec.id}: ${problem}`);
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
    associationLogoUrl: spec.logoUrl ?? null,
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
    ...(spec.match ? {
      matchDurationMinutes: spec.match.durationMinutes,
      matchEndMode: spec.match.endMode,
      matchExtraMinutes: spec.match.extraMinutes,
      timeZone: spec.match.timeZone,
    } : {}),
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
  const everySpec = [...TOURNAMENTS, ...TESTER_TOURNAMENTS];
  const specs = everySpec.filter((spec) => (only != null ? only.includes(spec.id) : onlyTesters ? spec.testers : !spec.testers));
  if (specs.some((spec) => !spec.id.startsWith("test-"))) throw new Error("Test tournament ids must start with test-");
  const small = specs.find((spec) => !isValidPlayerCount(spec.players.length));
  if (small) throw new Error(`Test tournament ${small.id} must have at least ${MIN_TOURNAMENT_PLAYERS} players in groups of 4`);
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
