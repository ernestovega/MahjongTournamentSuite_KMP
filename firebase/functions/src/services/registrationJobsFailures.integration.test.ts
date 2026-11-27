import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db } from "../firebase";
import { setMailTransportForTests } from "./mailService";
import { runRegistrationsHour } from "./registrationJobs";

/**
 * The hourly job of the registrations when things fail: a tournament that cannot load and a mailbox that refuses
 * every email. The job goes on with the other tournaments and counts no email as sent.
 */
describe("the hourly job of the registrations when things fail", () => {
  it("skips a broken tournament and counts no reminder or report when the mailbox fails", async () => {
    const broken = db.doc(`tournaments/jobs-broken-${Date.now()}`);
    await broken.set({ drawPending: true });
    const tournament = db.doc(`tournaments/jobs-mailbox-${Date.now()}`);
    await tournament.set({
      name: "Jobs", isTeams: true, drawPending: true, timeZone: "UTC", contactEmail: "org@example.test", hostCountry: "ES",
      registration: { status: "OPEN", closesOn: "2030-06-05" },
    });
    await tournament.collection("registrations").doc("07760001").set({ status: "CONFIRMED", order: 1, email: "ana@example.test", teamId: "small" });
    await tournament.collection("registrations").doc("07760002").set({ status: "CONFIRMED", order: 2, email: "" });

    setMailTransportForTests(async () => {
      throw new Error("mailbox down");
    });
    try {
      // A Sunday at 21:30, three days before the close: the team reminder and the weekly report are due.
      const results = await runRegistrationsHour(new Date(Date.UTC(2030, 5, 2, 21, 30)));
      assert.equal(results.some((result) => result.tournamentId === broken.id), false);
      const mine = results.find((result) => result.tournamentId === tournament.id);
      assert.deepEqual([mine?.teamReminders, mine?.weeklyReport], [0, false]);
      assert.equal((await tournament.get()).get("registration.teamReminderSentFor"), "2030-06-05");

      // At three at night the job also purges the registrations of finished tournaments.
      assert.ok(Array.isArray(await runRegistrationsHour(new Date(Date.UTC(2030, 5, 3, 3, 10)))));
    } finally {
      setMailTransportForTests(null);
    }
  });
});
