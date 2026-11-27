import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import test from "node:test";

/** Loads the Firebase setup in a new process with these variables, and gives its project id and bucket. */
function setupWith(env: Record<string, string>): { projectId: string; bucket: string } {
  const keys = ["EMA_FIREBASE_STORAGE_BUCKET", "EMA_FIREBASE_PROJECT", "GCLOUD_PROJECT", "GOOGLE_CLOUD_PROJECT", "EMA_FIREBASE_SERVICE_ACCOUNT"];
  const clean = Object.fromEntries(Object.entries(process.env).filter(([key]) => !keys.includes(key)));
  const output = execFileSync(process.execPath, [
    "-e",
    "const f = require('./firebase'); console.log(JSON.stringify({ projectId: f.firebaseProjectId, bucket: f.storage.name }))",
  ], { cwd: __dirname, env: { ...clean, ...env }, encoding: "utf8" });
  return JSON.parse(output.trim().split("\n").pop() as string) as { projectId: string; bucket: string };
}

test("reads the project and the bucket from the variables, in order, with the defaults last", () => {
  assert.deepEqual(setupWith({}), { projectId: "mahjong-tournament-suite", bucket: "mahjong-tournament-suite.firebasestorage.app" });
  assert.deepEqual(setupWith({ GOOGLE_CLOUD_PROJECT: "cloud" }).projectId, "cloud");
  assert.deepEqual(setupWith({ GCLOUD_PROJECT: "gcloud", GOOGLE_CLOUD_PROJECT: "cloud" }).projectId, "gcloud");
  assert.deepEqual(
    setupWith({ EMA_FIREBASE_PROJECT: " own ", EMA_FIREBASE_STORAGE_BUCKET: " bucket ", EMA_FIREBASE_SERVICE_ACCOUNT: "sa@example.test" }),
    { projectId: "own", bucket: "bucket" },
  );
});
