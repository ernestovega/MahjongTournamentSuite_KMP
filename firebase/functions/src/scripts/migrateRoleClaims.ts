import { auth, firebaseProjectId } from "../firebase";
import { runScript } from "./scriptRun";

const applyChanges = process.argv.includes("--apply");

/**
 * Moves the accounts from the old role claim to the new one: `admin` becomes `superAdmin`. The API knows only the
 * new claims, so run it right after the first deploy of the country admin roles, or nobody can manage the project.
 * Editors and players keep their claims. It previews by default. Add --apply to write.
 */
async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the super admin claim migration for ${firebaseProjectId}.`);
  let pageToken: string | undefined;
  let changed = 0;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    for (const user of page.users) {
      const claims: Record<string, unknown> = { ...(user.customClaims ?? {}) };
      if (claims.admin !== true) continue;
      delete claims.admin;
      delete claims.editor;
      claims.superAdmin = true;
      console.log(`${user.uid} (${user.email ?? "no email"}): admin -> superAdmin`);
      if (applyChanges) await auth.setCustomUserClaims(user.uid, claims);
      changed++;
    }
    pageToken = page.pageToken;
  } while (pageToken);
  console.log(changed === 0 ? "Nothing to change." : applyChanges ? `Done: ${changed} accounts.` : `Run again with --apply to change ${changed} accounts.`);
}

runScript("migrateRoleClaims", main, applyChanges);
