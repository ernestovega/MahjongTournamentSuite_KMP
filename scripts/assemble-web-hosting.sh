#!/usr/bin/env bash
# Builds the two web apps and puts them in one folder for Firebase Hosting (firebase.json runs it before a deploy).
# The site mts.mahjongmadrid.com serves the player app at / and the admin app at /admin/. Both share the session.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :playerApp:wasmJsBrowserDistribution :adminApp:wasmJsBrowserDistribution

out=build/hosting
rm -rf "$out"
mkdir -p "$out/admin"
cp -R playerApp/build/dist/wasmJs/productionExecutable/. "$out/"
cp -R adminApp/build/dist/wasmJs/productionExecutable/. "$out/admin/"
# firebase.json lets browsers keep every .wasm file for a year without asking again. That is only safe while the
# name of each file is the hash of its content, so a new build gets new names.
if find "$out" -name '*.wasm' | grep -Ev '/[0-9a-f]{16,}\.wasm$'; then
  echo "A .wasm file above has no content hash in its name. Fix the build or the cache rule in firebase.json." >&2
  exit 1
fi
echo "Web hosting files in $out"
