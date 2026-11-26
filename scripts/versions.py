#!/usr/bin/env python3
"""Versions of the three products: the web, the Android app (with the desktop admin app) and the backend.

The versions live in gradle/libs.versions.toml. See docs/versioning.md.

    python3 scripts/versions.py show
    python3 scripts/versions.py status
    python3 scripts/versions.py bump <web|admin|backend>
    python3 scripts/versions.py check [--base origin/main]

`status` puts the version of each product on main next to the published one: the web and the backend as the site
answers them, and the Android app on each Google Play track. Google Play needs credentials: in GitHub Actions run
Actions > Check · Published versions > Run workflow; on a computer, sign in with `gcloud auth application-default login` and install
google-auth and google-api-python-client. Without them, `status` still shows the web and the backend.

`check` lists the files that the branch changed since it left the base branch, finds the products
that those files go into, and fails when one of those products has not a higher version than on
the base branch. The "Build and test" job runs it on every pull request to main.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = "gradle/libs.versions.toml"

WEB, ADMIN, BACKEND = "web", "admin", "backend"
PRODUCTS = (WEB, ADMIN, BACKEND)
LABELS = {WEB: "Web", ADMIN: "Android app and desktop admin app", BACKEND: "Backend"}
# Only the Android app has a version code, because Google Play needs one.
HAS_CODE = {WEB: False, ADMIN: True, BACKEND: False}
# The products that the Gradle build makes. The backend is built with npm.
GRADLE_PRODUCTS = {WEB, ADMIN}

# The site that serves the web at / and /admin/, the file /version.json, and the backend at /api/.
SITE = os.environ.get("MTS_SITE_URL", "https://mts.mahjongmadrid.com")

# The products that each Gradle module goes into.
MODULES = {
    "domain": {WEB, ADMIN},
    "data": {WEB, ADMIN},
    "designsystem": {WEB, ADMIN},
    "ui": {WEB, ADMIN},
    "adminApp": {WEB, ADMIN},
    # The Android app (androidApp, the admin version) holds the player part too.
    "playerApp": {WEB, ADMIN},
    "androidApp": {ADMIN},
    # The mobile suite and the iOS app have the version of the Android app: they are the same app.
    "suite": {ADMIN},
    "iosApp": {ADMIN},
}

# The products that each platform source set goes into. Other source sets, such as commonMain,
# go into every product of their module.
SOURCE_SETS = {
    "wasmJsMain": {WEB},
    "webMain": {WEB},
    "jsMain": {WEB},
    "androidMain": {ADMIN},
    "iosMain": {ADMIN},
    # The desktop app is the admin app.
    "jvmMain": {ADMIN},
}

# Root files of the Gradle build. A change can touch every product.
BUILD_FILES = {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"}

VERSION_LINE = re.compile(r'^(?P<product>web|admin|backend)-version(?P<kind>Name|Code)\s*=\s*"(?P<value>[^"]*)"\s*$', re.M)


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def read_versions(text: str) -> dict[str, dict[str, str]]:
    versions: dict[str, dict[str, str]] = {}
    for match in VERSION_LINE.finditer(text):
        versions.setdefault(match["product"], {})[match["kind"].lower()] = match["value"]
    return versions


def without_versions(text: str) -> str:
    # The line break goes too, so that adding a product's version lines is a change of version lines only.
    # Comment lines go too: they change no build.
    return re.sub(r"^\s*#.*\n?", "", re.sub(VERSION_LINE.pattern + r"\n?", "", text, flags=re.M), flags=re.M)


def parse_name(name: str) -> tuple[int, int, int]:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", name)
    if not match:
        raise ValueError(f"'{name}' is not a MAJOR.MINOR.PATCH version")
    return int(match[1]), int(match[2]), int(match[3])


def is_test_source_set(name: str) -> bool:
    return name == "test" or "Test" in name


def products_of(path: str) -> set[str]:
    """The products that a changed file goes into. Docs, tests, CI and scripts go into none."""
    if path.endswith(".md"):
        return set()
    parts = path.split("/")
    top = parts[0]
    if top in MODULES:
        products = MODULES[top]
        # Test folders outside src, such as playerApp/diagnostics-test, go into no product.
        if len(parts) > 2 and "test" in parts[1].lower():
            return set()
        if len(parts) > 2 and parts[1] == "src":
            source_set = parts[2]
            if is_test_source_set(source_set):
                return set()
            if source_set in SOURCE_SETS:
                products = products & SOURCE_SETS[source_set]
        return set(products)
    if path in BUILD_FILES or (top == "gradle" and path != CATALOG):
        return set(GRADLE_PRODUCTS)
    if top == "firebase":
        # Everything under firebase/ is the backend: functions, Firestore and Storage rules, and indexes.
        if "/test/" in path or re.search(r"\.test\.[cm]?[jt]s$", path):
            return set()
        return {BACKEND}
    if path in ("firebase.json", ".firebaserc"):
        # They configure the hosting of the web and the deploy of the backend.
        return {WEB, BACKEND}
    if top in ("public", "kotlin-js-store"):
        return {WEB}
    return set()


def changed_files(base: str) -> tuple[list[str], str]:
    merge_base = git("merge-base", base, "HEAD").strip()
    return [line for line in git("diff", "--name-only", merge_base, "HEAD").splitlines() if line], merge_base


def check(base: str) -> int:
    files, merge_base = changed_files(base)
    head_text = (ROOT / CATALOG).read_text()
    try:
        base_text = git("show", f"{merge_base}:{CATALOG}")
    except subprocess.CalledProcessError:
        base_text = ""
    head, before = read_versions(head_text), read_versions(base_text)

    required: dict[str, list[str]] = {product: [] for product in PRODUCTS}
    for path in files:
        if path == CATALOG:
            # Only the version lines changed: nothing else to bump.
            if without_versions(head_text) == without_versions(base_text):
                continue
            touched = set(GRADLE_PRODUCTS)
        else:
            touched = products_of(path)
        for product in touched:
            required[product].append(path)

    errors: list[str] = []
    lines: list[str] = []
    for product in PRODUCTS:
        now = head.get(product, {})
        old = before.get(product)
        label = LABELS[product]
        try:
            now_name = parse_name(now.get("name", ""))
            if HAS_CODE[product]:
                int(now.get("code", ""))
        except ValueError as error:
            errors.append(f"{label}: {error} in {CATALOG}.")
            continue
        if old is None:
            lines.append(f"- {label}: {now['name']} (new, nothing to compare)")
            continue
        old_name = parse_name(old["name"])
        name_up = now_name > old_name
        code_up = int(now["code"]) > int(old["code"]) if HAS_CODE[product] else name_up
        show = f"{old['name']} → {now['name']}" if name_up else now["name"]
        if HAS_CODE[product]:
            show += f" (code {old['code']} → {now['code']})" if now["code"] != old["code"] else f" (code {now['code']})"
        if now_name < old_name or (HAS_CODE[product] and int(now["code"]) < int(old["code"])):
            errors.append(f"{label}: the version went down ({show}).")
        elif name_up != code_up:
            errors.append(f"{label}: the version name and the version code must go up together ({show}).")
        elif required[product] and not name_up:
            example = ", ".join(required[product][:3]) + (" …" if len(required[product]) > 3 else "")
            errors.append(
                f"{label}: {len(required[product])} changed file(s) go into it ({example}) "
                f"but its version is still {now['name']}. Run: python3 scripts/versions.py bump {product}"
            )
        state = "bumped" if name_up else ("needs a bump" if required[product] else "no changes")
        lines.append(f"- {label}: {show}, {state}")

    report = "\n".join(lines)
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as out:
            out.write("### Versions\n\n" + report + "\n\n" + "".join(f"- ❌ {e}\n" for e in errors))
    for error in errors:
        print(f"::error title=Version not bumped::{error}" if os.environ.get("GITHUB_ACTIONS") else f"ERROR: {error}")
    return 1 if errors else 0


def bump(product: str) -> int:
    path = ROOT / CATALOG
    text = path.read_text()
    current = read_versions(text)[product]
    # Only the patch goes up here. MAJOR and MINOR are changed by hand in the catalog.
    major, minor, patch = parse_name(current["name"])
    patch += 1
    name = f"{major}.{minor}.{patch}"
    text = re.sub(rf'^({product}-versionName\s*=\s*)"[^"]*"', rf'\g<1>"{name}"', text, flags=re.M)
    message = f"{LABELS[product]}: {current['name']} → {name}"
    if HAS_CODE[product]:
        code = int(current["code"]) + 1
        text = re.sub(rf'^({product}-versionCode\s*=\s*)"[^"]*"', rf'\g<1>"{code}"', text, flags=re.M)
        message += f" (code {current['code']} → {code})"
    path.write_text(text)
    print(message)
    return 0


def show() -> int:
    for product, values in read_versions((ROOT / CATALOG).read_text()).items():
        print(f"{LABELS[product]}: {values['name']}" + (f" (code {values['code']})" if "code" in values else ""))
    return 0


def fetch_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"Cache-Control": "no-cache", "User-Agent": "mts-versions"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def published_web() -> str:
    """The web version that the site serves, from /version.json (written by scripts/assemble-web-hosting.sh)."""
    try:
        return fetch_json(f"{SITE}/version.json").get("web") or "unknown"
    except Exception as error:  # noqa: BLE001 - any failure is shown, not raised
        return f"unknown ({error})"


def published_backend() -> str:
    """The backend version that the API answers on /api/health."""
    try:
        return fetch_json(f"{SITE}/api/health").get("version") or "unknown (deployed before it had a version)"
    except Exception as error:  # noqa: BLE001
        return f"unknown ({error})"


TRACK_LABELS = {"internal": "internal testing", "alpha": "closed testing", "beta": "open testing", "production": "production"}


def published_android(latest_code: str) -> list[str]:
    """The releases of the Android app on each Google Play track, or why they could not be read."""
    try:
        import play
        tracks = play.tracks(play.publisher(), play.PACKAGES[ADMIN])
    except ImportError:
        return ["needs google-auth and google-api-python-client (or run Actions > Check · Published versions)"]
    except Exception as error:  # noqa: BLE001
        return [f"could not read Google Play ({type(error).__name__}: {error})"]
    order = list(TRACK_LABELS)
    lines = []
    for track in sorted(tracks, key=lambda name: (order.index(name) if name in order else len(order), name)):
        for release in tracks[track]:
            codes = release.get("versionCodes", [])
            state = release.get("status", "")
            text = f"{TRACK_LABELS.get(track, track)}: {release.get('name') or ', '.join(codes)}"
            if state != "completed":
                text += f" [{state}]"
            if state == "completed" and latest_code in codes:
                text += "  ✓ up to date"
            lines.append(text)
    return lines or ["nothing on Google Play"]


def main_versions() -> tuple[dict[str, dict[str, str]], str]:
    """The versions on origin/main, or in this checkout when origin/main cannot be read."""
    try:
        git("fetch", "--quiet", "origin", "main")
    except subprocess.CalledProcessError:
        pass
    try:
        return read_versions(git("show", f"origin/main:{CATALOG}")), "main"
    except subprocess.CalledProcessError:
        return read_versions((ROOT / CATALOG).read_text()), "this checkout"


def status() -> int:
    latest, where = main_versions()

    def name(product: str) -> str:
        values = latest.get(product, {})
        return values.get("name", "?") + (f" ({values['code']})" if "code" in values else "")

    def mark(published: str, product: str) -> str:
        if published.startswith("unknown") or published.startswith("could not"):
            return ""
        return "  ✓ up to date" if published == latest.get(product, {}).get("name") else "  ← not published yet"

    web, backend = published_web(), published_backend()
    android = published_android(latest.get(ADMIN, {}).get("code", ""))
    rows = [
        ("Web", name(WEB), [web + mark(web, WEB)]),
        ("Backend", name(BACKEND), [backend + mark(backend, BACKEND)]),
        ("Android app", name(ADMIN), android),
    ]
    print(f"Latest = {where}. Published web and backend = {SITE}.\n")
    width = max(len(row[0]) for row in rows)
    latest_width = max(len(row[1]) for row in rows)
    print(f"{'Product'.ljust(width)}  {'Latest'.ljust(latest_width)}  Published")
    for label, newest, published in rows:
        for index, line in enumerate(published):
            first = index == 0
            print(f"{(label if first else '').ljust(width)}  {(newest if first else '').ljust(latest_width)}  {line}")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as out:
            out.write(f"### Versions\n\nLatest = {where}. Published web and backend = {SITE}.\n\n")
            out.write("| Product | Latest | Published |\n|---|---|---|\n")
            for label, newest, published in rows:
                out.write(f"| {label} | {newest} | {'<br>'.join(published)} |\n")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("show")
    commands.add_parser("status")
    bump_parser = commands.add_parser("bump")
    bump_parser.add_argument("product", choices=PRODUCTS)
    check_parser = commands.add_parser("check")
    check_parser.add_argument("--base", default="origin/main")
    args = parser.parse_args()
    if args.command == "bump":
        return bump(args.product)
    if args.command == "check":
        return check(args.base)
    if args.command == "status":
        return status()
    return show()


if __name__ == "__main__":
    sys.exit(main())
