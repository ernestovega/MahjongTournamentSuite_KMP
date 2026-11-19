#!/usr/bin/env node
// A short tournament played only by bots, to check the bots, the API and the checks before the real rehearsal.
// It needs the accounts of `rehearsalAccounts --calibration-setup`: 16 players (+mts-cal01..16, EMA 99980001-16)
// and an admin (+mts-botadmin). It acts as Ernesto: it creates the tournament, assigns the players, presses Play of
// each round and closes the tables that stay open. The tournament stays in staging (--delete removes it at the end).
//
//   node scripts/rehearsal/calibrate.mjs --minutes=8 --hand-seconds=20-30 --spectators=20 --report=out/calibration.json
//
// --app-slots=1 leaves slot 1 to an emulator with the real app (emulator-player.sh with REHEARSAL_ACCOUNT=cal), and
// --wait-before-play=240 gives it time to start before the first Play.
//
// Environment: REHEARSAL_PASSWORD and STAGING_ADMIN_EMAIL.
import { Api, Metrics, STAGING_API, Session, parseArgs, parseRange, rehearsalEmail, sleep } from "./api.mjs";
import { runBots, writeReport } from "./bots.mjs";
import { checkTournament, verdicts, writeCheck } from "./report.mjs";

/** Every calibration stays in staging, with the time of its start in the name, so it feeds the statistics. */
const NAME = `Calibración bots ${new Date().toISOString().slice(0, 16).replace("T", " ")}`;
const FIRST_EMA = 99980001;
/** 16 players in 2 rounds without a repeated pair: the rows of a 4x4 grid in round 1, its columns in round 2. */
const TABLES = [
  ...[0, 1, 2, 3].map((row) => ({ roundId: 1, tableId: row + 1, playerIds: [1, 2, 3, 4].map((seat) => row * 4 + seat) })),
  ...[0, 1, 2, 3].map((column) => ({ roundId: 2, tableId: column + 1, playerIds: [0, 1, 2, 3].map((row) => row * 4 + column + 1) })),
];

function madridNow(plusMinutes) {
  const date = new Date(Date.now() + plusMinutes * 60_000);
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Madrid", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(date).map((part) => [part.type, part.value]));
  return { date: `${parts.year}-${parts.month}-${parts.day}`, time: `${parts.hour}:${parts.minute}` };
}

async function must(promise, what) {
  const reply = await promise;
  if (reply.status < 200 || reply.status >= 300) throw new Error(`${what}: ${reply.status} ${JSON.stringify(reply.json).slice(0, 300)}`);
  return reply;
}

async function adminTournaments(api, token) {
  const reply = await must(api.call("GET", "/tournaments", { token, label: "admin GET tournaments" }), "Listing the tournaments");
  return reply.json.tournaments ?? [];
}

const BOTS_CLUB = "Bots Mahjong Club";

/** The organization that owns the calibration tournament. Every tournament needs one. */
async function botsOrganizationId(api, token) {
  const reply = await must(api.call("GET", "/admin/organizations", { token, label: "admin GET organizations" }), "Listing the organizations");
  const existing = (reply.json.organizations ?? []).find((item) => item.name === BOTS_CLUB);
  if (existing != null) return existing.id;
  const body = { name: BOTS_CLUB, type: "CLUB", country: "ES", primaryColor: "#02B16B" };
  const created = await must(api.call("POST", "/admin/organizations", { token, body, label: "admin POST organization" }), "Creating the organization");
  return created.json.id;
}

async function createTournament(api, token, minutes, extraMinutes) {
  const start = madridNow(2);
  const body = {
    name: NAME,
    shortName: "CALIB",
    organizerIds: [await botsOrganizationId(api, token)],
    eventStartDate: start.date,
    eventEndDate: start.date,
    hostCountry: "ES",
    hostCity: "Madrid",
    venueName: "Bots",
    venueAddress: "Calle Mayor 1, Madrid",
    contactEmail: "bots@example.com",
    isTeams: false,
    isPublic: true,
    numPlayers: 16,
    numRounds: 2,
    numTries: 1,
    timeZone: "Europe/Madrid",
    matchDurationMinutes: minutes,
    matchEndMode: "FINISH_HAND",
    matchExtraMinutes: extraMinutes,
    players: Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: 0 })),
    tables: TABLES,
    roundSchedules: [1, 2].map((roundId) => ({ roundId, date: start.date, startTime: start.time })),
  };
  await must(api.call("POST", "/tournaments", { token, body, label: "admin POST tournament" }), "Creating the tournament");
  const created = (await adminTournaments(api, token)).find((item) => item.name === NAME);
  if (created == null) throw new Error("The new tournament is not in the list");
  for (let slot = 1; slot <= 16; slot += 1) {
    await must(api.call("PUT", `/tournaments/${created.id}/players/${slot}`, {
      token, body: { emaId: String(FIRST_EMA + slot - 1) }, label: "admin PUT player",
    }), `Assigning slot ${slot}`);
  }
  return created.id;
}

async function roundTables(api, tournamentId, roundId) {
  const reply = await api.call("GET", `/public/tournaments/${tournamentId}/rounds/${roundId}/tables-with-hands`, { label: "admin GET round tables" });
  return reply.status === 200 ? reply.json.tables : [];
}

