import { auth, db, firebaseProjectId } from "../firebase";
import { findPlayerClaimProblems } from "../services/emaIdAudit";
import { grantPlayerCapability, revokePlayerCapability } from "../services/usersService";

const applyChanges = process.argv.includes("--apply");

/**
 * Makes the player capability and the EMA id go together:
 * - an editor or admin with the player capability and no EMA id loses the player capability,
 * - an account with an EMA id and no player capability gets it.
 * An account that is only a player and has no EMA id stays as it is: the script lists it for a manual decision.
 * It previews by default. Add --apply to write.
 */
async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the player capability fix for ${firebaseProjectId}.`);
  const users = await db.collection("users").select("emaId").get();
  const emaIdOf = new Map(users.docs.map((doc) => [doc.id, typeof doc.get("emaId") === "string" && doc.get("emaId") !== "" ? String(doc.get("emaId")) : null]));
  const claimsOf = new Map<string, { admin?: boolean; editor?: boolean; player?: boolean }>();
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => claimsOf.set(user.uid, (user.customClaims ?? {}) as { admin?: boolean; editor?: boolean; player?: boolean }));
    pageToken = page.pageToken;
  } while (pageToken);
  const problems = findPlayerClaimProblems([...claimsOf].map(([uid, claims]) => ({
    uid, hasPlayerClaim: claims.player === true, emaId: emaIdOf.get(uid) ?? null,
  })));
  for (const problem of problems) {
    const claims = claimsOf.get(problem.uid)!;
    const isManager = claims.admin === true || claims.editor === true;
    if (problem.code === "PLAYER_WITHOUT_EMA_ID" && !isManager) {
      console.log(`${problem.uid}: player only, no EMA id. Left as it is.`);
    } else if (problem.code === "PLAYER_WITHOUT_EMA_ID") {
      console.log(`${problem.uid}: ${claims.admin ? "admin" : "editor"} with the player capability and no EMA id -> remove the player capability`);
      if (applyChanges) await revokePlayerCapability(problem.uid);
    } else {
      console.log(`${problem.uid}: owns an EMA id and has no player capability -> grant it`);
      if (applyChanges) await grantPlayerCapability(problem.uid);
    }
  }
  console.log(problems.length === 0 ? "Nothing to fix." : applyChanges ? "Done." : "Run again with --apply to write.");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
