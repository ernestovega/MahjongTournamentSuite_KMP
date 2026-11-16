import { readFileSync } from "node:fs";
import { join } from "node:path";

/** What the build wrote into lib/backendVersion.json: the backend version and the other versions on main. */
export interface BuiltVersions {
  version: string;
  web?: string;
  android?: string;
  androidCode?: string;
}

let cached: BuiltVersions | undefined;

export function builtVersions(): BuiltVersions {
  if (cached === undefined) {
    try {
      const read = JSON.parse(readFileSync(join(__dirname, "backendVersion.json"), "utf8"));
      cached = { ...read, version: String(read.version ?? "unknown") };
    } catch {
      cached = { version: "unknown" };
    }
  }
  return cached as BuiltVersions;
}

/** The backend version. "unknown" when the build did not write it. */
export function backendVersion(): string {
  return builtVersions().version;
}
