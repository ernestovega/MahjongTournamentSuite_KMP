import { auth, db, firebaseProjectId } from "../firebase";
import { findEmaIdProblems, findPlayerClaimProblems } from "../services/emaIdAudit";
import { runTournamentIntegrityCheck } from "../services/tournamentIntegrityService";

/**
 * Checks the stored data for wrong or odd values. It only reads.
 * - Tournaments: slots, teams, tables (players of one team together, players who meet twice) and hands.
 * - Accounts: every EMA id belongs to one account, the index agrees with the profiles, and the player capability goes
 *   with the EMA id.
 * Usage: npm run check:integrity [-- <tournamentId> ...]. Without ids it checks every tournament and the accounts;
 * with ids only those tournaments. Add --json to print the reports as JSON. Add --quiet to hide info findings.
 * The exit code is 2 when it finds an error.
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
  const accountProblems = requested.length > 0 ? [] : await checkAccounts(asJson);
  if (asJson) console.log(JSON.stringify(requested.length > 0 ? reports : { tournaments: reports, accounts: accountProblems }, null, 2));
  if (reports.some((report) => report.counts.errors > 0) || accountProblems.length > 0) process.exitCode = 2;
}

/** The EMA ids of the accounts and their player capability. */
async function checkAccounts(quietOutput: boolean): Promise<unknown[]> {
  const [users, index] = await Promise.all([db.collection("users").select("emaId").get(), db.collection("emaIdIndex").select("uid").get()]);
  const emaIdOf = new Map(users.docs.map((doc) => [doc.id, typeof doc.get("emaId") === "string" && doc.get("emaId") !== "" ? String(doc.get("emaId")) : null]));
  const problems: unknown[] = findEmaIdProblems({
    profiles: users.docs.map((doc) => ({ uid: doc.id, emaId: emaIdOf.get(doc.id) ?? null })),
    index: index.docs.map((doc) => ({ emaId: doc.id, uid: String(doc.get("uid")) })),
  });
  const accounts: Array<{ uid: string; hasPlayerClaim: boolean; emaId: string | null }> = [];
  let pageToken: string | undefined;
  do {
    // eslint-disable-next-line no-await-in-loop
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => accounts.push({
      uid: user.uid,
      hasPlayerClaim: (user.customClaims as { player?: boolean } | undefined)?.player === true,
      emaId: emaIdOf.get(user.uid) ?? null,
    }));
    pageToken = page.pageToken;
  } while (pageToken);
  problems.push(...findPlayerClaimProblems(accounts));
  if (!quietOutput) {
    console.log(`
accounts: ${users.size} profiles, ${index.size} index documents, ${problems.length} problems`);
    problems.forEach((problem) => console.log(`  [error] ${JSON.stringify(problem)}`));
  }
  return problems;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
