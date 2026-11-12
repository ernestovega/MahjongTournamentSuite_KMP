import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";

import { db } from "../firebase";
import { readGlobalTournamentsRevision, readTournament, seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { setRoundCloseEnqueuer, type RoundCloseTask } from "../services/matchTimeTaskService";
import { getRoundClocks, updateRoundClock } from "../services/roundClockService";
import { HttpError } from "./httpError";

const MIN = 60_000;

/** A tournament of 2 rounds whose match lasts 120 minutes and stops at the end. */
async function seedClockTournament(): Promise<string> {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }] });
  await db.doc(`tournaments/${tournamentId}`).update({
    numRounds: 2,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: "STOP",
    matchExtraMinutes: 15,
    roundSchedules: [{ roundId: 1, date: "2026-11-12", startTime: "10:00" }, { roundId: 2, date: "2026-11-12", startTime: "13:00" }],
  });
  return tournamentId;
}

describe("the clocks of the rounds", () => {
  let tasks: Array<{ tournamentId: string; task: RoundCloseTask }> = [];

  before(() => setRoundCloseEnqueuer(async (tournamentId, task) => {
    tasks.push({ tournamentId, task });
  }));
  after(() => setRoundCloseEnqueuer());
  beforeEach(() => {
    tasks = [];
  });

  it("starts a round at the time of the server and plans its close", async () => {
    const tournamentId = await seedClockTournament();
    const revision = await readGlobalTournamentsRevision();
    const now = Date.now();

    const answer = await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "start", agoMs: 0 }, actor: null, nowMs: now });

    assert.deepEqual(answer.roundClocks, [{ roundId: 1, startedAtMs: now, pausedAtMs: null, pausedMs: 0, adjustMs: 0 }]);
    assert.equal(answer.serverNowMs, now);
    const stored = await readTournament(tournamentId);
    assert.deepEqual((stored.roundClocks as Record<string, unknown>)["1"], { startedAtMs: now, pausedAtMs: null, pausedMs: 0, adjustMs: 0 });
    assert.ok(await readGlobalTournamentsRevision() > revision);
    assert.deepEqual(tasks.map(({ task }) => [task.roundId, task.at.getTime()]), [[1, now + 120 * MIN]]);
  });

  it("keeps the first Play when two Timers press it", async () => {
    const tournamentId = await seedClockTournament();
    const now = Date.now();

    await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "start", agoMs: 0 }, actor: null, nowMs: now });
    const second = await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "start", agoMs: 0 }, actor: null, nowMs: now + 3000 });

    assert.equal(second.roundClocks[0].startedAtMs, now);
    // The second Play changed nothing, so it planned nothing.
    assert.equal(tasks.length, 1);
  });

  it("pauses, resumes and moves the close of the round", async () => {
    const tournamentId = await seedClockTournament();
    const now = Date.now();
    await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "start", agoMs: 0 }, actor: null, nowMs: now });

    await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "pause", agoMs: 0 }, actor: null, nowMs: now + 30 * MIN });
    const resumed = await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "resume", agoMs: 0 }, actor: null, nowMs: now + 40 * MIN });

    assert.equal(resumed.roundClocks[0].pausedMs, 10 * MIN);
    // The pause plans nothing. The resume plans the close 10 minutes later.
    assert.deepEqual(tasks.map(({ task }) => task.at.getTime()), [now + 120 * MIN, now + 130 * MIN]);
  });

  it("asks for a confirmation before resetting a round with saved hands", async () => {
    const tournamentId = await seedClockTournament();
    await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "start", agoMs: 0 }, actor: null });
    await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({ hasProgress: true });
    await db.doc(`tournaments/${tournamentId}/tables/1_1/hands/1`).update({ isDone: true, playerWinnerId: "1", handScore: "8" });

    await assert.rejects(
      updateRoundClock({ tournamentId, roundId: 1, action: { kind: "reset" }, actor: null }),
      (error: unknown) => error instanceof HttpError && error.status === 409
        && (error.details as { code?: string } | undefined)?.code === "ROUND_HAS_HANDS",
    );
    const reset = await updateRoundClock({ tournamentId, roundId: 1, action: { kind: "reset" }, force: true, actor: null });

    assert.deepEqual(reset.roundClocks, []);
    assert.deepEqual((await readTournament(tournamentId)).roundClocks, {});
    // The hands stay.
    assert.equal((await db.doc(`tournaments/${tournamentId}/tables/1_1/hands/1`).get()).get("isDone"), true);
  });

  it("resets a round without hands at once", async () => {
    const tournamentId = await seedClockTournament();
    await updateRoundClock({ tournamentId, roundId: 2, action: { kind: "start", agoMs: 0 }, actor: null });

    const reset = await updateRoundClock({ tournamentId, roundId: 2, action: { kind: "reset" }, actor: null });

    assert.deepEqual(reset.roundClocks, []);
  });

  it("refuses an unknown round, a completed tournament and a pause before the start", async () => {
    const tournamentId = await seedClockTournament();
    const status = (status: number) => (error: unknown) => error instanceof HttpError && error.status === status;

    await assert.rejects(updateRoundClock({ tournamentId, roundId: 3, action: { kind: "start", agoMs: 0 }, actor: null }), status(404));
    await assert.rejects(updateRoundClock({ tournamentId, roundId: 1, action: { kind: "pause", agoMs: 0 }, actor: null }), status(409));
    await db.doc(`tournaments/${tournamentId}`).update({ isCompleted: true });
    await assert.rejects(updateRoundClock({ tournamentId, roundId: 1, action: { kind: "start", agoMs: 0 }, actor: null }), status(409));
    await assert.rejects(getRoundClocks("missing-tournament"), status(404));
  });
});

