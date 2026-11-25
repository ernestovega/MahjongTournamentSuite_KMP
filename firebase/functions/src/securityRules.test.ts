import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

/**
 * The apps never read Firestore or Storage directly: everything goes through the API, which checks the permissions.
 * So the rules of both must refuse every read and write. A rule that opens a path would bypass the API.
 */
const root = join(__dirname, "..", "..", "..");
const compact = (text: string) => text.replace(/\/\/[^\n]*/g, "").replace(/\s+/g, " ").trim();

test("the Firestore rules refuse everything", () => {
  assert.equal(
    compact(readFileSync(join(root, "firebase", "firestore.rules"), "utf8")),
    "rules_version = '2'; service cloud.firestore { match /databases/{database}/documents { match /{document=**} { allow read, write: if false; } } }",
  );
});

test("the Storage rules refuse everything", () => {
  assert.equal(
    compact(readFileSync(join(root, "firebase", "storage.rules"), "utf8")),
    "rules_version = '2'; service firebase.storage { match /b/{bucket}/o { match /{allPaths=**} { allow read, write: if false; } } }",
  );
});

test("every Firebase config deploys those rules files", () => {
  const config = JSON.parse(readFileSync(join(root, "firebase.json"), "utf8"));
  const firestore = Array.isArray(config.firestore) ? config.firestore : [config.firestore];
  const storage = Array.isArray(config.storage) ? config.storage : [config.storage];
  assert.deepEqual([...new Set(firestore.map((it: { rules: string }) => it.rules))], ["firebase/firestore.rules"]);
  assert.deepEqual([...new Set(storage.map((it: { rules: string }) => it.rules))], ["firebase/storage.rules"]);
  const e2e = JSON.parse(readFileSync(join(root, "firebase.e2e.json"), "utf8"));
  assert.equal(e2e.firestore.rules, "firebase/firestore.rules");
});
