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
# The API page (Swagger UI) at /api-docs/. See docs/architecture/api.md.
mkdir -p "$out/api-docs"
cp docs/api/index.html docs/api/swagger-init.js docs/api/openapi.json "$out/api-docs/"
cp third_party/swagger-ui/swagger-ui-bundle.js third_party/swagger-ui/swagger-ui.css "$out/api-docs/"
# The published web version, for python3 scripts/versions.py status. See docs/versioning.md.
web_version=$(sed -n 's/^web-versionName *= *"\([^"]*\)".*/\1/p' gradle/libs.versions.toml)
[ -n "$web_version" ] || { echo "web-versionName is missing in gradle/libs.versions.toml" >&2; exit 1; }
printf '{"web":"%s"}\n' "$web_version" > "$out/version.json"
echo "Web hosting files in $out (web $web_version)"
