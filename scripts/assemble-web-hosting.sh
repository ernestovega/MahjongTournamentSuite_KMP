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
echo "Web hosting files in $out"
