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
  // The corner cases only, to try a change in minutes: 2 short rounds, and every case in round 1.
  corners: { players: 24, rounds: 2, minutes: 8, cornerRound: 1 },
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

/**
 * The planned start of the rounds, in Madrid. The rounds start when the organizer presses Play, so the planned time
 * only has to be later today: the form of a new tournament keeps its dates on today, and a round on another day is
 * outside them. It is an hour from now, and 23:59 when that is already tomorrow.
 */
export function plannedStart(now) {
  const today = madrid(now);
  const later = madrid(new Date(now.getTime() + 60 * 60_000));
  return later.date === today.date ? later : { date: today.date, time: "23:59" };
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
async function freePlayers(api, token, mailbox) {
  const registry = await api.call("GET", "/ema-player-registry", { token, label: "admin GET EMA registry" });
  const users = await api.call("GET", "/admin/users", { token, label: "admin GET users" });
  if (registry.status !== 200 || users.status !== 200) throw new Error(`Reading the registry: ${registry.status}, the accounts: ${users.status}`);
  return {
    free: eligiblePlayers(registry.json.players ?? [], users.json.users ?? []),
    returning: returningPlayers(registry.json.players ?? [], users.json.users ?? [], mailbox),
  };
}

/**
 * The players of earlier game days: an account of the mailbox of the tests (local+gd<tag>-sNNN@domain) with the EMA
 * player of the registry that a scenario may seat. They play again with their account, as a person who played other
 * tournaments does: the app sends them a reminder instead of an invitation. Each one comes with the email of its account.
 */
export function returningPlayers(registryPlayers, users, mailbox) {
  const [local, domain] = String(mailbox).toLowerCase().split("@");
  if (!local || !domain) return [];
  const escape = (text) => text.replace(/[.*+?^$()|[\]{}\\]/g, "\\$&");
  const pattern = new RegExp(`^${escape(local)}\\+gd[a-z0-9]+-s\\d{3}@${escape(domain)}$`);
  const allowed = new Map(eligiblePlayers(registryPlayers, []).map((player) => [player.emaId, player]));
  return users.flatMap((user) => {
    const email = String(user.email ?? "").toLowerCase();
    const player = allowed.get(user.emaId);
    if (user.disabled || player == null || !pattern.test(email)) return [];
    return [{ ...player, email, ...(user.uid ? { uid: user.uid } : {}) }];
  });
}

/**
 * The accounts of earlier game days to delete so that [wanted] EMA players are free: the oldest game days first (by the
 * date in the tag of the email, gdMMDD), and never so many that fewer than [keep] players of earlier game days stay.
 * Deleting an account leaves its EMA player and the tournaments it played; the EMA player is then free for a new
 * player, so each game day invites new accounts again (approved by Ernesto, 20 November 2026).
 */
export function accountsToFreeUp(returning, freeCount, wanted, keep) {
  const missing = wanted - freeCount;
  if (missing <= 0) return [];
  const tagOf = (player) => (/\+gd(\d{4})/.exec(player.email)?.[1] ?? "9999");
  const oldest = [...returning].sort((a, b) => tagOf(a).localeCompare(tagOf(b)) || a.email.localeCompare(b.email));
  return oldest.slice(0, Math.max(0, Math.min(missing, returning.length - keep))).filter((player) => player.uid);
}

/** Deletes the accounts in Users, as the bot admin, at most 50 a request (the limit of the server). */
async function deleteAccounts(api, token, accounts) {
  let deleted = 0;
  for (let start = 0; start < accounts.length; start += 50) {
    const uids = accounts.slice(start, start + 50).map((account) => account.uid);
    const reply = await api.call("POST", "/admin/users/delete", { token, body: { uids }, label: "admin POST users delete" });
    if (reply.status !== 200) throw new Error(`Deleting accounts of earlier game days: ${reply.status}`);
    deleted += (reply.json.results ?? []).filter((result) => result.outcome === "DELETED").length;
  }
  return deleted;
}

/** The players of the registry that a scenario may seat: see [freePlayers]. */
export function eligiblePlayers(registryPlayers, users) {
  const owned = new Set(users.map((user) => user.emaId).filter(Boolean));
  return registryPlayers.filter((player) =>
    !player.disabled && !owned.has(player.emaId) && Number(player.emaId) < 99980000 && player.firstName && player.lastName
    // The test players of the registry are not people of a tournament: the numbers from 00 (the Google Play
    // reviewers have 00000000) and the names with "test", as the seeds leave them out.
    && !player.emaId.startsWith("00")
    // An EMA number has 8 digits. The registry keeps a few with a lost leading zero (8010027): the import pads
    // the file to 8 digits and then does not find them, so a file with them would be refused.
    && /^\d{8}$/.test(player.emaId)
    && !/test/i.test(`${player.firstName} ${player.lastName}`));
}

/** How the player of a slot comes: "new" (an invitation), "account-before" (an account made by hand in Users) or "returning". */
export function slotKind(slot) {
  if (slot % 4 === 1) return "new";
  if (slot % 20 === 2) return "account-before";
  return "returning";
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
  let { free: all, returning: earlier } = await freePlayers(api, admin.token, mailbox);
  // The new players and those with an account made in Users need free EMA players. When the registry has too few, the
  // accounts of the oldest game days are deleted, and their EMA players are free again.
  const wantedFree = Array.from({ length: size.players }, (_, index) => slotKind(index + 1)).filter((kind) => kind !== "returning").length;
  const freeUp = accountsToFreeUp(earlier, all.length, wantedFree, size.players - wantedFree);
  if (freeUp.length > 0) {
    const deleted = await deleteAccounts(api, admin.token, freeUp);
    console.log(`${deleted} accounts of the oldest game days deleted, so their EMA players are free for new players.`);
    ({ free: all, returning: earlier } = await freePlayers(api, admin.token, mailbox));
  }
  const isGuest = (player) => /EMA GUEST/i.test(`${player.firstName} ${player.lastName}`);
  // Who plays: 1 in 4 players is new and gets an invitation, 1 in 20 has an account made by hand in Users before the
  // tournament, and the others played an earlier game day and come back with their account. Without enough of those,
  // new players take their place. A new player needs an EMA player without an account, and the registry of staging
  // has a few hundred: players who come back keep a large scenario at about 70 of them.
  const kinds = Array.from({ length: size.players }, (_, index) => slotKind(index + 1));
  // When the free EMA players run short, players of earlier game days also take slots of new players, from the last slot
  // up, while a few new players (1 in 20, at least 2) still get an invitation. With fewer free EMA players than that, the
  // free ones are the new players, and with none, every player comes back from an earlier game day.
  const minimumNew = Math.min(all.length, Math.max(2, Math.ceil(size.players / 20)));
  for (let index = kinds.length - 1; index >= 0; index -= 1) {
    const backWanted = kinds.filter((kind) => kind === "returning").length;
    const newWanted = kinds.length - Math.min(backWanted, earlier.length);
    if (newWanted <= all.length || backWanted >= earlier.length || kinds.length - backWanted <= minimumNew) break;
    if (kinds[index] !== "returning") kinds[index] = "returning";
  }
  const back = shuffle(earlier).slice(0, kinds.filter((kind) => kind === "returning").length);
  const newCount = size.players - back.length;
  const guests = shuffle(all.filter(isGuest)).slice(0, Math.min(2, newCount));
  const fresh = shuffle([...guests, ...shuffle(all.filter((player) => !isGuest(player))).slice(0, newCount - guests.length)]);
  if (fresh.length < newCount) {
    throw new Error(`The registry has only ${fresh.length} free EMA players, and ${earlier.length} players of earlier game days.`);
  }

  const now = new Date();
  const stamp = madrid(now);
  const start = plannedStart(now);
  const tag = `gd${stamp.date.slice(5).replace("-", "")}${Math.random().toString(36).slice(2, 6)}`;
  const minutes = size.minutes ?? roundMinutes(phoneEmulators);
  // The cases of the editor play in round 2, so the first round plays as usual. In corners they play in round 1, at
  // tables other than the late one.
  const fix = size.cornerRound ?? 2;
  const fixTables = fix === 1 ? [2, 4, 5, 6] : [2, 3, 4, 5];
  // About 1 in 4 players has an account before the tournament, made by hand in Users. The last slots (1 in 10, at
  // least 2) are not in the file: the organizer assigns them by hand and types their emails in the invitations.
  const manualCount = Math.max(2, Math.round(size.players / 10));
  // The slots that would come back take the players of earlier game days while there are some, then new players.
  let backIndex = 0;
  let freshIndex = 0;
  const slots = Array.from({ length: size.players }, (_, index) => {
    const slot = index + 1;
    const returning = kinds[index] === "returning" && backIndex < back.length;
    const player = returning ? back[backIndex++] : fresh[freshIndex++];
    return {
      slot,
      emaId: player.emaId,
      name: `${player.firstName} ${player.lastName}`,
      lastName: player.lastName,
      email: returning ? player.email : playerEmail(mailbox, tag, slot),
      returning,
      team: isTeams ? Math.floor(index / 4) + 1 : 0,
      teamName: isTeams ? `Equipo ${String.fromCharCode(65 + (Math.floor(index / 4) % 26))}${Math.floor(index / 104) || ""}` : "",
      via: slot > size.players - manualCount ? "manual" : "import",
      accountBefore: returning || kinds[index] === "account-before",
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
    // Round 1, table 1: two phones save the first hand at the same time, and after [afterHands] hands the scorer
    // gives two penalties (one each time) and writes a draw: the penalties count in the draw.
    cornerCases: [
      { kind: "race", round: 1, table: 1 },
      { kind: "penalty-draw", round: 1, table: 1, afterHands: 2, penalties: ["-10", "-5"] },
      { kind: "late", round: 1, table: 3, minutes: 4 },
      // Round 1, table 4: the players swap two seats before the first hand, and try again after it.
      { kind: "seat-change", round: 1, table: 4 },
      // The last round, table 1: a slow table that plays its last hand in the extra time after the gong.
      { kind: "extra-time", round: size.rounds, table: 1, afterHands: 3 },
      // The last round, table 2: the players leave after 3 hands, and the organizer closes the table in the review.
      { kind: "abandoned", round: size.rounds, table: 2, afterHands: 3 },
      { kind: "substitution", round: 1, table: 3, reason: "LATE", afterMinutes: 1 },
      { kind: "delete", round: fix, table: fixTables[0], which: "last", minHands: 4 },
      { kind: "delete", round: fix, table: fixTables[1], which: "older", minHands: 6, back: 3 },
      { kind: "edit", round: fix, table: fixTables[2], which: "last", minHands: 4 },
      { kind: "edit", round: fix, table: fixTables[3], which: "older", minHands: 6, back: 3 },
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
    + `${plan.slots.filter((slot) => slot.returning).length} back from earlier game days, ${plan.slots.filter((slot) => slot.accountBefore && !slot.returning).length} accounts made in Users, `
    + `${plan.slots.filter((slot) => slot.via === "manual").length} slots by hand. Written to ${out}.`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
