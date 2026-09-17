import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { normalizePlayerName } from "../services/playerName";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";

const MIGRATION_COLLECTION = "emaPlayerRegistryNameMigrations";
const APPLY_FLAG = "--apply";
const MAX_CHANGES_PER_BATCH = 200;

type NameChange = {
  emaId: string;
  before: string;
  after: string;
  data: FirebaseFirestore.DocumentData;
};

async function main(): Promise<void> {
  const shouldApply = process.argv.includes(APPLY_FLAG);
  const projectId = process.env.EMA_FIREBASE_PROJECT?.trim()
    || process.env.GCLOUD_PROJECT?.trim()
    || "unknown";
  const snapshot = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).get();
  const changes: NameChange[] = snapshot.docs.flatMap((document) => {
    const data = document.data();
    const before = String(data.name ?? "");
    const after = normalizePlayerName(before);
    return before === after ? [] : [{ emaId: document.id, before, after, data }];
  });

  console.log(`Project: ${projectId}`);
  console.log(`Checked ${snapshot.size} player records.`);
  console.log(`Found ${changes.length} names to normalize.`);
  for (const change of changes.slice(0, 20)) {
    console.log(`${change.emaId}: ${change.before} -> ${change.after}`);
  }
  if (changes.length > 20) console.log(`...and ${changes.length - 20} more.`);

  if (!shouldApply) {
    console.log(`Dry run only. Run again with ${APPLY_FLAG} to write these changes.`);
    return;
  }
  if (changes.length === 0) {
    console.log("No writes are required.");
    return;
  }

  const reportRef = db.collection(MIGRATION_COLLECTION).doc();
  await reportRef.set({
    projectId,
    status: "running",
    changedCount: changes.length,
    startedAt: FieldValue.serverTimestamp(),
  });

  for (let offset = 0; offset < changes.length; offset += MAX_CHANGES_PER_BATCH) {
    const batch = db.batch();
    for (const change of changes.slice(offset, offset + MAX_CHANGES_PER_BATCH)) {
      batch.set(reportRef.collection("backupPlayers").doc(change.emaId), change.data);
      batch.update(db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(change.emaId), {
        name: change.after,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    await batch.commit();
  }

  await reportRef.update({
    status: "complete",
    completedAt: FieldValue.serverTimestamp(),
  });

  const verification = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).get();
  const remaining = verification.docs.filter((document) => {
    const name = String(document.data().name ?? "");
    return name !== normalizePlayerName(name);
  });
  if (remaining.length > 0) {
    throw new Error(`Migration verification failed for ${remaining.length} player records.`);
  }
  console.log(`Updated ${changes.length} names. Verification passed.`);
  console.log(`Backup report: ${MIGRATION_COLLECTION}/${reportRef.id}`);
}

main().catch((error: unknown) => {
  console.error("Player name normalization failed.", error);
  process.exitCode = 1;
});
