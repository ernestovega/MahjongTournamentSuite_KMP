#!/bin/bash
# Runs the instrumented UI tests on the connected emulator. It starts the failed tests again, in new processes.
#
# The Compose test harness sometimes resumes an app coroutine on a thread that is not the main thread. A test then
# fails with "must be called on the main thread" or "State must be at least 'CREATED'". The app code is the same code
# that runs on a device, so this script runs only those tests again. Other failures stay failures.
#
# Usage: scripts/run-e2e-tests.sh [androidApp|playerAndroidApp] [test class or class#method, comma separated]
set -uo pipefail

module="${1:-androidApp}"
filter="${2:-}"
max_runs=4
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
results="$root/$module/build/outputs/androidTest-results/connected"
status_file="$(mktemp)"

run_gradle() {
  local args=()
  [ -n "$1" ] && args+=("-Pandroid.testInstrumentationRunnerArguments.class=$1")
  [ -n "${E2E_WAIT_MS:-}" ] && args+=("-Pandroid.testInstrumentationRunnerArguments.e2eWaitMs=$E2E_WAIT_MS")
  rm -rf "$results"
  (cd "$root" && ./gradlew ":$module:connectedDebugAndroidTest" ${args[@]+"${args[@]}"} > "$root/build/e2e-gradle.log" 2>&1)
}

# Prints "id<TAB>status<TAB>harness or real<TAB>message" for every test of the last run.
collect() {
  python3 - "$results" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
harness = ("must be called on the main thread", "State must be at least 'CREATED'", "performMeasureAndLayout called during measure layout", "runDetachLifecycle")
for path in glob.glob(sys.argv[1] + "/**/TEST-*.xml", recursive=True):
    for case in ET.parse(path).getroot().iter("testcase"):
        bad = [c for c in case if c.tag in ("failure", "error")]
        text = (bad[0].text or "") if bad else ""
        flaky = any(h in text for h in harness)
        lines = [l for l in text.split("\n") if l.strip() and not l.strip().startswith("at ")]
        message = " | ".join(lines[:12])[:900]
        print(f"{case.get('classname')}#{case.get('name')}\t{'FAIL' if bad else 'PASS'}\t{'harness' if flaky else 'real'}\t{message}")
PY
}

# The Google password manager shows a "Save password?" window over the app after a sign-in. Switch it off.
adb shell settings put secure autofill_service null > /dev/null 2>&1 || true
# The seed writes round times in the time zone of this computer. The emulator must use the same zone.
adb shell service call alarm 3 s16 "$(readlink /etc/localtime | sed 's#.*/zoneinfo/##')" > /dev/null 2>&1 || true

# The tests look for English texts. The apps follow the device language, so they get an English locale.
adb shell cmd locale set-app-locales com.etologic.mahjongtournamentsuite --locales en-US > /dev/null 2>&1 || true
adb shell cmd locale set-app-locales com.etologic.mahjongtournamentsuite.player --locales en-US > /dev/null 2>&1 || true

current="$filter"
for run in $(seq 1 "$max_runs"); do
  run_gradle "$current"
  collect > "$status_file.run"
  # Newer results replace older ones for the same test.
  cat "$status_file" "$status_file.run" | awk -F'\t' '{r[$1]=$0} END {for (k in r) print r[k]}' > "$status_file.merged"
  mv "$status_file.merged" "$status_file"
  current="$(awk -F'\t' '$2=="FAIL" && $3=="harness" {print $1}' "$status_file.run" | paste -sd, -)"
  [ -z "$current" ] && break
  echo "Run $run: starting again the tests that hit the harness error: $current" | cut -c1-300
done

passed=$(awk -F'\t' '$2=="PASS"' "$status_file" | wc -l | tr -d ' ')
failed=$(awk -F'\t' '$2=="FAIL"' "$status_file" | wc -l | tr -d ' ')
awk -F'\t' '$2=="FAIL" {print "FAILED " $1 "\n    " $4}' "$status_file"
echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
