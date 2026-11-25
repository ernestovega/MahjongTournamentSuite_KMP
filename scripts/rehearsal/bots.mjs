#!/usr/bin/env node
// Bots that play a tournament in staging through the API, as the players do with the app, and spectators that only
// read. See docs/rehearsal.md.
//
//   node scripts/rehearsal/bots.mjs --tournament="Ensayo General MTS 2026" --slots=10-48 --spectators=100 \
//     --until=2026-11-19T19:45:00+01:00 --report=out/bots-github.json
//
// Environment: REHEARSAL_PASSWORD (the password of the bot accounts) and STAGING_ADMIN_EMAIL (the base of the emails:
// slot 12 signs in as <local>+mts-j12@<domain>).
//
// At each table the lowest slot writes the hands, as the scorer of a real table. Slot 1 is a person, slots 2-9 are
// emulators, the others are bots. A process writes only for the tables whose lowest slot is one of its bots, so several
// processes (GitHub, the Mac) and the emulators never write the same table.
import fs, { appendFileSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

import {
  Api, Metrics, Session, STAGING_API, parseArgs, parseRange, percentile, randomBetween, rehearsalEmail, sleep,
} from "./api.mjs";

const SEATS = ["East", "South", "West", "North"];
const SCORES = [8, 8, 8, 8, 9, 10, 10, 12, 13, 16, 17, 24, 24, 32, 40, 48, 64, 88];
/** Answers that are part of a normal round: the bot waits and tries again. */
const WAIT_MESSAGES = [
  "has not started yet", "seats of this table are not assigned", "time of this match is over",
  "closed the hand entry", "uses totals only",
];

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function randomHand(seats) {
  const roll = Math.random();
  if (roll < 0.1) return { isDraw: true };
  const winner = pick(seats);
  const score = String(pick(SCORES));
  if (roll < 0.55) return { playerWinnerId: winner, playerLooserId: "", handScore: score };
  const loser = pick(seats.filter((seat) => seat !== winner));
  // A chicken hand is won on a discard with 8 to 12 points. The players mark about a third of those.
  const isChickenHand = Number(score) <= 12 && Math.random() < 0.35;
  return { playerWinnerId: winner, playerLooserId: loser, handScore: score, ...(isChickenHand ? { isChickenHand: true } : {}) };
}

const textOf = (value) => String(value ?? "").trim();

/** True when the hand on the server is the hand that the bot sent. */
export function sameHand(saved, server) {
  if (saved.isDraw) return textOf(server.playerWinnerId) === "";
  return textOf(server.playerWinnerId) === textOf(saved.playerWinnerId)
    && textOf(server.playerLooserId).replace(/^-$/, "") === textOf(saved.playerLooserId)
    && textOf(server.handScore) === textOf(saved.handScore)
    && Boolean(server.isChickenHand) === Boolean(saved.isChickenHand);
}

/** The hand as the server has it, in the form that the bot sends. */
function handOf(server) {
  if (textOf(server.playerWinnerId) === "") return { isDraw: true };
  return {
    playerWinnerId: textOf(server.playerWinnerId),
    playerLooserId: textOf(server.playerLooserId).replace(/^-$/, ""),
    handScore: textOf(server.handScore),
    ...(server.isChickenHand ? { isChickenHand: true } : {}),
  };
}

function seatsOf(table) {
  const seats = [table.playerEastId, table.playerSouthId, table.playerWestId, table.playerNorthId];
  return seats.every((id) => id != null && String(id).trim() !== "" && String(id) !== "null") ? seats.map(String) : null;
}

/** Shared by the bots of one process: what the tournament looks like now, read at most every few seconds. */
class Tournament {
  constructor(api, id, clockCacheMs = 5_000, metrics = null) {
    this.api = api;
    this.id = id;
    this.clockCacheMs = clockCacheMs;
    this.metrics = metrics;
    this.tablesByRound = new Map();
    this.clocks = { at: 0, value: null };
    this.info = { at: 0, value: null };
  }

  async roundClocks(fresh = false) {
    if (fresh || Date.now() - this.clocks.at > this.clockCacheMs) {
      this.clocks.at = Date.now();
      const reply = await this.api.call("GET", `/public/tournaments/${this.id}/round-clocks`, { label: "GET round-clocks" });
      if (reply.status === 200) {
        this.noteClockChanges(this.clocks.value, reply.json);
        this.clocks.value = reply.json;
      }
    }
    return this.clocks.value;
  }

  /**
   * What the phones see of the organizer in Round control: a pause, a resume and a change of the time (Set time, Stop).
   * Each change is an event once per process, with the round and the change of the end in ms.
   */
  noteClockChanges(before, after) {
    if (this.metrics == null || before == null) return;
    for (const clock of after?.roundClocks ?? []) {
      const old = (before.roundClocks ?? []).find((item) => item.roundId === clock.roundId);
      if (old == null) continue;
      const round = clock.roundId;
      if (old.pausedAtMs == null && clock.pausedAtMs != null) this.metrics.event("clock-paused-seen", { round, atMs: Date.now() });
      if (old.pausedAtMs != null && clock.pausedAtMs == null) this.metrics.event("clock-resumed-seen", { round, atMs: Date.now(), pausedMs: clock.pausedMs ?? 0 });
      // Set time and Stop change the match time (adjustMs). A pause moves the end too, but by itself (pausedMs).
      if ((old.adjustMs ?? 0) !== (clock.adjustMs ?? 0)) {
        this.metrics.event("clock-adjusted-seen", { round, atMs: Date.now(), from: old.adjustMs ?? 0, to: clock.adjustMs ?? 0, endChangeMs: (clock.adjustMs ?? 0) - (old.adjustMs ?? 0) });
      }
    }
  }

  /** True when the clock of the round is paused now, read again from the server. */
  async isPaused(roundId) {
    const clock = ((await this.roundClocks(true))?.roundClocks ?? []).find((item) => item.roundId === roundId);
    return clock?.pausedAtMs != null;
  }

  /** The instant of the end of the match time of a round (as clockEndMs of the server), or null. */
  async matchEnd(roundId, minutes) {
    this.clocks.at = 0;
    const clock = ((await this.roundClocks())?.roundClocks ?? []).find((item) => item.roundId === roundId);
    if (clock == null || clock.pausedAtMs != null || minutes == null) return null;
    return clock.startedAtMs + (clock.pausedMs ?? 0) + Math.max(0, minutes * 60_000 + (clock.adjustMs ?? 0));
  }

  /** The highest round with a clock, or 1 before the first Play. */
  async currentRound() {
    const clocks = (await this.roundClocks())?.roundClocks ?? [];
    return clocks.length === 0 ? 1 : Math.max(...clocks.map((clock) => clock.roundId));
  }

  async hasClock(roundId) {
    const clocks = (await this.roundClocks())?.roundClocks ?? [];
    return clocks.some((clock) => clock.roundId === roundId);
  }

  async tables(roundId) {
    const cached = this.tablesByRound.get(roundId);
    if (cached && Date.now() - cached.at < 60_000) return cached.value;
    const reply = await this.api.call("GET", `/public/tournaments/${this.id}/tables?roundId=${roundId}`, { label: "GET tables" });
    if (reply.status !== 200) return cached?.value ?? [];
    this.tablesByRound.set(roundId, { at: Date.now(), value: reply.json.tables });
    return reply.json.tables;
  }

  async details() {
    if (Date.now() - this.info.at > 60_000) {
      this.info.at = Date.now();
      const reply = await this.api.call("GET", `/public/tournaments/${this.id}`, { label: "GET tournament" });
      if (reply.status === 200) this.info.value = reply.json;
    }
    return this.info.value;
  }
}

/** One player. It polls like the app, and writes the hands when it is the scorer of its table. */
class PlayerBot {
  constructor(context, slotNumber, session) {
    Object.assign(this, context);
    this.slotNumber = slotNumber;
    this.session = session;
    this.playerId = null;
    this.lastManifest = null;
    this.seen = new Map();
    this.signatures = new Map();
  }

  log(message) {
    if (this.verbose) console.log(`[j${String(this.slotNumber).padStart(2, "0")}] ${message}`);
  }

  async findSlot() {
    const reply = await this.api.call("GET", "/me", { token: await this.session.bearer(), label: "GET me" });
    const slot = reply.json?.tournaments?.find((item) => item.tournamentId === this.tournament.id);
    this.playerId = slot?.playerId ?? null;
    return this.playerId != null;
  }

  async manifest() {
    const reply = await this.api.call("GET", `/public/tournaments/${this.tournament.id}/sync/manifest`, { label: "GET manifest" });
    return reply.status === 200 ? reply.json : null;
  }

  async readTable(roundId, tableId) {
    const reply = await this.api.call("GET", `/public/tournaments/${this.tournament.id}/tables/${roundId}/${tableId}`, { label: "GET table" });
    return reply.status === 200 ? reply.json : null;
  }

  /** Records when this bot first sees each hand, to measure how long a hand takes to reach the other phones. */
  noteHands(key, data) {
    for (const hand of data?.hands ?? []) {
      if (!hand.isDone) continue;
      const id = `${key}#${hand.handId}`;
      if (this.seen.has(id)) continue;
      this.seen.set(id, Date.now());
      const sentAt = this.sentAt.get(id);
      if (sentAt != null && !this.writes(key)) this.delays.push(Date.now() - sentAt);
    }
  }

  /**
   * Compares the hands that this bot wrote with the table on the server. A hand that is no longer done was deleted by
   * an editor, and a hand with other values was corrected. Both are events. A corrected hand is now the right one, so
   * the record of the bot takes its new values. Returns true when a hand was deleted.
   */
  noteCorrections(key, data) {
    let deleted = false;
    const latest = new Map();
    for (const entry of this.handLog) if (entry.table === key && !entry.deleted) latest.set(entry.handId, entry);
    // The newest hand of the table: the checks tell a correction of the last hand from one of an older hand.
    const newest = Math.max(0, ...[...latest.keys()].map(Number));
    for (const entry of latest.values()) {
      const server = data.hands.find((hand) => Number(hand.handId) === Number(entry.handId));
      if (server == null) continue;
      if (!server.isDone || !sameHand(entry.hand, server)) this.holdTables.delete(key);
      if (!server.isDone) {
        entry.deleted = true;
        deleted = true;
        this.metrics.event("hand-deleted-seen", {
          table: key, handId: entry.handId, newest, slot: this.slotNumber, version: data.table?.version, to: handOf(server),
        });
      } else if (!sameHand(entry.hand, server)) {
        this.metrics.event("hand-corrected-seen", {
          table: key, handId: entry.handId, newest, slot: this.slotNumber, from: entry.hand, to: handOf(server), version: data.table?.version,
        });
        entry.hand = handOf(server);
        entry.corrected = true;
      }
    }
    return deleted;
  }

  /** The other phones of the table see a correction of the editor as a change of a hand that they had already seen. */
  noteWatchedCorrections(key, data) {
    for (const hand of data?.hands ?? []) {
      const id = `${key}#${hand.handId}`;
      const signature = hand.isDone ? JSON.stringify(handOf(hand)) : "";
      const before = this.signatures.get(id);
      this.signatures.set(id, signature);
      if (before == null || before === "" || before === signature) continue;
      const kind = signature === "" ? "hand-deleted-seen" : "hand-corrected-seen";
      this.metrics.event(kind, { table: key, handId: hand.handId, slot: this.slotNumber, watcher: true, version: data?.table?.version });
    }
  }

  writes(key) {
    return this.scorerOf.get(key) === this.slotNumber;
  }

  /**
   * True when the table plays only draws for the tie of the plan: the tie table of round 1, and in the next rounds any
   * table with one of its players. A draw with no penalty gives every player of the table the same result.
   */
  async playsForTheTie(roundId, table) {
    if (this.tieTable == null) return false;
    const [tieRound, tieTableId] = this.tieTable.split("_").map(Number);
    if (roundId === tieRound) return table.tableId === tieTableId;
    if (roundId < tieRound) return false;
    const tied = (await this.tournament.tables(tieRound)).find((item) => item.tableId === tieTableId)?.playerIds ?? [];
    return table.playerIds.some((id) => tied.includes(id));
  }

  async run() {
    if (!(await this.findSlot())) {
      this.metrics.event("no-slot", { slot: this.slotNumber });
      this.log("not in the tournament");
      return;
    }
    let roundId = 0;
    while (Date.now() < this.until && !this.stopped()) {
      const current = await this.tournament.currentRound();
      if (current !== roundId) {
        roundId = current;
        await this.playRound(roundId);
      } else {
        await sleep(this.pollMs);
        await this.manifest();
      }
      const details = await this.tournament.details();
      if (details?.isCompleted) break;
    }
  }

  async playRound(roundId) {
    const tables = await this.tournament.tables(roundId);
    const table = tables.find((item) => item.playerIds.includes(this.playerId));
    if (table == null) return;
    const key = `${roundId}_${table.tableId}`;
    const scorer = Math.min(...table.playerIds);
    const mine = this.ownSlots.has(scorer);
    if (mine) this.scorerOf.set(key, scorer);
    const scorerHere = scorer === this.playerId;
    this.log(`round ${roundId}, table ${table.tableId}${scorerHere ? ", scorer" : ""}`);
    if (scorerHere) await this.score(roundId, table, key);
    else await this.watch(roundId, table, key);
  }

  /** The scorer: sets the seats, then writes a hand every minute or so until the table is completed. */
  async score(roundId, table, key) {
    const late = this.lateTables.has(key);
    if (late) {
      this.metrics.event("late-table", { table: key });
      await sleep(this.lateMs);
    }
    const tie = await this.playsForTheTie(roundId, table);
    if (tie) this.metrics.event("tie-table", { table: key, playerIds: table.playerIds });
    let nextHandAt = Date.now() + randomBetween(...this.handSeconds) * 1_000 * 0.5;
    while (Date.now() < this.until && !this.stopped()) {
      const data = await this.readTable(roundId, table.tableId);
      if (data == null) {
        await sleep(this.pollMs);
        continue;
      }
      this.noteHands(key, data);
      // An editor of the organization deleted or corrected one of the hands of this table in the admin app.
      const deleted = this.noteCorrections(key, data);
      if (data.table.isCompleted) {
        this.metrics.event("table-completed", { table: key, hands: data.hands.filter((hand) => hand.isDone).length });
        if (this.stopTables.has(key)) await this.handAfterTheStop(roundId, table, key, seatsOf(data.table));
        return;
      }
      if (deleted) {
        // The player who called the organization writes the right hand again, at once.
        const seatsNow = seatsOf(data.table);
        // A table of the tie plays only draws, also when a hand is written again.
        if (seatsNow != null && (await this.writeHand(roundId, table, key, seatsNow, tie ? "reentry-draw" : "reentry")) === "done") return;
        continue;
      }
      if (!(await this.tournament.hasClock(roundId)) && seatsOf(data.table) != null) {
        await sleep(this.pollMs);
        continue;
      }
      let seats = seatsOf(data.table);
      if (seats == null) {
        await this.setSeats(roundId, table, data.table.version);
        continue;
      }
      const doneHands = data.hands.filter((hand) => hand.isDone).length;
      if (this.seatChangeTables.has(key)) {
        const step = this.seatChangeTables.get(key);
        // The corner case: the players swap East and South before the first hand (allowed), and try to swap them back
        // after it (refused).
        if (step === "before" && doneHands === 0) {
          const reply = await this.setSeats(roundId, { ...table, playerIds: [seats[1], seats[0], seats[2], seats[3]].map(Number) }, data.table.version);
          this.metrics.event("seat-change", { table: key, when: "before", status: reply.status, seats: [seats[1], seats[0], seats[2], seats[3]] });
          this.seatChangeTables.set(key, "after");
          continue;
        }
        if (step === "after" && doneHands >= 1) {
          const reply = await this.setSeats(roundId, { ...table, playerIds: [seats[1], seats[0], seats[2], seats[3]].map(Number) }, data.table.version);
          this.metrics.event("seat-change", { table: key, when: "after", status: reply.status, message: reply.json?.message ?? "" });
          this.seatChangeTables.delete(key);
          continue;
        }
      }
      if (this.totalsOnlyTables.has(key)) {
        // The corner case: the table plays on paper, and an editor types its totals in the admin app.
        this.metrics.event("table-left-for-totals", { table: key });
        return;
      }
      const offline = this.offlineTables.get(key);
      if (offline != null && !this.offlineDone.has(key) && doneHands >= offline.afterHands && Date.now() >= nextHandAt) {
        this.offlineDone.add(key);
        const outcome = await this.writeHand(roundId, table, key, seats, tie ? "offline-draw" : "offline");
        if (outcome === "done") return;
        nextHandAt = Date.now() + randomBetween(...this.handSeconds) * 1_000;
        continue;
      }
      const leaveAfter = this.abandonTables.get(key);
      if (leaveAfter != null && doneHands >= leaveAfter) {
        // The corner case: the players leave the table without finishing it, and the organizer closes it.
        this.metrics.event("table-abandoned", { table: key, hands: doneHands });
        return;
      }
      const extra = this.extraTimeTables.get(key);
      if (extra != null && doneHands >= extra.afterHands) {
        // The corner case: the table is slow and plays its last hand in the extra time after the gong.
        const end = await this.tournament.matchEnd(roundId, this.matchMinutes);
        if (end == null || Date.now() < end + this.extraTimeAfterMs) {
          if (!this.extraWaiting.has(key)) {
            this.extraWaiting.add(key);
            this.metrics.event("table-waits-for-extra-time", { table: key, hands: doneHands });
          }
          await sleep(this.pollMs);
          continue;
        }
        this.extraTimeTables.delete(key);
        const outcome = await this.writeHand(roundId, table, key, seats, tie ? "extra-draw" : "extra");
        if (outcome === "done") return;
        nextHandAt = Date.now() + this.waitMs;
        continue;
      }
      if (Date.now() < nextHandAt) {
        await sleep(Math.min(this.pollMs, nextHandAt - Date.now()));
        await this.manifest();
        continue;
      }
      const hold = this.holdTables.get(key);
      if (hold != null && data.hands.filter((hand) => hand.isDone).length >= hold) {
        if (!this.holdUntil.has(key)) {
          this.holdUntil.set(key, Date.now() + this.holdMs);
          this.metrics.event("table-waits-for-editor", { table: key, hands: hold });
        }
        if (Date.now() < this.holdUntil.get(key)) {
          await sleep(this.pollMs);
          continue;
        }
        this.holdTables.delete(key);
      }
      let kind = "hand";
      const forced = this.penaltyDrawTables.get(key);
      if (forced != null && !this.penaltyDrawDone.has(key) && data.hands.filter((hand) => hand.isDone).length >= forced.afterHands) {
        // The corner case: the penalties of the plan, one call each (the second one adds a row), then a draw.
        this.penaltyDrawDone.add(key);
        let version = data.table.version;
        for (const [seat, value] of forced.penalties.entries()) {
          const reply = await this.penalty(roundId, table.tableId, seats, version, seat, value, true);
          if (reply.status === 200) version = Number(reply.json?.tableVersion ?? version + 1);
        }
        kind = "draw";
      } else if (tie) {
        kind = "draw";
      } else if (forced == null && Math.random() < this.penaltyRate) {
        await this.penalty(roundId, table.tableId, seats, data.table.version);
      }
      // The hand that the editor corrects while the table waits is a won hand: a draw has no score to correct.
      if (kind === "hand" && hold != null && data.hands.filter((hand) => hand.isDone).length === hold - 1) kind = "won";
      const outcome = await this.writeHand(roundId, table, key, seats, kind);
      if (outcome === "done") return;
      nextHandAt = Date.now() + (outcome === "wait" ? this.waitMs : randomBetween(...this.handSeconds) * 1_000);
    }
  }

  /** Saves the seats in the order of table.playerIds. Returns the answer. */
  async setSeats(roundId, table, version) {
    const [east, south, west, north] = table.playerIds.map(String);
    const reply = await this.api.call(
      "POST",
      `/player/tournaments/${this.tournament.id}/tables/${roundId}/${table.tableId}/seats`,
      {
        token: await this.session.bearer(),
        body: { playerEastId: east, playerSouthId: south, playerWestId: west, playerNorthId: north, expectedVersion: version },
        label: "POST seats",
      },
    );
    if (reply.status !== 200) await sleep(this.waitMs);
    return reply;
  }

  async penalty(roundId, tableId, seats, version, seat = Math.floor(Math.random() * 4), value = "-10", planned = false) {
    const body = { expectedVersion: version };
    SEATS.forEach((name, index) => {
      body[`player${name}Penalty`] = index === seat ? value : "";
    });
    const reply = await this.api.call("POST", `/player/tournaments/${this.tournament.id}/tables/${roundId}/${tableId}/penalties`, {
      token: await this.session.bearer(),
      body,
      label: "POST penalty",
    });
    if (reply.status === 200 || planned) {
      this.metrics.event("penalty", { table: `${roundId}_${tableId}`, seat: seats[seat], value, planned, status: reply.status, handId: reply.json?.handId });
    }
    return reply;
  }

  /** Preview, then save, as the app does. Returns "ok", "wait" or "done". */
  async writeHand(roundId, table, key, seats, kind = "hand") {
    const path = `/player/tournaments/${this.tournament.id}/tables/${roundId}/${table.tableId}/hands`;
    const draw = kind === "draw" || kind.endsWith("-draw");
    kind = kind.replace(/-draw$/, "");
    let hand = draw ? { isDraw: true } : randomHand(seats);
    while (kind === "won" && hand.isDraw) hand = randomHand(seats);
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      const token = await this.session.bearer();
      const preview = await this.api.call("POST", `${path}/preview`, { token, body: hand, label: "POST hand preview" });
      if (preview.status !== 200) return this.refusal(preview, key);
      const body = { ...hand, expectedVersion: Number(preview.json.tableVersion ?? 0) };
      const race = this.raceTables.has(key) && !this.raced.has(key);
      const save = () => this.api.call("POST", path, { token, body, label: "POST hand" });
      const replies = race ? await Promise.all([save(), this.raceSave(key, path, body)]) : [await save()];
      if (race) {
        this.raced.add(key);
        this.metrics.event("race", { table: key, statuses: replies.map((reply) => reply.status) });
      }
      const reply = replies.find((item) => item.status === 200) ?? replies[0];
      if (reply.status === 200) {
        const id = `${key}#${reply.json.handId}`;
        this.sentAt.set(id, Date.now());
        this.handLog.push({ table: key, handId: reply.json.handId, hand, ms: Math.round(reply.ms), at: new Date().toISOString(), ...(kind === "reentry" ? { reentry: true } : {}) });
        if (kind === "reentry") this.metrics.event("hand-reentered", { table: key, handId: reply.json.handId, slot: this.slotNumber });
        if (kind === "draw") this.metrics.event("planned-draw", { table: key, handId: reply.json.handId });
        // The corner case of the pause: the players go on saving hands while the organizer has the clock paused.
        if (this.pauseRounds.has(roundId) && (await this.tournament.isPaused(roundId))) {
          this.metrics.event("hand-while-paused", { table: key, handId: reply.json.handId, atMs: Date.now() });
        }
        if (kind === "offline") await this.loseTheNetwork(key, path, body, reply.json.handId, roundId, table.tableId);
        if (kind === "extra") {
          const end = await this.tournament.matchEnd(roundId, this.matchMinutes);
          this.metrics.event("extra-time-hand", { table: key, handId: reply.json.handId, savedAt: Date.now(), matchEnd: end });
        }
        return "ok";
      }
      if (reply.status !== 409 || !/changed/i.test(reply.json?.message ?? "")) return this.refusal(reply, key);
      this.metrics.event("version-conflict", { table: key, attempt });
    }
    return "wait";
  }

  /**
   * The corner case of the Stop: the organizer stopped the round and the server closed the table, but a player of it
   * still sends a hand, as a phone that did not look again. The server must refuse it.
   */
  async handAfterTheStop(roundId, table, key, seats) {
    this.stopTables.delete(key);
    const path = `/player/tournaments/${this.tournament.id}/tables/${roundId}/${table.tableId}/hands`;
    const hand = randomHand(seats ?? table.playerIds.map(String));
    const token = await this.session.bearer();
    const preview = await this.api.call("POST", `${path}/preview`, { token, body: hand, label: "POST hand preview (after the Stop)" });
    const reply = preview.status !== 200 ? preview
      : await this.api.call("POST", path, { token, body: { ...hand, expectedVersion: Number(preview.json?.tableVersion ?? 0) }, label: "POST hand (after the Stop)" });
    if (reply.status === 200) this.handLog.push({ table: key, handId: reply.json.handId, hand, ms: Math.round(reply.ms), at: new Date().toISOString() });
    this.metrics.event("hand-after-stop", { table: key, status: reply.status, message: reply.json?.message ?? "", atMs: Date.now() });
  }

  /**
   * The answer of the hand never reached the phone: it lost the network. The phone is away for a while, and then it
   * sends the same hand again, with the version of then, as the app does after a failed send. The server must refuse
   * it (the table changed), so the hand is not saved twice, and the phone then shows the hand from the table.
   */
  async loseTheNetwork(key, path, body, handId, roundId, tableId) {
    const away = this.offlineTables.get(key)?.seconds ?? 60;
    this.metrics.event("offline-lost-reply", { table: key, handId, slot: this.slotNumber, seconds: away });
    await sleep(away * 1_000);
    const again = await this.api.call("POST", path, { token: await this.session.bearer(), body, label: "POST hand (after the network)" });
    const data = await this.readTable(roundId, tableId);
    const shown = (data?.hands ?? []).filter((hand) => hand.isDone && Number(hand.handId) === Number(handId)).length;
    this.metrics.event("offline-back", { table: key, handId, slot: this.slotNumber, status: again.status, message: again.json?.message ?? "", shown });
  }

  /** The second player of the table sends the same hand at the same time, from another account. */
  async raceSave(key, path, body) {
    const other = this.raceSessions.get(key);
    if (other == null) return { status: -1, json: null, ms: 0 };
    return this.api.call("POST", path, { token: await other.bearer(), body, label: "POST hand (race)" });
  }

  refusal(reply, key) {
    const message = reply.json?.message ?? "";
    if (/already completed|All hands of this table are done|tournament is completed/i.test(message)) return "done";
    if (WAIT_MESSAGES.some((text) => message.includes(text))) return "wait";
    this.metrics.event("hand-refused", { table: key, status: reply.status, message });
    return "wait";
  }

  /** The other players: they look at the table when the manifest says it changed. */
  async watch(roundId, table, key) {
    while (Date.now() < this.until && !this.stopped()) {
      const manifest = await this.manifest();
      const version = manifest?.tableVersions?.[key];
      if (manifest == null || version !== this.lastManifest) {
        this.lastManifest = version;
        const data = await this.readTable(roundId, table.tableId);
        this.noteHands(key, data);
        this.noteWatchedCorrections(key, data);
        if (data?.table?.isCompleted) return;
      }
      if ((await this.tournament.currentRound()) !== roundId) return;
      const offline = this.offlineTables.get(key);
      const seenHere = (data) => (data?.hands ?? []).filter((hand) => hand.isDone).length;
      if (offline != null && !this.backgroundDone.has(key) && this.watcherOf(table) === this.playerId) {
        const before = seenHere(await this.readTable(roundId, table.tableId));
        if (before >= offline.afterHands) {
          // The corner case: this phone goes to the background (no polls), and then it comes back to the table.
          this.backgroundDone.add(key);
          const away = offline.seconds ?? 60;
          this.metrics.event("background", { table: key, slot: this.slotNumber, seconds: away, hands: before });
          await sleep(away * 1_000);
          const data = await this.readTable(roundId, table.tableId);
          this.noteHands(key, data);
          this.metrics.event("background-back", { table: key, slot: this.slotNumber, before, after: seenHere(data) });
        }
      }
      await sleep(this.pollMs);
    }
  }

  /** The phone of the table that goes to the background: the highest slot, as the scorer is the lowest. */
  watcherOf(table) {
    return Math.max(...table.playerIds);
  }
}

