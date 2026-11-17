#!/usr/bin/env bash
# The part of the rehearsal that runs on the MacBook Pro: the real app on the four running emulators (slots 2-5) and
# the bots of slots 49-60. Everything it writes stays in build/rehearsal. See docs/rehearsal.md.
#
#   export STAGING_ADMIN_EMAIL=<the staging admin email>
#   export REHEARSAL_PASSWORD="$(gcloud secrets versions access latest --secret=REHEARSAL_PASSWORD --project=mts---staging)"
#   scripts/rehearsal/mac.sh 2026-11-19T19:45:00+01:00
#
# Start the four emulators first. Optional: REHEARSAL_TOURNAMENT and REHEARSAL_HAND_SECONDS.
set -euo pipefail
cd "$(dirname "$0")/../.."

until_time="${1:?Pass when the rehearsal ends, as 2026-11-19T19:45:00+01:00}"
export REHEARSAL_TOURNAMENT="${REHEARSAL_TOURNAMENT:-Ensayo General MTS 2026}"
export REHEARSAL_MINUTES="$(node -e 'console.log(Math.max(1, Math.round((Date.parse(process.argv[1]) - Date.now()) / 60000)))' "$until_time")"
mkdir -p build/rehearsal

if [ "${SKIP_BUILD:-}" != "1" ]; then
  ./gradlew :androidApp:assembleStaging :androidApp:assembleStagingAndroidTest -Pmts.testBuildType=staging
fi

serials=()
while read -r serial state; do
  if [ "$state" = "device" ]; then serials+=("$serial"); fi
done < <(adb devices | tail -n +2)
if [ "${#serials[@]}" -lt 4 ]; then
  echo "Start four emulators first (found ${#serials[@]})." >&2
  exit 1
fi

for index in 0 1 2 3; do
  slot=$((index + 2))
  REHEARSAL_LOGCAT="build/rehearsal/emulator-j0$slot-logcat.txt" \
    scripts/rehearsal/emulator-player.sh "$slot" "${serials[$index]}" > "build/rehearsal/emulator-j0$slot.log" 2>&1 &
  echo "Slot $slot on ${serials[$index]}: build/rehearsal/emulator-j0$slot.log"
done

node scripts/rehearsal/bots.mjs --tournament="$REHEARSAL_TOURNAMENT" --slots=49-60 --until="$until_time" \
  --hand-seconds="${REHEARSAL_HAND_SECONDS:-60-90}" --report=build/rehearsal/bots-mac.json --title="Bots 49-60 (Mac)"
wait
echo "Done. The reports are in build/rehearsal."
