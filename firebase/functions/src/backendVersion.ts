import { readFileSync } from "node:fs";
import { join } from "node:path";

let cached: string | undefined;

/** The backend version, written into lib/backendVersion.json by the build. "unknown" when the file is missing. */
export function backendVersion(): string {
  if (cached === undefined) {
    try {
      cached = String(JSON.parse(readFileSync(join(__dirname, "backendVersion.json"), "utf8")).version ?? "unknown");
    } catch {
      cached = "unknown";
    }
  }
  return cached;
}
