import { auth, db, firebaseProjectId } from "../firebase";
import { findEmaIdProblems, findPlayerClaimProblems } from "../services/emaIdAudit";

/**
 * Checks that every EMA id belongs to one account, that the index agrees with the profiles, and that the player
 * capability goes with the EMA id. It only reads.
 * Usage: npm run audit:ema-ids. The exit code is 2 when it finds a problem.
 */
async function main(): Promise<void> {
  const [users, index] = await Promise.all([db.collection("users").select("emaId").get(), db.collection("emaIdIndex").select("uid").get()]);
  const problems = findEmaIdProblems({
    profiles: users.docs.map((doc) => ({ uid: doc.id, emaId: typeof doc.get("emaId") === "string" && doc.get("emaId") !== "" ? doc.get("emaId") : null })),
    index: index.docs.map((doc) => ({ emaId: doc.id, uid: String(doc.get("uid")) })),
  });
  const emaIdOf = new Map(users.docs.map((doc) => [doc.id, typeof doc.get("emaId") === "string" && doc.get("emaId") !== "" ? String(doc.get("emaId")) : null]));
  const accounts: Array<{ uid: string; hasPlayerClaim: boolean; emaId: string | null }> = [];
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => accounts.push({
      uid: user.uid,
      hasPlayerClaim: (user.customClaims as { player?: boolean } | undefined)?.player === true,
      emaId: emaIdOf.get(user.uid) ?? null,
    }));
    pageToken = page.pageToken;
  } while (pageToken);
  const claimProblems = findPlayerClaimProblems(accounts);
  console.log(`Checked ${users.size} accounts and ${index.size} index documents in ${firebaseProjectId}.`);
  [...problems, ...claimProblems].forEach((problem) => console.log(JSON.stringify(problem)));
  const total = problems.length + claimProblems.length;
  console.log(total === 0 ? "Every EMA id belongs to one account and the player capability goes with it." : `${total} problems found.`);
  if (total > 0) process.exitCode = 2;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
