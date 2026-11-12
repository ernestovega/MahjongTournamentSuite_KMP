import { db, firebaseProjectId } from "../firebase";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "../services/dataVersionsService";
import { plannedRoundClocks } from "../services/matchTime";
import { roundCloseTasks, scheduleRoundCloses } from "../services/matchTimeTaskService";
import { runScript } from "./scriptRun";

const applyChanges = process.argv.includes("--apply");

/**
 * Moves the stored tournaments to the clocks of the Timer. A round now starts only with the Play of the Timer, so a
 * round whose planned start is past and that has no clock gets one at its planned start: a round in play stays in
 * play and a played round stays over. The rounds to come get nothing and wait for their Play. Completed tournaments
 * are left as they are. It plans the close of the rounds still in play. It is safe to run again: a round with a
 * clock keeps it.
 */
async function backfill(): Promise<{ tournaments: number; rounds: number }> {
  const nowMs = Date.now();
  const snapshot = await db.collection("tournaments").get();
  console.log(`${applyChanges ? "Applying" : "Previewing"} the round clocks for ${firebaseProjectId}: ${snapshot.size} tournaments.`);
  let tournaments = 0;
  let rounds = 0;
  for (const document of snapshot.docs) {
    const data = document.data();
    const clocks = plannedRoundClocks(data, nowMs);
    if (clocks.size === 0) continue;
    tournaments += 1;
    rounds += clocks.size;
    console.log(`${document.id}: rounds ${[...clocks.keys()].join(", ")} start at their planned time.`);
    if (!applyChanges) continue;
    const update: Record<string, unknown> = { ...tournamentVersionUpdate("rounds") };
    for (const [roundId, clock] of clocks) update[`roundClocks.${roundId}`] = clock;
    await document.ref.update(update);
    const stored = { ...data, roundClocks: { ...(data.roundClocks as object | undefined), ...Object.fromEntries(clocks) } };
    const expected = roundCloseTasks(document.id, stored, nowMs).length;
    const planned = await scheduleRoundCloses(document.id, stored, nowMs);
    if (planned < expected) {
      console.warn(`${document.id}: planned ${planned} of ${expected} round closes. Set EMA_FIREBASE_SERVICE_ACCOUNT to the service account of the functions, or save the settings of the tournament in the admin app, which plans them.`);
    }
  }
  if (applyChanges && tournaments > 0) await bumpGlobalDataVersion("tournaments");
  console.log(`${applyChanges ? "Wrote" : "Would write"} ${rounds} clocks in ${tournaments} tournaments.`);
  if (!applyChanges && rounds > 0) console.log("Run again with --apply to write them.");
  return { tournaments, rounds };
}

runScript("backfillRoundClocks", backfill, applyChanges);