/** A person in the room or at home who follows the tournament: lists, tables and rankings, without an account. */
async function spectate(context, index) {
  const { api, tournament } = context;
  await sleep(index * 300);
  let lastRevision = null;
  let lastPlayers = 0;
  while (Date.now() < context.until && !context.stopped()) {
    const reply = await api.call("GET", `/public/tournaments/${tournament.id}/sync/manifest`, { label: "GET manifest (spectator)" });
    const revision = JSON.stringify(reply.json?.tableVersions ?? {});
    if (reply.status === 200 && revision !== lastRevision) {
      lastRevision = revision;
      const roundId = await tournament.currentRound();
      await api.call("GET", `/public/tournaments/${tournament.id}/rounds/${roundId}/tables-with-hands`, { label: "GET round tables (spectator)" });
    }
    if (Date.now() - lastPlayers > 120_000) {
      lastPlayers = Date.now();
      await api.call("GET", `/public/tournaments/${tournament.id}/players`, { label: "GET players (spectator)" });
      await api.call("GET", `/public/tournaments/${tournament.id}/teams`, { label: "GET teams (spectator)" });
    }
    await sleep(context.spectatorPollMs);
  }
}

/**
 * A phone in the venue, on the same network as the others: it sits at a table and polls like the app on the table
 * screen (the manifest every 5 seconds, the table when it changes). Many of them from one process share one address.
 */