/** Presses Play, waits until the tables close or the time with its extra minutes is over, then closes the rest. */
async function playRound(api, session, metrics, tournamentId, roundId, minutes, extraMinutes) {
  await must(api.call("POST", `/tournaments/${tournamentId}/rounds/${roundId}/clock`, {
    token: await session.bearer(), body: { action: "start" }, label: "admin POST clock",
  }), `Play of round ${roundId}`);
  metrics.event("play", { round: roundId });
  console.log(`Round ${roundId}: Play.`);
  const deadline = Date.now() + (minutes + extraMinutes + 1) * 60_000;
  let open = [];
  while (Date.now() < deadline) {
    await sleep(15_000);
    open = (await roundTables(api, tournamentId, roundId)).filter((item) => !item.table.isCompleted).map((item) => item.table.tableId);
    if (open.length === 0) break;
  }
  if (open.length > 0) {
    await must(api.call("POST", `/tournaments/${tournamentId}/complete-open-tables`, {
      token: await session.bearer(), body: { tables: open.map((tableId) => ({ roundId, tableId })) }, label: "admin POST complete tables",
    }), `Closing the open tables of round ${roundId}`);
    metrics.event("closed-by-admin", { round: roundId, tables: open });
  }
  console.log(`Round ${roundId}: done${open.length ? `, the admin closed tables ${open.join(", ")}` : ""}.`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const password = process.env.REHEARSAL_PASSWORD ?? "";
  const baseEmail = process.env.STAGING_ADMIN_EMAIL ?? "";
  if (!password || !baseEmail) throw new Error("Set REHEARSAL_PASSWORD and STAGING_ADMIN_EMAIL.");
  const minutes = Number(args.minutes ?? 8);
  const extraMinutes = Number(args["extra-minutes"] ?? 2);
  const [handMin, handMax] = String(args["hand-seconds"] ?? "20-30").split("-").map(Number);
  const appSlots = new Set(parseRange(args["app-slots"] ?? ""));
  const waitBeforePlay = Number(args["wait-before-play"] ?? 50);
  const metrics = new Metrics();
  const api = new Api(args.api ?? STAGING_API, metrics);

  const admin = new Session(api, rehearsalEmail(baseEmail, "botadmin"), password);
  if (!(await admin.signIn())) throw new Error("The bot admin could not sign in. Run rehearsalAccounts --calibration-setup.");
  const tournamentId = await createTournament(api, admin.token, minutes, extraMinutes);
  console.log(`Created the calibration tournament ${tournamentId}.`);

  let finished = false;
  const bots = runBots({
    api, metrics, tournamentId, baseEmail, password,
    slots: Array.from({ length: 16 }, (_, index) => index + 1).filter((slot) => !appSlots.has(slot)),
    accountSuffix: (slot) => `cal${String(slot).padStart(2, "0")}`,
    spectators: Number(args.spectators ?? 20),
    venuePhones: Number(args["venue-phones"] ?? 0),
    until: Date.now() + (2 * (minutes + extraMinutes + 2) + 5) * 60_000 + waitBeforePlay * 1_000,
    stopped: () => finished,
    handSeconds: [handMin, handMax ?? handMin],
    penaltyRate: Number(args["penalty-rate"] ?? 0.1),
    lateTables: ["2_3"],
    raceTables: ["1_2"],
    verbose: Boolean(args.verbose),
  });

  let failure = null;
  try {
    // The 16 bots sign in one every 2.5 seconds. Play waits until they are in, as players sit before the start.
    await sleep(waitBeforePlay * 1_000);
    await playRound(api, admin, metrics, tournamentId, 1, minutes, extraMinutes);
    await playRound(api, admin, metrics, tournamentId, 2, minutes, extraMinutes);
    await sleep(20_000);
  } catch (error) {
    failure = error;
  }
  finished = true;
  const report = await bots;
  writeReport(args.report ?? "out/calibration.json", "Calibration: bots", report);

  const check = await checkTournament(api, tournamentId, [report]);
  const integrity = await api.call("GET", `/tournaments/${tournamentId}/integrity`, { token: await admin.bearer(), label: "admin GET integrity" });
  const lines = verdicts(check, [report], integrity.status === 200 ? integrity.json : null);
  if (appSlots.size > 0) {
    // The tables where the app is the scorer: the lowest slot of the table plays with the app.
    const appTables = TABLES.filter((table) => appSlots.has(Math.min(...table.playerIds)));
    const all = await api.call("GET", `/public/tournaments/${tournamentId}/tables-with-hands`, { label: "admin GET all tables" });
    const byKey = new Map((all.json?.tables ?? []).map((item) => [`${item.table.roundId}_${item.table.tableId}`, item]));
    const written = appTables.map((table) => {
      const item = byKey.get(`${table.roundId}_${table.tableId}`);
      return `${table.roundId}_${table.tableId}: ${(item?.hands ?? []).filter((hand) => hand.isDone).length} hands`;
    });
    const ok = written.every((line) => !line.endsWith(": 0 hands"));
    lines.push(`${ok ? "✅" : "❌"} The app on the emulator wrote the hands of its tables (${written.join(", ")}).`);
  }
  writeCheck(String(args.report ?? "out/calibration.json").replace(/\.json$/, "-check.json"), "Calibration: checks", check, lines);
  if (integrity.status === 200 && integrity.json.findings?.length) {
    console.log("Integrity findings:", JSON.stringify(integrity.json.findings, null, 2));
  }

  if (args.delete) {
    await api.call("DELETE", `/tournaments/${tournamentId}`, { token: await admin.bearer(), label: "admin DELETE tournament" });
    console.log("Deleted the calibration tournament.");
  }
  if (failure) throw failure;
  if (lines.some((line) => line.startsWith("❌"))) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
