#!/usr/bin/env bash
# Runs a phase of the organizer of a game day scenario (ScenarioOrganizerTest) with the real admin app on a connected
# tablet emulator, and records its screen. See docs/scenarios.md.
#
#   scripts/rehearsal/emulator-organizer.sh setup out/scenario [serial]
#
# The folder holds plan.json and the import files of scripts/rehearsal/scenario.mjs. The video (pieces of 3 minutes)
# and the log go to out/organizer-<phase>/.
# Environment: STAGING_ADMIN_EMAIL and REHEARSAL_PASSWORD (the bot admin signs in). Optional: REHEARSAL_API,
# REHEARSAL_APKS (the folder of the built APKs).
set -euo pipefail

phase="${1:?Pass the phase, for example setup}"
plan_dir="${2:?Pass the folder of the plan}"
serial="${3:-}"
: "${STAGING_ADMIN_EMAIL:?Set STAGING_ADMIN_EMAIL}"
: "${REHEARSAL_PASSWORD:?Set REHEARSAL_PASSWORD}"
api="${REHEARSAL_API:-https://europe-southwest1-mts---staging.cloudfunctions.net/api}"
apks="${REHEARSAL_APKS:-androidApp/build/outputs/apk}"
out="out/organizer-$phase"
mkdir -p "$out"

adb_cmd=(adb)
if [ -n "$serial" ]; then adb_cmd+=(-s "$serial"); fi

base="$(printf '%s' "$STAGING_ADMIN_EMAIL" | tr '[:upper:]' '[:lower:]')"
local_part="${base%@*}"
local_part="${local_part%%+*}"
# The logs of GitHub are public: the addresses of the accounts never show in them.
if [ -n "${GITHUB_ACTIONS:-}" ]; then
  echo "::add-mask::$local_part"
  if [ -n "${REHEARSAL_IMAP_USER:-}" ]; then echo "::add-mask::${REHEARSAL_IMAP_USER%@*}"; fi
fi
email="$(printf '%s+mts-botadmin@%s' "$local_part" "${base#*@}")"

app_apk="$(find "$apks" -path '*androidTest*' -prune -o -name '*staging*.apk' -print | head -n 1)"
test_apk="$(find "$apks" -path '*androidTest*' -name '*staging*.apk' | head -n 1)"
if [ -z "$app_apk" ] || [ -z "$test_apk" ]; then
  echo "No staging APKs in $apks." >&2
  exit 1
fi

"${adb_cmd[@]}" wait-for-device
"${adb_cmd[@]}" install -r -g "$app_apk" >/dev/null
"${adb_cmd[@]}" install -r -g "$test_apk" >/dev/null
"${adb_cmd[@]}" shell settings put global hide_error_dialogs 1
"${adb_cmd[@]}" shell rm -rf /data/local/tmp/scenario
"${adb_cmd[@]}" push "$plan_dir" /data/local/tmp/scenario >/dev/null

"${adb_cmd[@]}" logcat -c || true
"${adb_cmd[@]}" logcat -s SCENARIO:I E2E:I TestRunner:I AndroidRuntime:E MahjongTournamentSuite:W > "$out/logcat.txt" 2>&1 &
logcat_pid=$!

# The screen, in pieces of 3 minutes: screenrecord stops at 3 minutes.
(
  piece=0
  while true; do
    piece=$((piece + 1))
    "${adb_cmd[@]}" shell screenrecord --time-limit 180 --bit-rate 2000000 "/sdcard/organizer-$(printf '%03d' "$piece").mp4" || true
  done
) &
record_pid=$!

finish() {
  kill "$record_pid" 2>/dev/null || true
  "${adb_cmd[@]}" shell pkill -INT screenrecord 2>/dev/null || true
  sleep 3
  for file in $("${adb_cmd[@]}" shell ls /sdcard/ | tr -d '\r' | grep '^organizer-.*\.mp4$' || true); do
    "${adb_cmd[@]}" pull "/sdcard/$file" "$out/$file" >/dev/null 2>&1 || true
  done
  "${adb_cmd[@]}" pull /sdcard/Download/e2e "$out/screenshots" >/dev/null 2>&1 || true
  kill "$logcat_pid" 2>/dev/null || true
}
trap finish EXIT

echo "The organizer runs the phase $phase."
result="$("${adb_cmd[@]}" shell am instrument -w -r \
  -e class com.etologic.mahjongtournamentsuite.e2e.ScenarioOrganizerTest \
  -e e2eBackendUrl "$api" \
  -e e2eWaitMs 60000 \
  -e scenarioEmail "$email" \
  -e scenarioPassword "'$REHEARSAL_PASSWORD'" \
  -e scenarioDir /data/local/tmp/scenario \
  -e scenarioPhase "$phase" \
  com.etologic.mahjongtournamentsuite.staging.test/androidx.test.runner.AndroidJUnitRunner)"
printf '%s\n' "$result" | tee "$out/instrument.txt"
# am instrument ends with OK when the test passed.
printf '%s\n' "$result" | grep -q '^OK (1 test)' || { echo "The organizer failed in the phase $phase." >&2; exit 1; }