async function venuePhone(context, index) {
  const { api, tournament } = context;
  await sleep((index * 5_000) / Math.max(1, context.venuePhones));
  let lastVersion = null;
  while (Date.now() < context.until && !context.stopped()) {
    const roundId = await tournament.currentRound();
    const tables = await tournament.tables(roundId);
    const table = tables.length === 0 ? null : tables[index % tables.length];
    const reply = await api.call("GET", `/public/tournaments/${tournament.id}/sync/manifest`, { label: "GET manifest (venue)" });
    const version = table == null ? null : reply.json?.tableVersions?.[`${roundId}_${table.tableId}`];
    if (reply.status === 200 && table != null && version !== lastVersion) {
      lastVersion = version;
      await api.call("GET", `/public/tournaments/${tournament.id}/tables/${roundId}/${table.tableId}`, { label: "GET table (venue)" });
    }
    await sleep(context.pollMs);
  }
}

export async function findTournament(api, nameOrId) {
  const reply = await api.call("GET", "/public/tournaments", { label: "GET tournaments" });
  if (reply.status !== 200) throw new Error(`Listing the tournaments failed: ${reply.status} ${reply.json?.message ?? ""}`);
  const found = reply.json.tournaments.find((item) => item.id === nameOrId || item.name === nameOrId);
  if (found == null) throw new Error(`No public tournament "${nameOrId}"`);
  return found.id;
}

