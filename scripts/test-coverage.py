#!/usr/bin/env python3
"""Writes the table of tests per part of the code in docs/test-scenarios-coverage.md.

For each part of the code the table gives its size, the number of tests of each kind, and the lines and branches that
the tests run. The counts come from the test sources, so they are always quick to get. The coverage comes from a
measure: Kover for the Kotlin modules (JVM tests) and the coverage of Node for the backend (unit and integration tests
on the emulators). The last measure stays in docs/test-coverage.json, so a run without --measure keeps it.

Usage, from the repository root:
  python3 scripts/test-coverage.py              # counts the tests, keeps the last measured coverage
  python3 scripts/test-coverage.py --measure    # runs the tests with coverage first (about 15 min, Java 21)
  python3 scripts/test-coverage.py --check      # fails when the counts in the doc are not the counts of the code

See docs/test-scenarios-coverage.md. Only the Python standard library is used.
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs" / "test-scenarios-coverage.md"
MEASURE_FILE = ROOT / "docs" / "test-coverage.json"
FUNCTIONS = ROOT / "firebase" / "functions"
START = "<!-- test-table:start -->"
END = "<!-- test-table:end -->"

KOTLIN_MODULES = ["domain", "data", "designsystem", "ui", "adminApp", "playerApp"]
KINDS = ["unit", "ui", "screenshot", "integration", "android_e2e", "web_e2e"]

# The parts of the code, in the order of the table: (key, name in the table).
PARTS = [
    ("domain", "`domain`: business rules, use cases"),
    ("data", "`data`: repositories, network, telemetry"),
    ("designsystem", "`designsystem`: theme, base components"),
    ("ui", "`ui`: shared Compose components"),
    ("adminApp", "`adminApp`: admin screens"),
    ("playerApp", "`playerApp`: player screens"),
    ("androidApp", "`androidApp`: Android entry point"),
    ("backend_api", "Backend: API routes and middleware"),
    ("backend_services", "Backend: services"),
    ("backend_other", "Backend: models, clients, setup"),
    ("backend_scripts", "Backend: maintenance and seed scripts"),
    ("rules", "Firestore and Storage rules (deny all)"),
    ("scripts", "Repository scripts (Game day, smoke, versions)"),
]

TEST_LINE = re.compile(r"^\s*@Test\b", re.M)
JS_TEST_LINE = re.compile(r"^\s*(?:await\s+)?(?:it|test)(?:\.only)?\(", re.M)
PY_TEST_LINE = re.compile(r"^\s*def test_", re.M)
UI_MARKERS = ("runComposeUiTest", "createComposeRule", "runDesktopComposeUiTest", "ComposeUiTest")
SCREENSHOT_MARKERS = ("captureRoboImage", "captureScreenshot", "Roborazzi")


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return ""


def code_lines(paths) -> int:
    return sum(sum(1 for line in read(path).splitlines() if line.strip()) for path in paths)


def files(base: Path, pattern: str) -> list[Path]:
    return [path for path in base.glob(pattern) if "/build/" not in str(path) and "node_modules" not in str(path)]


# Parts whose lines and branches run do not count in the total. See the notes under the table.
UNCOUNTED_PARTS = {"backend_scripts"}


def backend_part(relative: str) -> str:
    """The part of a file of firebase/functions/src (or lib), by its folder."""
    if relative.startswith("api/"):
        return "backend_api"
    if relative.startswith("services/"):
        return "backend_services"
    if relative.startswith("scripts/"):
        return "backend_scripts"
    return "backend_other"


def count() -> tuple[dict, dict]:
    """The number of tests of each kind, and the lines of code, of each part."""
    tests: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    lines: dict[str, int] = defaultdict(int)

    for module in KOTLIN_MODULES:
        base = ROOT / module / "src"
        main = [path for path in files(base, "*/kotlin/**/*.kt") if not path.relative_to(base).parts[0].endswith("Test")]
        lines[module] = code_lines(main)
        for path in files(base, "*Test/kotlin/**/*.kt"):
            text = read(path)
            number = len(TEST_LINE.findall(text))
            if not number:
                continue
            if path.name.endswith("ScreenshotTest.kt") or any(marker in text for marker in SCREENSHOT_MARKERS):
                kind = "screenshot"
            elif any(marker in text for marker in UI_MARKERS):
                kind = "ui"
            else:
                kind = "unit"
            tests[module][kind] += number

    # The Android app: its own code, and the instrumented tests of the admin part and of the player part.
    lines["androidApp"] = code_lines(files(ROOT / "androidApp" / "src" / "main", "**/*.kt"))
    for path in files(ROOT / "androidApp" / "src" / "androidTest", "**/*.kt"):
        number = len(TEST_LINE.findall(read(path)))
        tests["playerApp" if "/player/e2e/" in str(path) else "adminApp"]["android_e2e"] += number
    for path in files(ROOT / "androidApp" / "src" / "test", "**/*.kt"):
        tests["androidApp"]["unit"] += len(TEST_LINE.findall(read(path)))

    # The web apps in Chromium (Playwright): the admin files start with "admin-".
    for path in files(ROOT / "e2e" / "web" / "tests", "*.test.mjs"):
        tests["adminApp" if path.name.startswith("admin-") else "playerApp"]["web_e2e"] += len(JS_TEST_LINE.findall(read(path)))
    # The scripts of the player page (start diagnostics), tested with Node.
    for path in files(ROOT / "playerApp" / "diagnostics-test", "*.test.mjs"):
        tests["playerApp"]["unit"] += len(JS_TEST_LINE.findall(read(path)))

    # The backend: unit tests (*.test.ts) and integration tests on the emulators (*.integration.test.ts).
    source = FUNCTIONS / "src"
    for path in files(source, "**/*.ts"):
        relative = str(path.relative_to(source))
        if path.name == "securityRules.test.ts":
            tests["rules"]["unit"] += len(JS_TEST_LINE.findall(read(path)))
        elif path.name.endswith(".integration.test.ts"):
            tests[backend_part(relative)]["integration"] += len(JS_TEST_LINE.findall(read(path)))
        elif path.name.endswith(".test.ts"):
            tests[backend_part(relative)]["unit"] += len(JS_TEST_LINE.findall(read(path)))
        elif not relative.startswith("testing/"):
            lines[backend_part(relative)] += code_lines([path])
    lines["rules"] = code_lines([path for path in ROOT.glob("*.rules")] + files(ROOT / "firebase", "*.rules"))

    # The scripts of the repository and their tests.
    scripts = ROOT / "scripts"
    script_files = [path for path in files(scripts, "**/*") if path.suffix in (".py", ".mjs", ".sh") and path.is_file()]
    lines["scripts"] = code_lines([path for path in script_files if ".test." not in path.name and not path.name.endswith("_test.py") and "/test/" not in str(path)])
    for path in script_files:
        if path.name.endswith(".test.mjs"):
            tests["scripts"]["unit"] += len(JS_TEST_LINE.findall(read(path)))
        elif path.name.endswith("_test.py"):
            tests["scripts"]["unit"] += len(PY_TEST_LINE.findall(read(path)))
    return tests, lines


# ---------------------------------------------------------------------------------------------------------------
# Measure


def run(cmd: list[str], cwd: Path, env: dict | None = None) -> None:
    print("$", " ".join(cmd), flush=True)
    result = subprocess.run(cmd, cwd=cwd, env=env)
    if result.returncode != 0:
        sys.exit(f"Failed: {' '.join(cmd)}")


def module_of(source: str, cache: dict) -> str | None:
    """The Kotlin module whose main sources hold this file (package path / file name)."""
    if source not in cache:
        cache[source] = next(
            (module for module in KOTLIN_MODULES for path in (ROOT / module / "src").glob(f"*/kotlin/{source}")
             if not path.relative_to(ROOT / module / "src").parts[0].endswith("Test")),
            None,
        )
    return cache[source]


def measure_kotlin() -> dict:
    """The merged Kover report of the JVM tests: code of one module run by the tests of another module counts too.
    Each source file goes to the module that holds it."""
    run(["./gradlew", "koverXmlReportCoverage", "--continue"], ROOT)
    report = ROOT / "build" / "reports" / "kover" / "reportCoverage.xml"
    totals: dict[str, dict[str, list[int]]] = defaultdict(lambda: {"line": [0, 0], "branch": [0, 0]})
    cache: dict[str, str | None] = {}
    for package in ET.parse(report).getroot().iter("package"):
        for sourcefile in package.findall("sourcefile"):
            module = module_of(f"{package.get('name')}/{sourcefile.get('name')}", cache)
            if module is None:
                continue
            for counter in sourcefile.findall("counter"):
                kind = {"LINE": "line", "BRANCH": "branch"}.get(counter.get("type"))
                if kind:
                    covered, missed = int(counter.get("covered")), int(counter.get("missed"))
                    totals[module][kind][0] += covered
                    totals[module][kind][1] += covered + missed
    return dict(totals)


def measure_backend() -> dict:
    """Runs the unit and integration tests of the backend on the emulators with coverage, as `npm run test:coverage`,
    and adds the lines and branches of each part from the lcov report."""
    lcov = FUNCTIONS / "coverage.lcov"
    lcov.unlink(missing_ok=True)
    tests = (
        "node --test --test-concurrency=1 --experimental-test-coverage "
        "--test-coverage-exclude='lib/**/*.test.js' --test-coverage-exclude='lib/testing/**' "
        "--test-reporter=dot --test-reporter-destination=stdout "
        "--test-reporter=lcov --test-reporter-destination=coverage.lcov 'lib/**/*.test.js'"
    )
    run(["npm", "run", "build"], FUNCTIONS)
    firebase = shutil.which("firebase") or str(ROOT / "node_modules" / ".bin" / "firebase")
    run([firebase, "emulators:exec", "--config", "../../firebase.json", "--only", "auth,firestore",
         "--project", "demo-mts-tests", tests], FUNCTIONS)
    totals: dict[str, dict[str, list[int]]] = defaultdict(lambda: {"line": [0, 0], "branch": [0, 0]})
    part = None
    for line in read(lcov).splitlines():
        if line.startswith("SF:"):
            path = line[3:]
            relative = re.sub(r"^(?:.*/)?lib/", "", path)
            part = backend_part(relative)
        elif part and line[:3] in ("LH:", "LF:", "BRH", "BRF"):
            key, value = line.split(":", 1)
            kind = "line" if key.startswith("L") else "branch"
            index = 0 if key.endswith("H") else 1
            totals[part][kind][index] += int(value)
    lcov.unlink(missing_ok=True)
    return dict(totals)


# ---------------------------------------------------------------------------------------------------------------
# Table


def percent(pair) -> str:
    if not pair or not pair[1]:
        return "–"
    return f"{100 * pair[0] / pair[1]:.0f} %"


def number(value: int) -> str:
    return f"{value:,}" if value else "–"


def table(tests: dict, lines: dict, measured: dict) -> str:
    coverage = measured.get("parts", {})
    rows = [
        "| Part | Code lines | Unit | UI (JVM) | Screenshot | Integration | Android E2E | Web E2E | Lines run | Branches run |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    total = defaultdict(int)
    for key, name in PARTS:
        counts = tests.get(key, {})
        for kind in KINDS:
            total[kind] += counts.get(kind, 0)
        total["lines"] += lines.get(key, 0)
        part = coverage.get(key, {})
        rows.append(
            f"| {name} | {number(lines.get(key, 0))} | "
            + " | ".join(number(counts.get(kind, 0)) for kind in KINDS)
            + f" | {percent(part.get('line'))} | {percent(part.get('branch'))} |"
        )
    # The maintenance and seed scripts run by hand once, so the total of lines and branches run leaves them out.
    counted = [part for key, part in coverage.items() if key not in UNCOUNTED_PARTS]
    all_line = [sum(part.get("line", [0, 0])[i] for part in counted) for i in (0, 1)]
    all_branch = [sum(part.get("branch", [0, 0])[i] for part in counted) for i in (0, 1)]
    rows.append(
        f"| **Total** | **{number(total['lines'])}** | "
        + " | ".join(f"**{number(total[kind])}**" for kind in KINDS)
        + f" | **{percent(all_line)}** | **{percent(all_branch)}** |"
    )
    when = measured.get("date")
    source = (
        f"Coverage measured on {when} at `{measured.get('commit')}` with `python3 scripts/test-coverage.py --measure`."
        if when else "Coverage not measured yet: run `python3 scripts/test-coverage.py --measure`."
    )
    return "\n".join([
        START,
        "<!-- Written by scripts/test-coverage.py. Do not edit by hand. -->",
        "",
        "\n".join(rows),
        "",
        "- **Counts** are the tests in the sources of each kind. A screenshot test can record several images, and an",
        "  Android or Web E2E test of the admin or player part counts in `adminApp` or `playerApp`.",
        "- **Kinds:** Unit = Kotlin `commonTest`/`jvmTest` without Compose, and Node or Python tests. UI (JVM) = Compose UI",
        "  tests on the JVM. Screenshot = Roborazzi. Integration = backend tests on the Auth and Firestore emulators.",
        "  Android E2E = `androidApp/src/androidTest` on emulators. Web E2E = Playwright on the Wasm apps.",
        "- **Lines run and branches run** come from Kover (JVM tests of each Kotlin module) and from the coverage of Node",
        "  (backend unit and integration tests, on the compiled JavaScript). They do not count the Android and Web E2E tests,",
        "  `androidMain`, `wasmJsMain` or `androidApp`. A dash means not measured.",
        "- **The total of lines and branches run** leaves out the maintenance and seed scripts of the backend: they run",
        "  by hand, once or on test data, and their row shows how much of them the tests run.",
        f"- {source}",
        END,
    ])


def write_doc(text: str) -> None:
    doc = read(DOC)
    if START in doc and END in doc:
        before, rest = doc.split(START, 1)
        after = rest.split(END, 1)[1]
        doc = before + text + after
    else:
        marker = "## 1. Kinds of tests"
        section = "## 0. Tests per part of the code\n\n" + text + "\n\n"
        doc = doc.replace(marker, section + marker, 1) if marker in doc else doc + "\n" + section
    DOC.write_text(doc, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--measure", action="store_true", help="run the tests with coverage first")
    parser.add_argument("--check", action="store_true", help="fail when the table in the doc is not up to date")
    args = parser.parse_args()

    measured = json.loads(read(MEASURE_FILE) or "{}")
    if args.measure:
        parts = measure_kotlin()
        parts.update(measure_backend())
        commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True, text=True).stdout.strip()
        measured = {"date": datetime.date.today().isoformat(), "commit": commit, "parts": parts}
        MEASURE_FILE.write_text(json.dumps(measured, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    tests, lines = count()
    text = table(tests, lines, measured)
    if args.check:
        current = read(DOC)
        if text not in current:
            sys.exit("The table of docs/test-scenarios-coverage.md is not up to date: run python3 scripts/test-coverage.py")
        print("The table is up to date.")
        return
    write_doc(text)
    print(text)


if __name__ == "__main__":
    os.chdir(ROOT)
    main()
