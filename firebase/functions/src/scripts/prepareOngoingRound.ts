import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "../services/dataVersionsService";
import { closeRoundTables } from "../services/matchTimeCloseService";
import { planOngoingRound } from "../services/ongoingRound";
import { runScript } from "./scriptRun";

const applyChanges = process.argv.includes("--apply");
const tournamentId = process.argv.slice(2).find((arg) => !arg.startsWith("--")) ?? "madrid-open-2026";

/**
 * Leaves an ongoing tournament ready to try the tables: the first round after the last round with hands starts at
 * the time of the run, with no clock, so Play in the Timer starts it. Every planned time of the tournament (rounds,
 * agenda and event dates) moves by the same amount. The rounds before it get a clock that is over and the server
 * closes their tables with hands, as at the end of a round. Run it again whenever you need a fresh round.
 *
 * It previews by default. Add --apply to write.
 * Usage: node lib/scripts/prepareOngoingRound.js [tournamentId] [--apply]   (default madrid-open-2026)
 */
async function main(): Promise<{ tournamentId: string; round: number; closedTables: number }> {
  const ref = db.collection("tournaments").doc(tournamentId);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw new Error(`Tournament ${tournamentId} not found in ${firebaseProjectId}`);
  const tournament = snapshot.data() ?? {};
  const withHands = await ref.collection("tables").where("hasProgress", "==", true).select("roundId", "isCompleted").get();
  const roundsWithHands = new Set(withHands.docs.map((doc) => Number(doc.get("roundId"))));
  const nowMs = Date.now();
  const plan = planOngoingRound(tournament, roundsWithHands, nowMs);
  const start = plan.roundSchedules[plan.roundId - 1];
  const openBefore = withHands.docs.filter((doc) => doc.get("isCompleted") !== true && Number(doc.get("roundId")) < plan.roundId);

  console.log(`${applyChanges ? "Preparing" : "Previewing"} ${tournamentId} (${String(tournament.name ?? "")}) in ${firebaseProjectId}.`);
  console.log(`  rounds with hands: ${roundsWithHands.size === 0 ? "none" : [...roundsWithHands].sort((a, b) => a - b).join(", ")}`);
  console.log(`  round ${plan.roundId} starts ${start.date} ${start.startTime} (${String(tournament.timeZone)}), it was ${plan.previousStart.date} ${plan.previousStart.startTime}`);
  console.log(`  every planned time moves ${Math.round(plan.shiftMs / 60_000)} minutes; event ${plan.eventStartDate} to ${plan.eventEndDate}`);
  console.log(`  rounds 1-${plan.roundId - 1} get a clock that is over${plan.roundId === 1 ? " (none)" : ""}`);
  if (plan.clearedClocks.length > 0) console.log(`  rounds ${plan.clearedClocks.join(", ")} lose their clock`);
  console.log(`  open tables with hands before round ${plan.roundId}: ${openBefore.length} (the server closes them)`);
  if (!applyChanges) {
    console.log("Run again with --apply to write it.");
    return { tournamentId, round: plan.roundId, closedTables: 0 };
  }

  const update: Record<string, unknown> = {
    roundSchedules: plan.roundSchedules,
    agendaItems: plan.agendaItems,
    eventStartDate: plan.eventStartDate,
    eventEndDate: plan.eventEndDate,
    updatedAt: FieldValue.serverTimestamp(),
    ...tournamentVersionUpdate("rounds"),
  };
  for (const [roundId, clock] of plan.clocks) update[`roundClocks.${roundId}`] = clock;
  for (const roundId of plan.clearedClocks) update[`roundClocks.${roundId}`] = FieldValue.delete();
  // The write fails if someone changed the tournament since the read, so the plan never mixes with other changes.
  await ref.update(update, { lastUpdateTime: snapshot.updateTime });

  let closedTables = 0;
  const rounds = [...new Set(openBefore.map((doc) => Number(doc.get("roundId"))))].sort((a, b) => a - b);
  for (const roundId of rounds) closedTables += (await closeRoundTables(tournamentId, roundId)).closed;
  await bumpGlobalDataVersion("tournaments");
  console.log(`Done: round ${plan.roundId} is ready for Play; closed ${closedTables} tables.`);
  return { tournamentId, round: plan.roundId, closedTables };
}

runScript("prepareOngoingRound", main, applyChanges);
