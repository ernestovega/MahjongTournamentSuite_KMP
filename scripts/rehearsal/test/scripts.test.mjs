// The shell scripts of the game day and the workflows that run them: what the fixes of the game day rely on.
// Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readdirSync, readFileSync } from "node:fs";

const root = new URL("../../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf8");
const organizer = "scripts/rehearsal/emulator-organizer.sh";

test("the shell scripts of the rehearsal are valid bash", () => {
  for (const name of readdirSync(new URL("scripts/rehearsal/", root)).filter((file) => file.endsWith(".sh"))) {
    execFileSync("bash", ["-n", new URL(`scripts/rehearsal/${name}`, root).pathname]);
  }
});

test("the organizer hides every address, in any case, in the lines that go to the public job log", () => {
  const script = read(organizer);
  const hide = /^hide_emails\(\) \{.*\}$/m.exec(script)?.[0];
  assert.ok(hide, "hide_emails is defined");
  const input = [
    "SCENARIO [setup] slot 3 is mailbox+gd1119ab-s003@example.test",
    "MahjongTournamentSuite: signed in as Admin+MTS-BotAdmin@Example.TEST",
    "FATAL EXCEPTION: no user ADMIN@EXAMPLE.TEST",
  ].join("\n");
  const output = execFileSync("bash", ["-c", `${hide}\nhide_emails`], { input, encoding: "utf8" });
  assert.doesNotMatch(output, /@/);
  assert.equal(output.split("\n").filter((line) => line.includes("<email>")).length, 3);
  // The three blocks of the log of a failure use it.
  for (const block of ["'SCENARIO|E2E'", "'MahjongTournamentSuite'", "'FATAL EXCEPTION'"]) {
    const line = script.split("\n").find((each) => each.includes("grep") && each.includes(block));
    assert.match(line, /\| hide_emails/, block);
  }
});

test("the tablet of the organizer is in the Madrid time zone, as the plan", () => {
  // The plan dates the rounds on the day in Madrid, and the form of a new tournament dates it with the day of the tablet.
  assert.match(read(organizer), /set-timezone Europe\/Madrid/);
  const emulators = read(".github/workflows/scenario.yml").split("\n").filter((line) => line.includes("emulator-options:"));
  assert.ok(emulators.length >= 2);
  for (const line of emulators) assert.match(line, /-timezone Europe\/Madrid/);
});

test("the organizer keeps the log of the app at info level for the job log of a failure", () => {
  assert.match(read(organizer), /logcat -s [^\n]*MahjongTournamentSuite:I/);
});

test("the workflows use one major version of each action, on Node 24", () => {
  // CI moved every action to its newest major on 19-nov (Node 24). An older one in a new workflow would bring back
  // the warnings of Node 20.
  const minimum = {
    "actions/checkout": 7, "actions/setup-node": 7, "actions/setup-java": 6, "actions/setup-python": 7,
    "actions/upload-artifact": 7, "actions/download-artifact": 8, "google-github-actions/auth": 3,
    "google-github-actions/setup-gcloud": 3, "gradle/actions/setup-gradle": 6,
  };
  const seen = {};
  for (const name of readdirSync(new URL(".github/workflows/", root)).filter((file) => file.endsWith(".yml"))) {
    for (const [, action, major] of read(`.github/workflows/${name}`).matchAll(/uses: ([\w./-]+)@v(\d+)/g)) {
      (seen[action] ??= new Set()).add(Number(major));
      if (minimum[action] != null) assert.ok(Number(major) >= minimum[action], `${name}: ${action}@v${major}`);
    }
  }
  for (const [action, majors] of Object.entries(seen)) assert.equal(majors.size, 1, `${action}: ${[...majors].join(", ")}`);
});

test("the latency action counts the saves of a hand per 10 minutes, with p50, p95 and max", (context) => {
  try {
    execFileSync("jq", ["--version"]);
  } catch {
    context.skip("jq is not installed");
    return;
  }
  const workflow = read(".github/workflows/rehearsal.yml");
  const program = /jq -s -r '([\s\S]*?)' requests\.jsonl/.exec(workflow)?.[1];
  assert.ok(program, "the jq program of the saves");
  const save = (minute, seconds) => ({ t: `2026-11-19T10:${minute}`.slice(0, 15), m: "POST", u: "/api/player/tournaments/t1/tables/1/3/hands", l: seconds, s: 200 });
  const rows = [
    ...Array.from({ length: 20 }, (_, index) => save("12", (index + 1) / 10)),
    // A preview and a read in the same minutes are not saves.
    { ...save("15", 9), u: "/api/player/tournaments/t1/tables/1/3/hands/preview" },
    { ...save("15", 9), m: "GET" },
    save("21", 0.5),
  ];
  const output = execFileSync("jq", ["-s", "-r", program], { input: rows.map((row) => JSON.stringify(row)).join("\n"), encoding: "utf8" });
  assert.deepEqual(output.trim().split("\n"), [
    "2026-11-19T10:10  saves 20  p50 1000  p95 1900  max 2000",
    "2026-11-19T10:20  saves 1  p50 500  p95 500  max 500",
  ]);
});
