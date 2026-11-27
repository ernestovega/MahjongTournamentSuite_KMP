import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import * as functions from "firebase-admin/functions";

import { CLOSE_ROUND_TASK_NAME, scheduleRoundCloses, setRoundCloseEnqueuer } from "./matchTimeTaskService";

const now = Date.UTC(2026, 9, 10, 12, 30);
const running = (startedAtMs: number) => ({ startedAtMs, pausedAtMs: null, pausedMs: 0, adjustMs: 0 });
const tournament = {
  numRounds: 3, timeZone: "UTC", matchDurationMinutes: 120, matchEndMode: "STOP", matchExtraMinutes: 0,
  roundClocks: { 1: running(Date.UTC(2026, 9, 10, 12, 0)), 2: running(Date.UTC(2026, 9, 10, 12, 5)), 3: running(Date.UTC(2026, 9, 10, 12, 10)) },
};

/** The close of the rounds with Cloud Tasks faked: a new task, a task that exists already and a queue that fails. */
describe("plans of the round closes in Cloud Tasks", () => {
  it("counts the new and the repeated tasks, and goes on after a failed one", async () => {
    const queued: Array<{ name: string; id: string }> = [];
    const answers = [
      async () => undefined,
      async () => {
        throw Object.assign(new Error("exists"), { code: "functions/task-already-exists" });
      },
      async () => {
        throw Object.assign(new Error("quota"), { code: "functions/resource-exhausted" });
      },
    ];
    const getFunctions = mock.method(functions, "getFunctions", () => ({
      taskQueue: (name: string) => ({
        enqueue: async (_data: unknown, options: { id: string }) => {
          queued.push({ name, id: options.id });
          await answers.shift()!();
        },
      }),
    }) as never);
    process.env.CLOUD_TASKS_EMULATOR_HOST = "localhost:9499";
    setRoundCloseEnqueuer();
    try {
      assert.equal(await scheduleRoundCloses("t1", tournament, now), 2);
    } finally {
      delete process.env.CLOUD_TASKS_EMULATOR_HOST;
      getFunctions.mock.restore();
    }
    assert.equal(queued.length, 3);
    assert.match(queued[0].name, new RegExp(`/functions/${CLOSE_ROUND_TASK_NAME}$`));
  });

  it("never reaches Cloud Tasks with the Firestore emulator", async () => {
    const getFunctions = mock.method(functions, "getFunctions", () => {
      throw new Error("must not be called");
    });
    const emulator = process.env.FIRESTORE_EMULATOR_HOST;
    process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";
    try {
      assert.equal(await scheduleRoundCloses("t1", tournament, now, 1), 1);
    } finally {
      if (emulator == null) delete process.env.FIRESTORE_EMULATOR_HOST;
      else process.env.FIRESTORE_EMULATOR_HOST = emulator;
      getFunctions.mock.restore();
    }
  });
});
