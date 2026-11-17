import { randomUUID } from "node:crypto";

import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

/**
 * Copies the EMA player registry of production to a staging project, with the EMA photos, when the EMA website cannot
 * be read. It only reads production: every write goes to `--to=<project>`, which can never be production.
 *
 * Run it in Google Cloud Shell as an owner of both projects, from firebase/functions:
 *   npm run build && node lib/scripts/copyEmaPlayersToStaging.js --to=mts---staging            (preview)
 *   npm run build && node lib/scripts/copyEmaPlayersToStaging.js --to=mts---staging --apply    (copy)
 * A second run copies everything again. The photo of a player who already has one in staging is not copied again.
 */
const PRODUCTION = "mahjong-tournament-suite";
const COLLECTION = "emaPlayerRegistry";
const PHOTO_FOLDER = "playerPhotos";
const BATCH_SIZE = 400;

const applyChanges = process.argv.includes("--apply");
const target = process.argv.find((arg) => arg.startsWith("--to="))?.slice("--to=".length).trim() ?? "";

function photoUrl(bucket: string, objectName: string, token: string): string {
  return `https://firebasestorage.googleapis.com/v0/b/${bucket}/o/${encodeURIComponent(objectName)}?alt=media&token=${token}`;
}

async function main(): Promise<void> {
  if (!target) throw new Error("Pass --to=<staging project>.");
  if (target === PRODUCTION) throw new Error("The target can never be the production project.");

  const prod = initializeApp({ projectId: PRODUCTION }, "production");
  const staging = initializeApp({ projectId: target }, "staging");
  const prodBucket = getStorage(prod).bucket(`${PRODUCTION}.firebasestorage.app`);
  const stagingBucketName = `${target}.firebasestorage.app`;
  const stagingBucket = getStorage(staging).bucket(stagingBucketName);
  const prodDb = getFirestore(prod);
  const stagingDb = getFirestore(staging);

  const players = await prodDb.collection(COLLECTION).get();
  const existing = new Map((await stagingDb.collection(COLLECTION).get()).docs.map((doc) => [doc.id, doc.data()]));
  const withPhoto = players.docs.filter((doc) => typeof doc.get("photoUrl") === "string").length;
  console.log(`Production: ${players.size} EMA players, ${withPhoto} with a photo. Staging now: ${existing.size}.`);
  if (!applyChanges) {
    console.log("Preview only. Add --apply to copy them to " + target + ".");
    return;
  }

  let batch = stagingDb.batch();
  let pending = 0;
  let copied = 0;
  let photos = 0;
  for (const doc of players.docs) {
    const data = { ...doc.data() };
    if (typeof data.photoUrl === "string") {
      const before = existing.get(doc.id)?.photoUrl;
      if (typeof before === "string" && before.includes(`/b/${stagingBucketName}/`)) {
        data.photoUrl = before;
      } else {
        const objectName = `${PHOTO_FOLDER}/${doc.id}.jpg`;
        const source = prodBucket.file(objectName);
        const [found] = await source.exists();
        if (found) {
          const token = randomUUID();
          const destination = stagingBucket.file(objectName);
          await source.copy(destination);
          await destination.setMetadata({ metadata: { firebaseStorageDownloadTokens: token } });
          data.photoUrl = photoUrl(stagingBucketName, objectName, token);
          photos += 1;
        } else {
          data.photoUrl = null;
        }
      }
    }
    batch.set(stagingDb.collection(COLLECTION).doc(doc.id), data);
    pending += 1;
    copied += 1;
    if (pending === BATCH_SIZE) {
      await batch.commit();
      batch = stagingDb.batch();
      pending = 0;
      console.log(`Copied ${copied}/${players.size}.`);
    }
  }
  if (pending > 0) await batch.commit();
  await stagingDb.collection("_meta").doc("dataVersions").set({
    resources: { emaPlayers: { revision: FieldValue.increment(1), changedAt: FieldValue.serverTimestamp() } },
  }, { merge: true });
  console.log(`Done: ${copied} EMA players and ${photos} photos copied to ${target}.`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
