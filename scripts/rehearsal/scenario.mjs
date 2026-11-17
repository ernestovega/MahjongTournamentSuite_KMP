#!/usr/bin/env node
// The plan of a game day scenario (docs/scenarios.md): who plays each slot, which accounts exist before, which slots
// the import fills and which ones the organizer assigns by hand, and the import files. The organizer on the tablet,
// the phones and the bots read it, so each one knows its part. It holds no password.
//
//   node scripts/rehearsal/scenario.mjs plan --scenario=small [--teams=off] [--chicken=off] [--best=off] \
//     [--phone-emulators] --out=out/scenario
//
// It writes plan.json, players.csv (the right file) and players-errors.csv (the same file with mistakes) to --out.
// Environment: REHEARSAL_PASSWORD, STAGING_ADMIN_EMAIL (the bot admin reads the EMA registry and the accounts) and
// REHEARSAL_IMAP_USER (the mailbox of the players).
import fs from "node:fs";
import path from "node:path";
import { Api, Metrics, STAGING_API, Session, parseArgs, rehearsalEmail } from "./api.mjs";

export const SCENARIOS = {
  small: { players: 24, rounds: 4 },
  medium: { players: 64, rounds: 7 },
  large: { players: 240, rounds: 11 },
};

/** Minutes of a round: the emulator that writes the hands takes about 70 s per hand, a bot about 40 s. */
export function roundMinutes(phoneEmulators) {
  return phoneEmulators ? 25 : 12;
}

function madrid(date) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Madrid", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(date).map((part) => [part.type, part.value]));
  return { date: `${parts.year}-${parts.month}-${parts.day}`, time: `${parts.hour}:${parts.minute}` };
}

function shuffle(list, random = Math.random) {
  const copy = [...list];
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const other = Math.floor(random() * (index + 1));
    [copy[index], copy[other]] = [copy[other], copy[index]];
  }
  return copy;
}

const on = (value, fallback = true) => (value == null ? fallback : !["off", "false", "no", "0"].includes(String(value).toLowerCase()));

/** The address of a player in the mailbox of the tests: <local>+<tag>-s<slot>@<domain>. */
export function playerEmail(mailbox, tag, slot) {
  const [local, domain] = mailbox.split("@");
  return `${local}+${tag}-s${String(slot).padStart(3, "0")}@${domain}`.toLowerCase();
}

/**
 * EMA players of the staging registry that no account uses yet: real names and countries, as in a real tournament.
 * The made-up players of the seeds and of the calibration (numbers from 99980000) stay out.
 */
async function freePlayers(api, token) {
  const registry = await api.call("GET", "/ema-player-registry", { token, label: "admin GET EMA registry" });
  const users = await api.call("GET", "/admin/users", { token, label: "admin GET users" });
  if (registry.status !== 200 || users.status !== 200) throw new Error(`Reading the registry: ${registry.status}, the accounts: ${users.status}`);
  const owned = new Set((users.json.users ?? []).map((user) => user.emaId).filter(Boolean));
  return (registry.json.players ?? []).filter((player) =>
    !player.disabled && !owned.has(player.emaId) && Number(player.emaId) < 99980000 && player.firstName && player.lastName
    // The test players of the registry are not people of a tournament: the numbers from 00 (the Google Play
    // reviewers have 00000000) and the names with "test", as the seeds leave them out.
    && !player.emaId.startsWith("00")
    && !/test/i.test(`${player.firstName} ${player.lastName}`));
}

function csv(rows) {
  return `${rows.map((row) => row.map((cell) => (/[",\n]/.test(String(cell)) ? `"${String(cell).replace(/"/g, '""')}"` : cell)).join(",")).join("\n")}\n`;
}

