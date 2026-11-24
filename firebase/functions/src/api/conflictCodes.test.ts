import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

/**
 * Every refusal with a 409 sends a `details.code`, and the apps know each code, so they write the reason in the language
 * of the user (ui/.../AppErrorUi.kt) instead of the English message of the server.
 */

const SRC = join(__dirname, "..");
const DOMAIN = join(__dirname, "../../../../domain/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/domain/model");

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return name.endsWith(".js") && !name.endsWith(".test.js") ? [path] : [];
  });
}

/** The text of each `conflict(...)` call, with its arguments. The compiled code calls it as `(0, httpError_1.conflict)(`. */
function conflictCalls(code: string): string[] {
  return [...code.matchAll(/\bconflict\)?\(/g)].map((match) => {
    const start = match.index! + match[0].length - 1;
    let depth = 0;
    let end = start;
    for (; end < code.length; end++) {
      if (code[end] === "(") depth++;
      if (code[end] === ")" && --depth === 0) break;
    }
    return code.slice(start, end + 1);
  });
}

function kotlinEnum(file: string, name: string): string[] {
  const text = readFileSync(join(DOMAIN, file), "utf8");
  const body = text.slice(text.indexOf(`enum class ${name}`));
  return [...body.slice(body.indexOf("{") + 1, body.indexOf("}")).matchAll(/\b([A-Z][A-Z0-9_]+)\b/g)].map((match) => match[1]);
}

test("every 409 of the API sends a code that the apps translate", () => {
  const known = new Set([
    ...kotlinEnum("ConflictCode.kt", "ConflictCode"),
    ...kotlinEnum("Registration.kt", "RegistrationErrorCode"),
    ...kotlinEnum("PlayerImport.kt", "ImportFileErrorCode"),
    "ROUND_HAS_HANDS",
    "TABLE_HAS_ALL_HANDS",
  ]);
  const missing: string[] = [];
  const unknown: string[] = [];
  let count = 0;
  for (const file of sourceFiles(SRC)) {
    if (file.endsWith(join("api", "httpError.js"))) continue;
    for (const call of conflictCalls(readFileSync(file, "utf8"))) {
      count++;
      if (!/\bcode:/.test(call)) missing.push(`${file}: ${call.slice(0, 80)}`);
      for (const match of call.matchAll(/\bcode:\s*"([A-Z_]+)"/g)) if (!known.has(match[1])) unknown.push(`${match[1]} in ${file}`);
    }
  }
  assert.ok(count > 40, `only ${count} conflict calls found: the scan is broken`);
  assert.deepEqual(missing, [], "409 without a code");
  assert.deepEqual(unknown, [], "codes that the apps do not know: add them to ConflictCode and AppErrorUi");
});
