import { db, firebaseProjectId } from "../firebase";
import { runTournamentIntegrityCheck } from "../services/tournamentIntegrityService";

/**
 * Checks tournaments for wrong or odd data. It only reads.
 * Usage: npm run check:integrity [-- <tournamentId> ...]. Without ids it checks every tournament.
 * Add --json to print the reports as JSON. Add --quiet to hide info findings.
 */
async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const asJson = args.includes("--json");
  const quiet = args.includes("--quiet");
  const requested = args.filter((arg) => !arg.startsWith("--"));
  const ids = requested.length > 0 ? requested : (await db.collection("tournaments").get()).docs.map((doc) => doc.id);
  if (!asJson) console.log(`Checking ${ids.length} tournaments in ${firebaseProjectId}.`);
  const reports = [];
  for (const id of ids) {
    // eslint-disable-next-line no-await-in-loop
    const report = await runTournamentIntegrityCheck(id);
    reports.push(report);
    if (asJson) continue;
    const { errors, warnings, infos } = report.counts;
    console.log(`\n${id}: ${errors} errors, ${warnings} warnings, ${infos} infos`);
    report.findings.filter((finding) => !(quiet && finding.severity === "info")).forEach((finding) => {
      console.log(`  [${finding.severity}] ${finding.code}${finding.where ? ` (${finding.where})` : ""}: ${finding.message}`);
    });
  }
  if (asJson) console.log(JSON.stringify(reports, null, 2));
  if (reports.some((report) => report.counts.errors > 0)) process.exitCode = 2;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