/**
 * Runs the bots and the spectators until [until] or until [stopped] says so. Returns the report.
 * options: api, tournamentId, slots, accountSuffix(slot), password, baseEmail, spectators, until, stopped,
 * handSeconds [min, max], penaltyRate, lateTables, raceTables, pollMs, spectatorPollMs, verbose, the corner cases of
 * [planOptions], and the waits that the tests make short: signInGapMs, clockCacheMs, holdMs, waitMs, extraTimeAfterMs.
 */
export async function runBots(options) {
  const startedAt = new Date().toISOString();
  const metrics = options.metrics ?? new Metrics();
  const api = options.api ?? new Api(options.apiBase ?? STAGING_API, metrics);
  const tournament = new Tournament(api, options.tournamentId, options.clockCacheMs, metrics);
  const ownSlots = new Set(options.slots);
  const context = {
    api, metrics, tournament, ownSlots,
    until: options.until,
    stopped: options.stopped ?? (() => false),
    handSeconds: options.handSeconds ?? [60, 90],
    penaltyRate: options.penaltyRate ?? 0.05,
    // The app checks the own table every 2 s while its round is in play (PollSchedule.OWN_TABLE_IN_PLAY).
    pollMs: options.pollMs ?? 2_000,
    spectatorPollMs: options.spectatorPollMs ?? 15_000,
    venuePhones: options.venuePhones ?? 0,
    verbose: options.verbose ?? false,
    lateTables: new Set(options.lateTables ?? []),
    // The tables where the editor corrects the newest hand: the table stops after [minHands] hands and waits for the
    // editor, as players who called the organization do. Without the wait the next hand would make it an older one.
    holdTables: new Map(Object.entries(options.holdTables ?? {})),
    holdUntil: new Map(),
    // How long such a table waits for the editor at most.
    holdMs: options.holdMs ?? 4 * 60_000,
    // How long the bot waits after an answer that asks it to wait (the seats refused, the hand entry closed).
    waitMs: options.waitMs ?? 10_000,
    // The slow table of the extra time saves its last hand this long after the gong.
    extraTimeAfterMs: options.extraTimeAfterMs ?? 20_000,
    // How long the scorer of a late table arrives after the gong.
    lateMs: options.lateMs ?? 120_000,
    raceTables: new Set(options.raceTables ?? []),
    penaltyDrawTables: new Map(Object.entries(options.penaltyDrawTables ?? {})),
    penaltyDrawDone: new Set(),
    seatChangeTables: new Map((options.seatChangeTables ?? []).map((key) => [key, "before"])),
    extraTimeTables: new Map(Object.entries(options.extraTimeTables ?? {})),
    extraWaiting: new Set(),
    abandonTables: new Map(Object.entries(options.abandonTables ?? {})),
    // The tables of manual totals: the scorer saves the seats and the players leave; an editor types the totals.
    totalsOnlyTables: new Set(options.totalsOnlyTables ?? []),
    // The tables where a phone loses the network: the answer of a hand never arrives, the phone is away for a while
    // and then sends the same hand again. Another phone of the table goes to the background as long.
    offlineTables: new Map(Object.entries(options.offlineTables ?? {})),
    offlineDone: new Set(),
    backgroundDone: new Set(),
    // The table of round 1 whose players finish level: it plays only draws, and so do their tables of the next rounds.
    tieTable: options.tieTable ?? null,
    // The round whose clock the organizer pauses, and the table whose scorer tries a hand after the Stop.
    pauseRounds: new Set(options.pauseRounds ?? []),
    stopTables: new Set(options.stopTables ?? []),
    matchMinutes: options.matchMinutes ?? null,
    raceSessions: new Map(),
    raced: new Set(),
    scorerOf: new Map(),
    sentAt: new Map(),
    handLog: [],
    delays: [],
  };

  // The sign-in route takes 30 calls a minute from one address, so the bots sign in one every 2.5 seconds.
  const bots = [];
  for (const slot of options.slots) {
    const email = options.emailOf ? options.emailOf(slot) : rehearsalEmail(options.baseEmail, options.accountSuffix(slot));
    const session = new Session(api, email, options.password);
    if (await session.signIn()) bots.push(new PlayerBot(context, slot, session));
    else metrics.event("sign-in-failed", { slot });
    await sleep(options.signInGapMs ?? 2_500);
  }
  console.log(`${bots.length} of ${options.slots.length} bots signed in.`);
  const bySlot = new Map(bots.map((bot) => [bot.slotNumber, bot]));
  // For a race, the second lowest slot of the table sends the same hand as the scorer.
  context.raceSessionFor = (key, table) => {
    const second = [...table.playerIds].sort((a, b) => a - b)[1];
    const bot = bySlot.get(second);
    if (bot) context.raceSessions.set(key, bot.session);
  };
  if (context.raceTables.size > 0) {
    for (const key of context.raceTables) {
      const [roundId, tableId] = key.split("_").map(Number);
      const table = (await tournament.tables(roundId)).find((item) => item.tableId === tableId);
      if (table) context.raceSessionFor(key, table);
    }
  }

  const spectators = [
    ...Array.from({ length: options.spectators ?? 0 }, (_, index) => spectate(context, index)),
    ...Array.from({ length: context.venuePhones }, (_, index) => venuePhone(context, index)),
  ];
  await Promise.all([...bots.map((bot) => bot.run().catch((error) => metrics.event("bot-crashed", { slot: bot.slotNumber, error: String(error) }))), ...spectators]);

  return {
    tournamentId: options.tournamentId,
    slots: options.slots,
    signedIn: bots.length,
    startedAt,
    finishedAt: new Date().toISOString(),
    hands: context.handLog,
    handDelayMs: { count: context.delays.length, p50: percentile(context.delays, 50), p95: percentile(context.delays, 95) },
    ...metrics.summary(),
  };
}