export async function makePlan(args) {
  const scenario = String(args.scenario ?? "small");
  const size = SCENARIOS[scenario];
  if (size == null) throw new Error(`Unknown scenario ${scenario}. Use ${Object.keys(SCENARIOS).join(", ")}.`);
  const mailbox = process.env.REHEARSAL_IMAP_USER ?? "";
  const password = process.env.REHEARSAL_PASSWORD ?? "";
  const baseEmail = process.env.STAGING_ADMIN_EMAIL ?? "";
  if (!mailbox || !password || !baseEmail) throw new Error("Set REHEARSAL_IMAP_USER, REHEARSAL_PASSWORD and STAGING_ADMIN_EMAIL.");
  const phoneEmulators = Boolean(args["phone-emulators"]);
  const isTeams = on(args.teams);
  const countChickenHands = on(args.chicken);
  const countBestHands = on(args.best);

  const api = new Api(args.api ?? STAGING_API, new Metrics());
  const admin = new Session(api, rehearsalEmail(baseEmail, "botadmin"), password);
  if (!(await admin.signIn())) throw new Error("The bot admin could not sign in.");
  // The EMA guests (people without an EMA number of their own) play too: two of them, when the registry has them.
  const all = await freePlayers(api, admin.token);
  const isGuest = (player) => /EMA GUEST/i.test(`${player.firstName} ${player.lastName}`);
  const guests = shuffle(all.filter(isGuest)).slice(0, 2);
  const free = shuffle([...guests, ...shuffle(all.filter((player) => !isGuest(player))).slice(0, size.players - guests.length)]);
  if (free.length < size.players) throw new Error(`The registry has only ${free.length} free EMA players.`);

  const now = new Date();
  const start = madrid(new Date(now.getTime() + 60 * 60_000));
  const stamp = madrid(now);
  const tag = `gd${stamp.date.slice(5).replace("-", "")}${Math.random().toString(36).slice(2, 6)}`;
  const minutes = roundMinutes(phoneEmulators);
  // About 1 in 4 players has an account before the tournament, made by hand in Users. The last slots (1 in 10, at
  // least 2) are not in the file: the organizer assigns them by hand and types their emails in the invitations.
  const manualCount = Math.max(2, Math.round(size.players / 10));
  const slots = Array.from({ length: size.players }, (_, index) => {
    const slot = index + 1;
    const player = free[index];
    return {
      slot,
      emaId: player.emaId,
      name: `${player.firstName} ${player.lastName}`,
      lastName: player.lastName,
      email: playerEmail(mailbox, tag, slot),
      team: isTeams ? Math.floor(index / 4) + 1 : 0,
      teamName: isTeams ? `Equipo ${String.fromCharCode(65 + (Math.floor(index / 4) % 26))}${Math.floor(index / 104) || ""}` : "",
      via: slot > size.players - manualCount ? "manual" : "import",
      accountBefore: slot % 4 === 2,
    };
  });

  const plan = {
    scenario,
    tag,
    name: `Game day ${scenario} ${stamp.date} ${stamp.time}`,
    shortName: `GD${scenario.slice(0, 1).toUpperCase()}${stamp.date.slice(8)}`,
    players: size.players,
    rounds: size.rounds,
    isTeams,
    countChickenHands,
    countBestHands,
    phoneEmulators,
    matchDurationMinutes: minutes,
    matchExtraMinutes: 3,
    // The schedule of the form. The rounds start when the organizer presses Play, one after the other.
    // The corner cases of the organizer (docs/scenarios.md). Round 2, so the first round plays as usual. The editor
    // deletes or corrects a hand in the admin app when the table has played [minHands] hands: the newest hand, or the
    // one [back] hands before it.
    // Round 1: table 3 starts late. Its scorer arrives [minutes] after the gong, and a player of it did not come: the
    // organizer substitutes the player (reason late, from round 1) [afterMinutes] after the gong, in Players.
    cornerCases: [
      { kind: "late", round: 1, table: 3, minutes: 4 },
      { kind: "substitution", round: 1, table: 3, reason: "LATE", afterMinutes: 1 },
      { kind: "delete", round: 2, table: 2, which: "last", minHands: 4 },
      { kind: "delete", round: 2, table: 3, which: "older", minHands: 6, back: 3 },
      { kind: "edit", round: 2, table: 4, which: "last", minHands: 4 },
      { kind: "edit", round: 2, table: 5, which: "older", minHands: 6, back: 3 },
    ],
    roundSchedules: Array.from({ length: size.rounds }, (_, index) => ({ roundId: index + 1, date: start.date, startTime: start.time })),
    slots,
  };

  const header = isTeams ? ["id", "ema id", "email", "team"] : ["id", "ema id", "email"];
  const row = (slot) => (isTeams ? [slot.slot, slot.emaId, slot.email, slot.teamName] : [slot.slot, slot.emaId, slot.email]);
  const imported = slots.filter((slot) => slot.via === "import");
  const good = [header, ...imported.map(row)];
  // The same file with the mistakes of a hurried organizer: an EMA number that the registry does not have, an email
  // with a typo, a slot twice and a slot that the tournament does not have.
  const bad = good.map((cells) => [...cells]);
  bad[1][1] = "99999999";
  bad[2][2] = String(bad[2][2]).replace("@", "@@");
  bad.push([...bad[3]]);
  bad.push([size.players + 5, imported[4].emaId, "", ...(isTeams ? [""] : [])]);
  return { plan, files: { "players.csv": csv(good), "players-errors.csv": csv(bad) } };
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const args = parseArgs(rest);
  if (command !== "plan") throw new Error("Usage: scenario.mjs plan --scenario=small|medium|large --out=<folder>");
  const { plan, files } = await makePlan(args);
  const out = String(args.out ?? "out/scenario");
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, "plan.json"), `${JSON.stringify(plan, null, 2)}\n`);
  for (const [name, text] of Object.entries(files)) fs.writeFileSync(path.join(out, name), text);
  console.log(`${plan.name}: ${plan.players} players, ${plan.rounds} rounds of ${plan.matchDurationMinutes} minutes, `
    + `${plan.slots.filter((slot) => slot.accountBefore).length} accounts before, `
    + `${plan.slots.filter((slot) => slot.via === "manual").length} slots by hand. Written to ${out}.`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
