#!/usr/bin/env bash
# Plays one slot of the rehearsal with the real app on a connected emulator, until the tournament ends or the time is
# over (RehearsalPlayerTest). See docs/rehearsal.md.
#
#   ./gradlew :androidApp:assembleStaging :androidApp:assembleStagingAndroidTest -Pmts.testBuildType=staging
#   scripts/rehearsal/emulator-player.sh 2 emulator-5554
#
# Environment: STAGING_ADMIN_EMAIL and REHEARSAL_PASSWORD. Optional: REHEARSAL_TOURNAMENT, REHEARSAL_MINUTES (150),
# REHEARSAL_HAND_SECONDS (60-90), REHEARSAL_API, REHEARSAL_APKS (the folder of the built APKs), REHEARSAL_ACCOUNT.
set -euo pipefail

slot="${1:?Pass the slot, for example 2}"
serial="${2:-}"
: "${STAGING_ADMIN_EMAIL:?Set STAGING_ADMIN_EMAIL}"
: "${REHEARSAL_PASSWORD:?Set REHEARSAL_PASSWORD}"
tournament="${REHEARSAL_TOURNAMENT:-Ensayo General MTS 2026}"
minutes="${REHEARSAL_MINUTES:-150}"
hand_seconds="${REHEARSAL_HAND_SECONDS:-60-90}"
api="${REHEARSAL_API:-https://europe-southwest1-mts---staging.cloudfunctions.net/api}"
apks="${REHEARSAL_APKS:-androidApp/build/outputs/apk}"

adb_cmd=(adb)
if [ -n "$serial" ]; then adb_cmd+=(-s "$serial"); fi

base="$(printf '%s' "$STAGING_ADMIN_EMAIL" | tr '[:upper:]' '[:lower:]')"
local_part="${base%@*}"
local_part="${local_part%%+*}"
# The logs of GitHub are public: the addresses of the accounts never show in them.
if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::add-mask::$local_part"; fi
# REHEARSAL_ACCOUNT: the letters of the account before the slot number, "j" (rehearsal) or "cal" (calibration).
email="$(printf '%s+mts-%s%02d@%s' "$local_part" "${REHEARSAL_ACCOUNT:-j}" "$slot" "${base#*@}")"

app_apk="$(find "$apks" -path '*androidTest*' -prune -o -name '*staging*.apk' -print | head -n 1)"
test_apk="$(find "$apks" -path '*androidTest*' -name '*staging*.apk' | head -n 1)"
if [ -z "$app_apk" ] || [ -z "$test_apk" ]; then
  echo "No staging APKs in $apks. Build them first (see the top of this script)." >&2
  exit 1
fi

"${adb_cmd[@]}" wait-for-device
"${adb_cmd[@]}" install -r -g "$app_apk" >/dev/null
"${adb_cmd[@]}" install -r -g "$test_apk" >/dev/null
"${adb_cmd[@]}" shell settings put global hide_error_dialogs 1

# What the player does goes to the log of the emulator, tag REHEARSAL. REHEARSAL_LOGCAT keeps it in a file.
if [ -n "${REHEARSAL_LOGCAT:-}" ]; then
  "${adb_cmd[@]}" logcat -c || true
  "${adb_cmd[@]}" logcat -s REHEARSAL:I E2E:I TestRunner:I > "$REHEARSAL_LOGCAT" 2>&1 &
  logcat_pid=$!
  trap 'kill "$logcat_pid" 2>/dev/null || true' EXIT
fi

echo "Slot $slot plays on ${serial:-the emulator} for $minutes minutes."
# The device shell joins the arguments again, so the texts with spaces go in single quotes.
"${adb_cmd[@]}" shell am instrument -w -r \
  -e class com.etologic.mahjongtournamentsuite.player.e2e.RehearsalPlayerTest \
  -e e2eBackendUrl "$api" \
  -e e2eWaitMs 60000 \
  -e rehearsalEmail "$email" \
  -e rehearsalPassword "'$REHEARSAL_PASSWORD'" \
  -e rehearsalTournament "'$tournament'" \
  -e rehearsalMinutes "$minutes" \
  -e rehearsalHandSeconds "$hand_seconds" \
  com.etologic.mahjongtournamentsuite.staging.test/androidx.test.runner.AndroidJUnitRunner
