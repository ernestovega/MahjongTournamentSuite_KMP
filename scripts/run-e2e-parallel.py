#!/usr/bin/env python3
"""Runs the instrumented UI tests of the admin and player parts of the Android app on several emulators at the same time.

Every emulator gets its own local backend (its own Auth and Firestore emulators and API port), because the tests reset
the test-* tournaments and would break each other on a shared backend. The tests go to the emulators from one queue:
an emulator that finishes a group of tests takes the next group, so a slow emulator never holds the run back.

Like scripts/run-e2e-tests.sh, the tests that fail with the Compose harness error ("must be called on the main
thread", ...) run again, up to 4 times. Other failures stay failures.

Usage, from the repository root on the Mac:
  scripts/run-e2e-parallel.py                       # both apps, 3 new emulators of the default AVD
  scripts/run-e2e-parallel.py --app player -n 2
  scripts/run-e2e-parallel.py --serials emulator-5554,emulator-5556   # emulators that already run
  scripts/run-e2e-parallel.py --tests com.etologic.mahjongtournamentsuite.e2e.SignInTest

See docs/instrumented-ui-tests.md. Only the Python standard library is used.
"""
from __future__ import annotations

import argparse
import json
import os
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "e2e-parallel"
FUNCTIONS = ROOT / "firebase" / "functions"

# One Android app holds both parts. Its test APK has the tests of both, told apart by their package ("tests").
MODULES = {
    # The admin part is a desktop style tool: a landscape tablet screen (1280x800 dp, as in CI).
    "admin": {"module": "androidApp", "package": "com.etologic.mahjongtournamentsuite", "tests": "com.etologic.mahjongtournamentsuite.e2e.",
              "size": "1920x1200", "density": "240"},
    # The player part runs on a normal phone screen. On a very small screen the sign-in button leaves the visible area.
    "player": {"module": "androidApp", "package": "com.etologic.mahjongtournamentsuite", "tests": "com.etologic.mahjongtournamentsuite.player.e2e.",
               "size": "1080x2400", "density": "420"},
}
HARNESS_ERRORS = (
    "must be called on the main thread",
    "State must be at least 'CREATED'",
    "performMeasureAndLayout called during measure layout",
    "runDetachLifecycle",
)
MAX_ATTEMPTS = 4
CHUNK = 5  # Tests per group. Small groups spread the work evenly; each group costs a few seconds of start-up.
ORCHESTRATOR = "androidx.test.orchestrator/androidx.test.orchestrator.AndroidTestOrchestrator"
TEST_SERVICES_VERSION = "1.6.0"
API_BASE_PORT = 5060  # Backend i listens on 5060+i, so it never clashes with a backend started by hand on 5055.
EMULATOR_BASE_PORT = 5580

print_lock = threading.Lock()


def log(message: str) -> None:
    with print_lock:
        print(time.strftime("%H:%M:%S"), message, flush=True)


def run(cmd: list[str], check: bool = True, timeout: float | None = None, **kwargs) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, text=True, capture_output=True, timeout=timeout, **kwargs)
    if check and result.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed:\n{result.stdout[-2000:]}\n{result.stderr[-2000:]}")
    return result


def sdk_dir() -> Path:
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key])
    props = ROOT / "local.properties"
    if props.exists():
        match = re.search(r"^sdk\.dir=(.*)$", props.read_text(), re.M)
        if match:
            return Path(match.group(1).strip())
    return Path.home() / "Library" / "Android" / "sdk"


SDK = sdk_dir()
ADB = shutil.which("adb") or str(SDK / "platform-tools" / "adb")
EMULATOR = str(SDK / "emulator" / "emulator")


def adb(serial: str, *args: str, check: bool = True, timeout: float | None = 120) -> str:
    return run([ADB, "-s", serial, *args], check=check, timeout=timeout).stdout


# ---------------------------------------------------------------------------------------------------------------
# Results of `am instrument -r`


