import { createHash } from "node:crypto";

import { db, firebaseProjectId, storage } from "../firebase";

/**
 * Writes associationLogoHash for every tournament that has a logo file but no hash.
 * It previews by default. Add --apply to write.
 */
const applyChanges = process.argv.includes("--apply");

async function main() {
  console.log(`${applyChanges ? "Applying" : "Previewing"} logo hashes for ${firebaseProjectId}.`);
  const snap = await db.collection("tournaments").get();
  let updated = 0;
  for (const doc of snap.docs) {
    const path = String(doc.get("associationLogoPath") ?? "").trim();
    if (path.length === 0 || typeof doc.get("associationLogoHash") === "string") continue;
    const [bytes] = await storage.file(path).download();
    const hash = createHash("sha256").update(bytes).digest("hex");
    console.log(`${doc.id}: ${hash.slice(0, 12)}`);
    if (applyChanges) await doc.ref.update({ associationLogoHash: hash });
    updated++;
  }
  console.log(`${updated} tournaments ${applyChanges ? "updated" : "to update"}.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
