import { FieldValue } from "firebase-admin/firestore";

import { auth, db, firebaseProjectId } from "../firebase";
import { readOrganizerIds } from "../services/organizationsService";
import { syncRoleClaims } from "../services/usersService";
import { globalVersionUpdate } from "../services/dataVersionsService";
import { runScript } from "./scriptRun";

const applyChanges = process.argv.includes("--apply");

/**
 * Moves a project from country admins to club admins.
 * - Each tournament without organizers gets a placeholder organization of its host country, "Pending organizer (ES)",
 *   with the color of the tournament. A super admin then gives each tournament its real organizers.
 * - The old countryAdmin claim and the adminCountries field go away. The account keeps its other roles: an editor stays
 *   an editor, and an account with nothing else becomes a player (or is disabled without an EMA id).
 * Run it right after the first deploy of the organizations. It previews by default. Add --apply to write.
 */
async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the organizations migration for ${firebaseProjectId}.`);

  const tournaments = await db.collection("tournaments").select("name", "hostCountry", "organizerIds", "primaryColor").get();
  const pending = tournaments.docs.filter((document) => readOrganizerIds(document.get("organizerIds")).length === 0);
  const placeholderOf = new Map<string, string>();
  for (const document of pending) {
    const country = String(document.get("hostCountry") ?? "").trim().toUpperCase() || "ES";
    let organizationId = placeholderOf.get(country);
    if (organizationId == null) {
      organizationId = `pending-organizer-${country.toLowerCase()}`;
      placeholderOf.set(country, organizationId);
      console.log(`organization ${organizationId}: Pending organizer (${country})`);
      if (applyChanges) {
        await db.doc(`organizations/${organizationId}`).set({
          name: `Pending organizer (${country})`,
          type: "CLUB",
          country,
          primaryColor: /^#[0-9A-F]{6}$/i.test(String(document.get("primaryColor") ?? "")) ? String(document.get("primaryColor")).toUpperCase() : "#02B16B",
          logoUrl: null,
          logoPath: null,
          logoHash: null,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      }
    }
    console.log(`tournament ${document.id} (${String(document.get("name") ?? "")}) -> ${organizationId}`);
    if (applyChanges) await document.ref.update({ organizerIds: [organizationId], updatedAt: FieldValue.serverTimestamp() });
  }

  let accounts = 0;
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    for (const user of page.users) {
      if ((user.customClaims ?? {}).countryAdmin !== true) continue;
      accounts++;
      console.log(`${user.uid} (${user.email ?? "no email"}): country admin -> loses the role; a super admin can make it a club admin`);
      if (!applyChanges) continue;
      await db.doc(`users/${user.uid}`).set({ adminCountries: FieldValue.delete() }, { merge: true });
      console.log(`  ${await syncRoleClaims(user.uid)}`);
    }
    pageToken = page.pageToken;
  } while (pageToken);

  if (applyChanges && pending.length > 0) {
    await db.collection("_meta").doc("dataVersions").set(globalVersionUpdate("tournaments"), { merge: true });
  }
  const summary = `${pending.length} tournaments without organizers, ${accounts} country admins`;
  console.log(applyChanges ? `Done: ${summary}.` : `Run again with --apply to change ${summary}.`);
}

runScript("migrateOrganizations", main, applyChanges);
