/**
 * Import it first, before "../firebase": it reads --target and sets the project variables (see scriptTarget.ts).
 */
import { parseScriptTarget } from "./scriptTarget";

const result = parseScriptTarget(process.argv, process.env);
process.env.EMA_FIREBASE_PROJECT = result.projectId;
process.env.GCLOUD_PROJECT = result.projectId;
if (result.target !== "emulator" && !process.env.EMA_FIREBASE_STORAGE_BUCKET?.trim()) {
  process.env.EMA_FIREBASE_STORAGE_BUCKET = `${result.projectId}.firebasestorage.app`;
}
console.log(`Target: ${result.target} (${result.projectId}).`);

export const scriptTarget = result;
