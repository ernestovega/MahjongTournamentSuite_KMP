import assert from "node:assert/strict";
import { afterEach, describe, it, mock } from "node:test";

import * as matchTimeCloseService from "./services/matchTimeCloseService";
import * as registrationHourRelay from "./services/registrationHourRelay";
import { closeRoundTablesTask, registrationsHourly } from "./index";

type Runnable = { run: (input: unknown) => Promise<unknown> };

/** The handlers of the task and the schedule, run as Cloud Functions would run them. */
describe("background functions", () => {
  afterEach(() => mock.restoreAll());

  it("closes the round of a task, and skips a task without a tournament or a round", async () => {
    const closing = mock.method(matchTimeCloseService, "closeRoundTables", async () => ({ tournaments: 1, closed: 2 }));
    const task = closeRoundTablesTask as unknown as Runnable;
    await task.run({ data: { tournamentId: "t1", roundId: 3 } });
    await task.run({ data: { tournamentId: "t1" } });
    await task.run({ data: { roundId: 3 } });
    assert.deepEqual(closing.mock.calls.map((call) => call.arguments.slice(0, 2)), [["t1", 3]]);
  });

  it("logs what the hourly run of the registrations did, and nothing when it did nothing", async () => {
    const logged: unknown[] = [];
    mock.method(console, "log", (...args: unknown[]) => { logged.push(args); });
    const answers: unknown[] = [{ results: [{ tournamentId: "t1", closed: true }] }, { results: [] }, undefined];
    mock.method(registrationHourRelay, "callRegistrationsHour", async () => answers.shift());
    const schedule = registrationsHourly as unknown as Runnable;
    for (let run = 0; run < 3; run++) await schedule.run({});
    assert.equal(logged.length, 1);
    assert.match(String((logged[0] as unknown[])[1]), /t1/);
  });
});
