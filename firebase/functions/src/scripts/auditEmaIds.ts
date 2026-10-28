import { db, firebaseProjectId } from "../firebase";
import { findEmaIdProblems } from "../services/emaIdAudit";

/**
 * Checks that every EMA id belongs to one account and that the index agrees with the profiles. It only reads.
 * Usage: npm run audit:ema-ids. The exit code is 2 when it finds a problem.
 */
async function main(): Promise<void> {
  const [users, index] = await Promise.all([db.collection("users").select("emaId").get(), db.collection("emaIdIndex").select("uid").get()]);
  const problems = findEmaIdProblems({
    profiles: users.docs.map((doc) => ({ uid: doc.id, emaId: typeof doc.get("emaId") === "string" && doc.get("emaId") !== "" ? doc.get("emaId") : null })),
    index: index.docs.map((doc) => ({ emaId: doc.id, uid: String(doc.get("uid")) })),
  });
  console.log(`Checked ${users.size} accounts and ${index.size} index documents in ${firebaseProjectId}.`);
  problems.forEach((problem) => console.log(JSON.stringify(problem)));
  console.log(problems.length === 0 ? "Every EMA id belongs to one account." : `${problems.length} problems found.`);
  if (problems.length > 0) process.exitCode = 2;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