describe("the clock routes", () => {
  let server: TestServer;
  let tournamentId = "";
  let otherTournamentId = "";
  let editor: TestAccount;
  let player: TestAccount;

  const call = async (method: string, path: string, token: string | null, body?: unknown) => {
    const response = await fetch(server.baseUrl + path, {
      method,
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { status: response.status, cache: response.headers.get("cache-control") ?? "", body: await response.json() as any };
  };

  before(async () => {
    requireAuthEmulator();
    setRoundCloseEnqueuer(async () => undefined);
    server = await startServer();
    tournamentId = await seedClockTournament();
    otherTournamentId = await seedClockTournament();
    editor = await createAccount({ role: "EDITOR", memberOf: [tournamentId] });
    player = await createAccount({ role: "PLAYER" });
  });
  after(async () => {
    setRoundCloseEnqueuer();
    await server.close();
  });

  it("lets an editor of the tournament press Play, and anyone read the clocks", async () => {
    const started = await call("POST", `/tournaments/${tournamentId}/rounds/1/clock`, editor.token, { action: "start", agoSeconds: 60 });

    assert.equal(started.status, 200);
    assert.equal(started.body.roundClocks[0].roundId, 1);
    assert.ok(Math.abs(started.body.serverNowMs - started.body.roundClocks[0].startedAtMs - 60_000) < 1000);
    const read = await call("GET", `/tournaments/${tournamentId}/round-clocks`, editor.token);
    assert.equal(read.body.roundClocks.length, 1);
    const open = await call("GET", `/public/tournaments/${tournamentId}/round-clocks`, null);
    assert.equal(open.status, 200);
    assert.equal(open.body.roundClocks.length, 1);
    assert.match(open.cache, /s-maxage=3/);
    // The tournament carries the clocks too.
    const tournament = await call("GET", `/public/tournaments/${tournamentId}`, null);
    assert.equal(tournament.body.roundClocks[0].startedAtMs, started.body.roundClocks[0].startedAtMs);
  });

  it("refuses a player, an editor of another tournament and an unknown action", async () => {
    assert.equal((await call("POST", `/tournaments/${tournamentId}/rounds/1/clock`, player.token, { action: "start" })).status, 403);
    assert.equal((await call("POST", `/tournaments/${otherTournamentId}/rounds/1/clock`, editor.token, { action: "start" })).status, 403);
    assert.equal((await call("POST", `/tournaments/${tournamentId}/rounds/1/clock`, editor.token, { action: "stop" })).status, 400);
    assert.equal((await call("POST", `/tournaments/${tournamentId}/rounds/1/clock`, null, { action: "start" })).status, 401);
  });
});

describe("the clock routes cannot be tricked", () => {
  let server: TestServer;
  let tournamentId = "";
  let editor: TestAccount;

  const post = async (body: unknown, token: string | null = editor.token) => {
    const response = await fetch(`${server.baseUrl}/tournaments/${tournamentId}/rounds/1/clock`, {
      method: "POST",
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body),
    });
    return { status: response.status, body: await response.json() as any };
  };

  before(async () => {
    requireAuthEmulator();
    setRoundCloseEnqueuer(async () => undefined);
    server = await startServer();
    tournamentId = await seedClockTournament();
    editor = await createAccount({ role: "EDITOR", memberOf: [tournamentId] });
  });
  after(async () => {
    setRoundCloseEnqueuer();
    await server.close();
  });

  it("takes the time of the server and ignores instants sent by the app", async () => {
    const before = Date.now();
    const started = await post({ action: "start", startedAtMs: 1, pausedMs: 999_999_999, adjustMs: 999_999_999 });

    assert.equal(started.status, 200);
    const clock = started.body.roundClocks[0];
    assert.ok(clock.startedAtMs >= before - 1000 && clock.startedAtMs <= Date.now());
    assert.equal(clock.pausedMs, 0);
    assert.equal(clock.adjustMs, 0);
    // The stored clock is the same as the answer.
    const stored = (await readTournament(tournamentId)).roundClocks as Record<string, { startedAtMs: number }>;
    assert.equal(stored["1"].startedAtMs, clock.startedAtMs);
  });

  it("refuses times that are not numbers, negative or too long", async () => {
    for (const body of [
      { action: "pause", agoSeconds: "abc" },
      { action: "pause", agoSeconds: -60 },
      { action: "resume", agoSeconds: "1e400" },
      { action: "setRemaining", remainingSeconds: -1 },
      { action: "setRemaining", remainingSeconds: 13 * 3600 },
      { action: "setRemaining", remainingSeconds: "ten" },
    ]) {
      assert.equal((await post(body)).status, 400, JSON.stringify(body));
    }
  });

  it("caps a late pause at 10 minutes, so the app cannot rewrite the past", async () => {
    const before = await readTournament(tournamentId);
    const startedAtMs = (before.roundClocks as Record<string, { startedAtMs: number }>)["1"].startedAtMs;
    const paused = await post({ action: "pause", agoSeconds: 24 * 3600 });

    assert.equal(paused.status, 200);
    const clock = paused.body.roundClocks[0];
    assert.ok(clock.pausedAtMs >= Math.max(startedAtMs, paused.body.serverNowMs - 10 * 60_000));
  });

  it("refuses a body without a token or with a forged token", async () => {
    assert.equal((await post({ action: "reset", force: true }, null)).status, 401);
    assert.equal((await post({ action: "reset", force: true }, "not-a-token")).status, 401);
    assert.equal((await readTournament(tournamentId)).roundClocks != null, true);
  });
});