export function markdownSummary(title, report) {
  const lines = [`### ${title}`, "", `Bots signed in: ${report.signedIn} of ${report.slots.length}. Hands saved: ${report.hands.length}.`];
  if (report.handDelayMs.count > 0) {
    lines.push(`A hand reached the other bots of the table in p50 ${report.handDelayMs.p50} ms, p95 ${report.handDelayMs.p95} ms.`);
  }
  lines.push("", "| Request | Count | Statuses | p50 ms | p95 ms | max ms |", "| --- | --- | --- | --- | --- | --- |");
  for (const [label, entry] of Object.entries(report.labels).sort()) {
    const statuses = Object.entries(entry.statuses).map(([status, count]) => `${status}: ${count}`).join(", ");
    lines.push(`| ${label} | ${entry.count} | ${statuses} | ${entry.p50} | ${entry.p95} | ${Math.round(entry.max)} |`);
  }
  const refusals = Object.entries(report.refusals);
  if (refusals.length > 0) {
    lines.push("", "Answers other than OK:", "");
    for (const [text, count] of refusals.sort((a, b) => b[1] - a[1])) lines.push(`- ${count} × ${text}`);
  }
  return lines.join("\n");
}

export function writeReport(path, title, report) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify(report, null, 2));
  const markdown = markdownSummary(title, report);
  console.log(markdown);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n\n`);
}

/**
 * Looks at the last round every 30 s with a plain read that the metrics do not count. [done] turns true a minute after
 * all its tables are completed, so the phones see the last saves and the corrections first.
 */
function watchTheLastRound(base, tournamentId, lastRound) {
  const state = { done: false, completedAt: null };
  const look = async () => {
    try {
      const response = await fetch(`${base.replace(/\/$/, "")}/public/tournaments/${tournamentId}/rounds/${lastRound}/tables-with-hands`);
      if (!response.ok) return;
      const tables = (await response.json()).tables ?? [];
      const completed = tables.length > 0 && tables.every((item) => item.table?.isCompleted);
      if (!completed) state.completedAt = null;
      else state.completedAt ??= Date.now();
      if (state.completedAt != null && Date.now() - state.completedAt >= 60_000) {
        if (!state.done) console.log(`Every table of round ${lastRound} is completed: the bots stop.`);
        state.done = true;
      }
    } catch {
      // The next look tries again.
    }
  };
  const timer = setInterval(look, 30_000);
  state.stop = () => clearInterval(timer);
  return state;
}

/**
 * The options of [runBots] for the corner cases of the plan of a game day (plan.cornerCases, docs/scenarios.md), and
 * the late and race tables of --late-tables and --race-tables.
 */
export function planOptions(plan, args = {}) {
  return {
    // The late tables of the plan of a game day, and those of --late-tables.
    lateTables: [
      ...String(args["late-tables"] ?? "").split(",").filter(Boolean),
      ...(plan?.cornerCases ?? []).filter((item) => item.kind === "late").map((item) => `${item.round}_${item.table}`),
    ],
    lateMs: ((plan?.cornerCases ?? []).find((item) => item.kind === "late")?.minutes ?? 2) * 60_000,
    holdTables: Object.fromEntries((plan?.cornerCases ?? [])
      .filter((item) => (item.kind === "delete" || item.kind === "edit") && item.which === "last")
      .map((item) => [`${item.round}_${item.table}`, item.minHands])),
    raceTables: [
      ...String(args["race-tables"] ?? "").split(",").filter(Boolean),
      ...(plan?.cornerCases ?? []).filter((item) => item.kind === "race").map((item) => `${item.round}_${item.table}`),
    ],
    penaltyDrawTables: Object.fromEntries((plan?.cornerCases ?? []).filter((item) => item.kind === "penalty-draw")
      .map((item) => [`${item.round}_${item.table}`, { afterHands: item.afterHands, penalties: item.penalties }])),
    seatChangeTables: (plan?.cornerCases ?? []).filter((item) => item.kind === "seat-change").map((item) => `${item.round}_${item.table}`),
    extraTimeTables: Object.fromEntries((plan?.cornerCases ?? []).filter((item) => item.kind === "extra-time")
      .map((item) => [`${item.round}_${item.table}`, { afterHands: item.afterHands }])),
    abandonTables: Object.fromEntries((plan?.cornerCases ?? []).filter((item) => item.kind === "abandoned")
      .map((item) => [`${item.round}_${item.table}`, item.afterHands])),
    totalsOnlyTables: (plan?.cornerCases ?? []).filter((item) => item.kind === "manual-totals").map((item) => `${item.round}_${item.table}`),
    offlineTables: Object.fromEntries((plan?.cornerCases ?? []).filter((item) => item.kind === "offline")
      .map((item) => [`${item.round}_${item.table}`, { afterHands: item.afterHands, seconds: item.seconds }])),
    tieTable: (plan?.cornerCases ?? []).filter((item) => item.kind === "tie").map((item) => `${item.round}_${item.table}`)[0] ?? null,
    pauseRounds: (plan?.cornerCases ?? []).filter((item) => item.kind === "pause").map((item) => item.round),
    stopTables: (plan?.cornerCases ?? []).filter((item) => item.kind === "stop").map((item) => `${item.round}_${item.table}`),
    matchMinutes: plan?.matchDurationMinutes ?? null,
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const password = process.env.REHEARSAL_PASSWORD ?? "";
  const baseEmail = process.env.STAGING_ADMIN_EMAIL ?? "";
  // A game day scenario (docs/scenarios.md): the plan names the tournament and the email of each slot.
  const plan = args.plan ? JSON.parse(fs.readFileSync(String(args.plan), "utf8")) : null;
  if (plan) args.tournament ??= plan.name;
  const emailOf = plan ? (slot) => plan.slots.find((item) => item.slot === slot)?.email : null;
  // Phones that only read need no account.
  if (parseRange(args.slots).length > 0 && (!password || (!baseEmail && !plan))) throw new Error("Set REHEARSAL_PASSWORD and STAGING_ADMIN_EMAIL.");
  const metrics = new Metrics();
  const api = new Api(args.api ?? STAGING_API, metrics);
  const tournamentId = await findTournament(api, args.tournament);
  const until = args.until ? Date.parse(args.until) : Date.now() + Number(args.minutes ?? 150) * 60_000;
  const [handMin, handMax] = String(args["hand-seconds"] ?? "60-90").split("-").map(Number);
  // In a game day the bots stop a minute after every table of the last round is completed, not at the deadline.
  const finished = plan ? watchTheLastRound(args.api ?? STAGING_API, tournamentId, plan.rounds) : null;
  const report = await runBots({
    api, metrics, tournamentId, baseEmail, password,
    slots: parseRange(args.slots),
    accountSuffix: (slot) => `j${String(slot).padStart(2, "0")}`,
    emailOf,
    spectators: Number(args.spectators ?? 0),
    venuePhones: Number(args["venue-phones"] ?? 0),
    until,
    handSeconds: [handMin, handMax ?? handMin],
    penaltyRate: Number(args["penalty-rate"] ?? 0.05),
    ...planOptions(plan, args),
    verbose: Boolean(args.verbose),
    stopped: finished ? () => finished.done : undefined,
  });
  finished?.stop();
  writeReport(args.report ?? "out/bots.json", args.title ?? "Bots", report);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