@dataclass
class Result:
    test: str  # class#method
    status: str  # PASS, FAIL, SKIP
    message: str = ""
    no_result: bool = False

    @property
    def harness(self) -> bool:
        return self.status == "FAIL" and (self.no_result or any(error in self.message for error in HARNESS_ERRORS))


def parse_instrumentation(output: str) -> list[Result]:
    """Reads the raw output of `am instrument -r`. A value such as `stack=` can go on for many lines."""
    results: list[Result] = []
    values: dict[str, str] = {}
    key = None
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            name, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            key = name
            values[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = line.split(":", 1)[1].strip()
            if code != "1" and values.get("class") and values.get("test"):
                status = {"0": "PASS", "-3": "SKIP", "-4": "SKIP"}.get(code, "FAIL")
                results.append(Result(f"{values['class']}#{values['test']}", status, values.get("stack", "").strip()))
            values, key = {}, None
        elif line.startswith("INSTRUMENTATION_"):
            key = None
        elif key is not None:
            values[key] += "\n" + line
    return results


def short_message(text: str) -> str:
    """The error first, then the app frames of the stack, as in run-e2e-tests.sh."""
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    frames = [line for line in lines if line.startswith("at ")]
    rest = [line for line in lines if not line.startswith("at ")]
    stack = frames[:8] + [frame for frame in frames[8:] if frame.startswith("at com.etologic")][:15]
    return " | ".join((rest[:1] + stack + rest[1:])[:60])[:4000]


# ---------------------------------------------------------------------------------------------------------------
# Local backends


@dataclass
class Backend:
    index: int
    process: subprocess.Popen | None = None

    @property
    def api_port(self) -> int:
        return API_BASE_PORT + self.index

    @property
    def log_path(self) -> Path:
        return OUT / f"backend-{self.index + 1}.log"

    def start(self, java_home: str) -> None:
        base = 9300 + self.index * 10
        config = {
            "firestore": {"database": "(default)", "rules": "firebase/firestore.rules", "indexes": "firebase/firestore.indexes.json"},
            "emulators": {
                "auth": {"host": "127.0.0.1", "port": base},
                "firestore": {"host": "127.0.0.1", "port": base + 1, "websocketPort": base + 2},
                "hub": {"host": "127.0.0.1", "port": base + 3},
                "logging": {"host": "127.0.0.1", "port": base + 4},
                "ui": {"enabled": False},
                "singleProjectMode": True,
            },
        }
        # The Firebase CLI wants the config in the project folder (the repository root). Git ignores these files.
        config_path = ROOT / f"firebase.e2e.parallel-{self.index + 1}.json"
        config_path.write_text(json.dumps(config, indent=2))
        project = f"demo-mts-e2e-{self.index + 1}"
        env = dict(os.environ, E2E_PORT=str(self.api_port), JAVA_HOME=java_home, GCLOUD_PROJECT=project, EMA_FIREBASE_PROJECT=project)
        env["PATH"] = os.pathsep.join([str(ROOT / "node_modules" / ".bin"), str(FUNCTIONS / "node_modules" / ".bin"), env["PATH"]])
        cmd = ["firebase", "emulators:exec", "--config", str(config_path), "--only", "auth,firestore", "--project", project,
               "node lib/scripts/startE2eBackend.js"]
        self.process = subprocess.Popen(cmd, cwd=FUNCTIONS, env=env, stdout=self.log_path.open("w"), stderr=subprocess.STDOUT,
                                        start_new_session=True)

    def wait_ready(self, timeout: float = 600) -> None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            if "E2E_BACKEND_READY" in self.log_path.read_text(errors="replace"):
                return
            if self.process and self.process.poll() is not None:
                break
            time.sleep(2)
        raise RuntimeError(f"Backend {self.index + 1} did not start. See {self.log_path}:\n"
                           + self.log_path.read_text(errors="replace")[-3000:])

    def stop(self) -> None:
        if self.process and self.process.poll() is None:
            try:
                os.killpg(self.process.pid, signal.SIGINT)
                self.process.wait(timeout=30)
            except Exception:
                try:
                    os.killpg(self.process.pid, signal.SIGKILL)
                except Exception:
                    pass
        (ROOT / f"firebase.e2e.parallel-{self.index + 1}.json").unlink(missing_ok=True)


# ---------------------------------------------------------------------------------------------------------------
# Emulators


@dataclass
class Device:
    serial: str
    backend: Backend
    started_here: bool
    process: subprocess.Popen | None = None
    logcat: subprocess.Popen | None = None
    shape: str | None = None
    results: list[Result] = field(default_factory=list)

    def shell(self, command: str, check: bool = False, timeout: float | None = 120) -> str:
        return adb(self.serial, "shell", command, check=check, timeout=timeout)


def free_emulator_ports(count: int) -> list[int]:
    used = set(re.findall(r"emulator-(\d+)", run([ADB, "devices"], check=False).stdout))
    ports, port = [], EMULATOR_BASE_PORT
    while len(ports) < count:
        if str(port) not in used:
            ports.append(port)
        port += 2
    return ports


def pick_avd(wanted: str | None) -> str:
    avds = run([EMULATOR, "-list-avds"]).stdout.split()
    avds = [avd for avd in avds if not avd.startswith("INFO")]
    if wanted:
        if wanted not in avds:
            raise RuntimeError(f"No AVD {wanted}. Found: {', '.join(avds)}")
        return wanted
    for avd in avds:
        if "tablet" in avd.lower():
            return avd
    if not avds:
        raise RuntimeError("No AVD found. Create one in Android Studio (Device Manager).")
    return avds[0]


def boot(device: Device, avd: str, port: int, show: bool) -> None:
    # -read-only lets several emulators of the same AVD run at once. Nothing they do is saved to the AVD.
    cmd = [EMULATOR, "-avd", avd, "-port", str(port), "-read-only", "-no-snapshot-save", "-no-boot-anim", "-no-audio"]
    if not show:
        cmd.append("-no-window")
    device.process = subprocess.Popen(cmd, stdout=(OUT / f"emulator-{port}.log").open("w"), stderr=subprocess.STDOUT,
                                      start_new_session=True)


def wait_booted(device: Device, timeout: float = 420) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if device.process and device.process.poll() is not None:
            raise RuntimeError(f"Emulator {device.serial} stopped. See {OUT}/emulator-*.log")
        # Early in a cold boot adbd can accept the shell and then not answer. That is "not booted yet".
        try:
            if adb(device.serial, "shell", "getprop sys.boot_completed", check=False, timeout=15).strip() == "1":
                return
        except subprocess.TimeoutExpired:
            pass
        time.sleep(3)
    raise RuntimeError(f"Emulator {device.serial} did not boot in {timeout} s.")


def host_time_zone() -> str:
    path = os.path.realpath("/etc/localtime")
    return path.split("zoneinfo/", 1)[1] if "zoneinfo/" in path else "UTC"


def prepare(device: Device, apks: list[Path], packages: list[str]) -> None:
    for command in (
        # The Google password manager shows a "Save password?" window over the app after a sign-in.
        "settings put secure autofill_service null",
        # The error windows of slow system apps must not cover the app.
        "settings put global hide_error_dialogs 1",
        "settings put global window_animation_scale 0",
        "settings put global transition_animation_scale 0",
        "settings put global animator_duration_scale 0",
        # The seed writes round times in the time zone of this computer. The emulator must use the same zone.
        f"service call alarm 3 s16 {host_time_zone()}",
        "mkdir -p /sdcard/Download/e2e",
    ):
        device.shell(command)
    for apk in apks:
        flags = ["-r", "-t", "-g"]
        if "test-services" in apk.name or "orchestrator" in apk.name:
            flags.append("--force-queryable")
        result = run([ADB, "-s", device.serial, "install", *flags, str(apk)], check=False, timeout=300)
        if result.returncode != 0 and "--force-queryable" in flags:
            flags.remove("--force-queryable")
            result = run([ADB, "-s", device.serial, "install", *flags, str(apk)], check=False, timeout=300)
        if result.returncode != 0 or "Failure" in result.stdout:
            raise RuntimeError(f"Install of {apk.name} on {device.serial} failed: {result.stdout} {result.stderr}")
    # The tests look for English texts. The apps follow the device language, so they get an English locale.
    for package in packages:
        device.shell(f"cmd locale set-app-locales {package} --locales en-US")
    device.logcat = subprocess.Popen([ADB, "-s", device.serial, "logcat", "-v", "time"],
                                     stdout=(OUT / f"logcat-{device.serial}.txt").open("w"), stderr=subprocess.STDOUT)


def set_shape(device: Device, app: str) -> None:
    if device.shape == app:
        return
    spec = MODULES[app]
    device.shell(f"wm size {spec['size']}")
    device.shell(f"wm density {spec['density']}")
    device.shape = app
    time.sleep(1)


def instrumentation(device: Device, package: str) -> str:
    for line in device.shell("pm list instrumentation").splitlines():
        match = re.match(r"instrumentation:(\S+) \(target=(\S+)\)", line.strip())
        if match and match.group(2) == package:
            return match.group(1)
    raise RuntimeError(f"No test runner for {package} on {device.serial}.")


def list_tests(device: Device, runner: str, wanted: list[str]) -> list[str]:
    """Asks the runner for the tests without running them (log=true), and keeps the wanted classes or methods."""
    output = device.shell(f"am instrument -r -w -e log true {runner}", timeout=600)
    tests = [result.test for result in parse_instrumentation(output)]
    if not tests:
        raise RuntimeError(f"No tests found with {runner}:\n{output[-3000:]}")
    if wanted:
        tests = [test for test in tests if test in wanted or test.split("#")[0] in wanted]
    return tests


def run_group(device: Device, runner: str, tests: list[str], wait_ms: str | None) -> list[Result]:
    args = [
        f"-e targetInstrumentation {runner}",
        "-e clearPackageData true",
        f"-e e2eBackendUrl http://10.0.2.2:{device.backend.api_port}/api",
        f"-e class {','.join(tests)}",
    ]
    if wait_ms:
        args.append(f"-e e2eWaitMs {wait_ms}")
    command = ("CLASSPATH=$(pm path androidx.test.services) app_process / androidx.test.services.shellexecutor.ShellMain "
               f"am instrument -r -w {' '.join(args)} {ORCHESTRATOR}")
    try:
        output = device.shell(command, timeout=60 * 60)
    except subprocess.TimeoutExpired:
        output = ""
    with (OUT / f"instrument-{device.serial}.txt").open("a") as raw:
        raw.write(f"\n===== {time.strftime('%H:%M:%S')} {','.join(tests)}\n{output}")
    results = {result.test: result for result in parse_instrumentation(output)}
    # A test the runner never reported (a crash of the run, for example) counts as a harness failure, so it runs again.
    for test in tests:
        results.setdefault(test, Result(test, "FAIL", "The run gave no result for this test.", no_result=True))
    return [results[test] for test in tests]


# ---------------------------------------------------------------------------------------------------------------
# Main


def test_tools() -> list[Path]:
    """The AndroidX Test Orchestrator and test services, from the Gradle cache or Google Maven."""
    version = re.search(r'androidx-test-orchestrator = \{[^}]*version = "([^"]+)"', (ROOT / "gradle" / "libs.versions.toml").read_text())
    wanted = [("orchestrator", version.group(1) if version else "1.6.1", "androidx/test/orchestrator"),
              ("test-services", TEST_SERVICES_VERSION, "androidx/test/services/test-services")]
    tools = OUT / "tools"
    tools.mkdir(parents=True, exist_ok=True)
    paths = []
    for name, ver, maven_path in wanted:
        target = tools / f"{name}-{ver}.apk"
        if not target.exists():
            cached = list((Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1").glob(f"androidx.test*/{name}/{ver}/*/{name}-{ver}.apk"))
            if cached:
                shutil.copy(cached[0], target)
            else:
                url = f"https://dl.google.com/android/maven2/{maven_path}/{ver}/{name}-{ver}.apk"
                log(f"Downloading {url}")
                urllib.request.urlretrieve(url, target)
        paths.append(target)
    return paths


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--app", choices=["both", "admin", "player"], default="both")
    parser.add_argument("-n", "--emulators", type=int, default=3, help="How many emulators to start (default 3)")
    parser.add_argument("--avd", help="AVD to start. Default: the first AVD with 'tablet' in its name, else the first AVD")
    parser.add_argument("--serials", help="Use these running emulators instead of starting new ones, comma separated")
    parser.add_argument("--tests", help="Test classes or class#method, comma separated, as in run-e2e-tests.sh")
    parser.add_argument("--show", action="store_true", help="Show the emulator windows")
    parser.add_argument("--no-build", action="store_true", help="Use the APKs of the last build")
    parser.add_argument("--wait-ms", default=os.environ.get("E2E_WAIT_MS"), help="How long a test waits for a screen")
    parser.add_argument("--keep", action="store_true", help="Leave the emulators and backends running at the end")
    options = parser.parse_args()

    apps = ["admin", "player"] if options.app == "both" else [options.app]
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*"):
        if old.is_file() and old.suffix in (".txt", ".log", ".tsv"):
            old.unlink()
    shutil.rmtree(OUT / "screenshots", ignore_errors=True)
    started = time.time()

    serials = [s.strip() for s in options.serials.split(",") if s.strip()] if options.serials else []
    count = len(serials) or options.emulators
    backends = [Backend(index) for index in range(count)]
    devices: list[Device] = []
    try:
        java_home = os.environ.get("E2E_JAVA_HOME") or (
            "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
            if Path("/Library/Java/JavaVirtualMachines/zulu-21.jdk").exists() else os.environ.get("JAVA_HOME", ""))

        # The emulators boot while Gradle and the backends work.
        if serials:
            devices = [Device(serial, backends[i], started_here=False) for i, serial in enumerate(serials)]
        else:
            avd = pick_avd(options.avd)
            log(f"Starting {count} emulators of {avd}.")
            for i, port in enumerate(free_emulator_ports(count)):
                device = Device(f"emulator-{port}", backends[i], started_here=True)
                boot(device, avd, port, options.show)
                devices.append(device)

        log("Building the functions.")
        run(["npm", "run", "build"], cwd=FUNCTIONS)
        for backend in backends:
            backend.start(java_home)
        log(f"Starting {count} local backends on ports {', '.join(str(b.api_port) for b in backends)}.")

        if not options.no_build:
            tasks = list(dict.fromkeys(f":{MODULES[a]['module']}:{t}" for a in apps for t in ("assembleDebug", "assembleDebugAndroidTest")))
            log("Building the apps: " + " ".join(tasks))
            gradle = subprocess.run(["./gradlew", *tasks], cwd=ROOT, stdout=(OUT / "gradle.log").open("w"), stderr=subprocess.STDOUT)
            if gradle.returncode != 0:
                raise RuntimeError(f"Gradle failed. See {OUT / 'gradle.log'}")
        apks = test_tools()
        for module_name in dict.fromkeys(MODULES[a]["module"] for a in apps):
            module = ROOT / module_name / "build" / "outputs" / "apk"
            apks += sorted((module / "debug").glob("*.apk"))[:1] + sorted((module / "androidTest" / "debug").glob("*.apk"))[:1]
        packages = list(dict.fromkeys(MODULES[a]["package"] for a in apps))

        for device in devices:
            wait_booted(device)
        log("Emulators ready. Installing the apps.")
        threads = [threading.Thread(target=prepare, args=(device, apks, packages)) for device in devices]
        [thread.start() for thread in threads]
        [thread.join() for thread in threads]

        for backend in backends:
            backend.wait_ready()
        log("Backends ready.")

        runners = {app: instrumentation(devices[0], MODULES[app]["package"]) for app in apps}
        work: queue.Queue = queue.Queue()
        groups: list[tuple[str, list[str]]] = []
        wanted = [t.strip() for t in (options.tests or "").split(",") if t.strip()]
        for app in apps:
            names = [n for n in list_tests(devices[0], runners[app], wanted) if n.startswith(MODULES[app]["tests"])]
            log(f"{app}: {len(names)} tests.")
            for start in range(0, len(names), CHUNK):
                groups.append((app, names[start:start + CHUNK]))
        if not groups:
            raise RuntimeError(f"No test matches {options.tests}.")
        for group in groups:
            work.put((group[0], group[1], 1))

        final: dict[str, tuple[str, Result]] = {}
        final_lock = threading.Lock()
        pending = [len(groups)]

        def worker(device: Device) -> None:
            while True:
                with final_lock:
                    if pending[0] == 0:
                        return
                try:
                    app, tests, attempt = work.get(timeout=2)
                except queue.Empty:
                    continue
                try:
                    set_shape(device, app)
                    results = run_group(device, runners[app], tests, options.wait_ms)
                except Exception as error:  # A lost emulator must not stop the run: the group goes to another one.
                    log(f"{device.serial} stops taking tests: {error}")
                    work.put((app, tests, attempt))
                    return
                again = [r.test for r in results if r.harness]
                with final_lock:
                    for result in results:
                        final[result.test] = (app, result)
                    passed = sum(r.status == "PASS" for r in results)
                    log(f"{device.serial} {app} {passed}/{len(results)} passed (try {attempt}) {tests[0].rsplit('.', 1)[-1]}...")
                    if again and attempt < MAX_ATTEMPTS:
                        log(f"{device.serial} starting again after the harness error: {', '.join(t.rsplit('.', 1)[-1] for t in again)}")
                        pending[0] += 1
                        work.put((app, again, attempt + 1))
                    pending[0] -= 1

        threads = [threading.Thread(target=worker, args=(device,), daemon=True) for device in devices]
        [thread.start() for thread in threads]
        [thread.join() for thread in threads]
        while not work.empty():  # Every emulator was lost before the end.
            app, tests, _ = work.get()
            for test in tests:
                final.setdefault(test, (app, Result(test, "FAIL", "No emulator was left to run this test.")))

        for device in devices:
            target = OUT / "screenshots" / device.serial
            target.mkdir(parents=True, exist_ok=True)
            adb(device.serial, "pull", "/sdcard/Download/e2e/.", str(target), check=False, timeout=300)

        rows = sorted(final.items())
        with (OUT / "results.tsv").open("w") as tsv:
            for test, (app, result) in rows:
                tsv.write(f"{app}\t{test}\t{result.status}\t{short_message(result.message)}\n")
        failed = [(test, app, result) for test, (app, result) in rows if result.status == "FAIL"]
        for test, app, result in failed:
            print(f"FAILED {app} {test}\n    {short_message(result.message)}")
        passed = sum(1 for _, (_, r) in rows if r.status == "PASS")
        minutes = (time.time() - started) / 60
        print(f"{passed} passed, {len(failed)} failed, in {minutes:.1f} min on {count} emulators. Details in {OUT}")
        return 0 if not failed and passed > 0 else 1
    finally:
        if not options.keep:
            for device in devices:
                if device.logcat:
                    device.logcat.terminate()
                if device.started_here:
                    run([ADB, "-s", device.serial, "emu", "kill"], check=False, timeout=30)
                else:
                    device.shell("wm size reset")
                    device.shell("wm density reset")
            for backend in backends:
                backend.stop()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)
