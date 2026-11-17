#!/usr/bin/env python3
"""The gallery of the screenshot tests and the game day videos (docs/screenshot-gallery.md).

    python3 scripts/gallery/gallery.py build   [--out DIR] [--previous URL_OR_FILE] [--versions N]
    python3 scripts/gallery/gallery.py videos  --out DIR --previous ... (needs GITHUB_TOKEN, GITHUB_REPOSITORY)
    python3 scripts/gallery/gallery.py publish --out DIR --site SITE --project PROJECT (needs an access token)

build: the history of the reference images comes from git (the last N commits of main that changed one). Every
image is stored once, by its Git LFS id: img/<id>.png (the original) and thumb/<id>.webp (small, for the lists).
Only the ids that the site does not have yet are downloaded from Git LFS, so a run costs little LFS bandwidth.

videos: the screen recordings of the last game days (the organizer tablet of Actions > Game day), compressed to a
few MB each and kept for the last KEEP_GAME_DAYS runs.

publish: Firebase Hosting through its REST API. A new version lists every file by the hash of its content, and
Hosting asks only for the files it does not have, so the images and videos already published are never uploaded
again and the runner does not need them.
"""
import argparse
import gzip
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent

MODULES = [
    # id, label, main code that changes what its screenshots show
    ("adminApp", "Admin", ["adminApp", "ui", "designsystem"]),
    ("playerApp", "Player", ["playerApp", "ui", "designsystem"]),
    ("ui", "UI", ["ui", "designsystem"]),
    ("designsystem", "Design system", ["designsystem"]),
]
DEFAULT_VERSIONS = 30
KEEP_GAME_DAYS = 3
GAME_DAY_WORKFLOW = "scenario.yml"
VIDEO_ARTIFACTS = re.compile(r"^organizer-")
THUMB_HEIGHT = 440
LFS_POINTER = re.compile(rb"^version https://git-lfs.github.com/spec/v1\noid sha256:([0-9a-f]{64})\nsize (\d+)\n")


def log(*args):
    print(*args, file=sys.stderr, flush=True)


def git(*args, binary=False, input=None):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=True, input=input)
    return result.stdout if binary else result.stdout.decode().strip()


# ---------------------------------------------------------------- previous site


def load_previous(source):
    """The manifest of the published site, or an empty one."""
    if not source:
        return {}
    try:
        if source.startswith("http"):
            with urllib.request.urlopen(source.rstrip("/") + "/manifest.json", timeout=30) as response:
                return json.load(response)
        return json.loads(Path(source).read_text())
    except (OSError, ValueError, urllib.error.URLError) as error:
        log(f"No previous manifest ({error}); starting a new one.")
        return {}


# ---------------------------------------------------------------- history of the images


def image_key(path):
    """adminApp/screenshots/dialogs/x/phone-dark.png -> adminApp/dialogs/x/phone-dark"""
    module, _, rest = path.partition("/screenshots/")
    return f"{module}/{rest[:-4]}"


def tree_oids(commit, blob_cache):
    """{image key: LFS id} of the reference images at a commit, read from the pointers (no LFS download)."""
    listing = git("ls-tree", "-r", "-z", commit, binary=True).split(b"\0")
    entries = []
    for line in listing:
        if not line:
            continue
        meta, path = line.decode().split("\t", 1)
        if "/screenshots/" not in path or not path.endswith(".png") or path.split("/")[0] not in {m[0] for m in MODULES}:
            continue
        entries.append((path, meta.split()[2]))
    missing = [blob for _, blob in entries if blob not in blob_cache]
    if missing:
        out = git("cat-file", "--batch", binary=True, input=("\n".join(missing) + "\n").encode())
        pos = 0
        for blob in missing:
            header_end = out.index(b"\n", pos)
            size = int(out[pos:header_end].split()[2])
            content = out[header_end + 1 : header_end + 1 + size]
            pos = header_end + 1 + size + 1
            match = LFS_POINTER.match(content)
            blob_cache[blob] = (match.group(1).decode(), int(match.group(2))) if match else None
    result = {}
    for path, blob in entries:
        if blob_cache.get(blob):
            result[image_key(path)] = blob_cache[blob]
    return result


def versions_at(commit):
    try:
        toml = git("show", f"{commit}:gradle/libs.versions.toml")
    except subprocess.CalledProcessError:
        return {}
    found = dict(re.findall(r'^(web|admin|backend)-versionName\s*=\s*"([^"]+)"', toml, re.M))
    return {"web": found.get("web"), "app": found.get("admin"), "backend": found.get("backend")}


def build_history(head, count):
    commits = git(
        "log", "--first-parent", f"-{count}", "--format=%H%x1f%cI%x1f%s", head, "--", "*/screenshots/**"
    ).splitlines()
    versions = []
    states = []
    blob_cache = {}
    sizes = {}
    for line in commits:
        sha, date, subject = line.split("\x1f")
        state = tree_oids(sha, blob_cache)
        for key, (oid, size) in state.items():
            sizes[oid] = size
        states.append({key: oid for key, (oid, size) in state.items()})
        if subject.startswith("Merge "):
            # A merge says nothing; the last commit that it brought says what changed.
            try:
                merged = git("log", "-1", "--no-merges", "--format=%s", f"{sha}^1..{sha}^2")
                if merged:
                    subject = f"{merged} (merge)"
            except subprocess.CalledProcessError:
                pass
        versions.append({"sha": sha, "date": date, "subject": subject, **versions_at(sha)})
    # images[key] = [[version index, id or null], ...]: the versions (newest first) where the image changed. The
    # oldest version kept is the starting point, so every image it has gets an entry there.
    images = {}
    keys = set().union(*states) if states else set()
    for key in sorted(keys):
        entries = []
        for index, state in enumerate(states):
            current = state.get(key)
            older = states[index + 1].get(key) if index + 1 < len(states) else "start"
            if current != older and not (older == "start" and current is None):
                entries.append([index, current])
        images[key] = entries
    for index, version in enumerate(versions):
        changes = [key for key, entries in images.items() if any(e[0] == index for e in entries)]
        version["changed"] = len(changes) if index + 1 < len(versions) else 0
    oids = {oid for state in states for oid in state.values()}
    return versions, images, oids, sizes


def staleness(head):
    """For each module: commits to its main code after its images last changed (a hint that they may be old)."""
    result = []
    for module, label, code in MODULES:
        last = git("log", "-1", "--format=%H%x1f%cI%x1f%s", head, "--", f"{module}/screenshots")
        if not last:
            continue
        sha, date, subject = last.split("\x1f")
        paths = [f":(glob){c}/src/*Main/**" for c in code] + ([":(glob)screenshotTesting/src/**"] if module.endswith("App") else [])
        after = git("log", "--format=%H%x1f%cI%x1f%s", f"{sha}..{head}", "--", *paths).splitlines()
        item = {"id": module, "label": label, "imagesSha": sha, "imagesDate": date, "codeCommitsAfter": len(after)}
        if after:
            last_sha, last_date, last_subject = after[0].split("\x1f")
            item["lastCode"] = {"sha": last_sha, "date": last_date, "subject": last_subject}
        result.append(item)
    return result


# ---------------------------------------------------------------- GitHub


def github(path, raw=False):
    token = os.environ.get("GITHUB_TOKEN")
    url = path if path.startswith("http") else f"https://api.github.com{path}"
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    if raw:
        # Artifacts redirect to a storage URL that refuses the GitHub token: follow it without the token.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs):
                return None

        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code not in (301, 302, 303, 307, 308):
                raise
            with urllib.request.urlopen(error.headers["Location"], timeout=600) as response:
                return response.read()
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def last_verify(repo, head):
    try:
        runs = github(f"/repos/{repo}/actions/workflows/screenshots.yml/runs?per_page=10&status=completed")["workflow_runs"]
    except (urllib.error.URLError, KeyError, OSError) as error:
        log(f"No screenshot test runs ({error}).")
        return None
    for run in runs:
        if run.get("conclusion") in ("success", "failure"):
            behind = None
            try:
                behind = int(git("rev-list", "--count", f"{run['head_sha']}..{head}"))
            except subprocess.CalledProcessError:
                pass
            return {
                "conclusion": run["conclusion"],
                "sha": run["head_sha"],
                "date": run["created_at"],
                "url": run["html_url"],
                "branch": run.get("head_branch"),
                "commitsBehind": behind,
            }
    return None


def lfs_download(repo, oids, sizes, dest):
    """Downloads Git LFS objects by id with the batch API. Returns {id: path}."""
    if not oids:
        return {}
    token = os.environ.get("GITHUB_TOKEN")
    headers = {"Accept": "application/vnd.git-lfs+json", "Content-Type": "application/vnd.git-lfs+json"}
    if token:
        import base64

        headers["Authorization"] = "Basic " + base64.b64encode(f"x-access-token:{token}".encode()).decode()
    result = {}
    todo = sorted(oids)
    for start in range(0, len(todo), 100):
        chunk = todo[start : start + 100]
        body = {"operation": "download", "transfers": ["basic"], "objects": [{"oid": o, "size": sizes[o]} for o in chunk]}
        request = urllib.request.Request(
            f"https://github.com/{repo}.git/info/lfs/objects/batch", data=json.dumps(body).encode(), headers=headers
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            objects = json.load(response)["objects"]

        def fetch(obj):
            action = obj.get("actions", {}).get("download")
            if not action:
                log(f"LFS object {obj['oid']} is not available: {obj.get('error')}")
                return None
            req = urllib.request.Request(action["href"], headers=action.get("header", {}))
            for attempt in range(4):
                try:
                    with urllib.request.urlopen(req, timeout=120) as response:
                        data = response.read()
                    break
                except (urllib.error.URLError, OSError):
                    if attempt == 3:
                        raise
                    time.sleep(2 ** attempt)
            if hashlib.sha256(data).hexdigest() != obj["oid"]:
                raise RuntimeError(f"LFS object {obj['oid']} has the wrong content")
            path = dest / f"{obj['oid']}.png"
            path.write_bytes(data)
            return obj["oid"], path

        with ThreadPoolExecutor(8) as pool:
            for item in pool.map(fetch, objects):
                if item:
                    result[item[0]] = item[1]
    return result


# ---------------------------------------------------------------- build


def local_lfs_object(oid):
    path = ROOT / ".git" / "lfs" / "objects" / oid[:2] / oid[2:4] / oid
    return path if path.exists() else None


def make_thumb(png, target):
    from PIL import Image

    with Image.open(png) as image:
        image = image.convert("RGB")
        if image.height > THUMB_HEIGHT:
            image = image.resize((max(1, round(image.width * THUMB_HEIGHT / image.height)), THUMB_HEIGHT), Image.LANCZOS)
        image.save(target, "WEBP", quality=72, method=6)


def command_build(args):
    out = Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    (out / "img").mkdir(parents=True)
    (out / "thumb").mkdir()
    previous = load_previous(args.previous)
    have = set(previous.get("files", {}).get("img", []))
    repo = os.environ.get("GITHUB_REPOSITORY", "ernestovega/mahjongtournamentsuite_kmp")
    head = git("rev-parse", args.head)

    versions, images, oids, sizes = build_history(head, args.versions)
    log(f"{len(versions)} versions, {len(images)} images, {len(oids)} different contents, {len(have & oids)} already published.")

    # The contents the site lacks: from the local LFS store when they are there, otherwise from GitHub.
    need = sorted(oids - have)
    found = {oid: local_lfs_object(oid) for oid in need}
    remote = [oid for oid, path in found.items() if path is None]
    with tempfile.TemporaryDirectory() as tmp:
        if remote:
            log(f"Downloading {len(remote)} images from Git LFS ({sum(sizes[o] for o in remote) / 1e6:.1f} MB).")
            found.update(lfs_download(repo, set(remote), sizes, Path(tmp)))
        for oid in need:
            if found.get(oid) is None:
                sys.exit(f"Image {oid} could not be downloaded.")
            shutil.copyfile(found[oid], out / "img" / f"{oid}.png")
            make_thumb(found[oid], out / "thumb" / f"{oid}.webp")

    manifest = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "repo": repo,
        "head": head,
        "verify": last_verify(repo, head),
        "modules": staleness(head),
        "versions": versions,
        "images": images,
        "videos": previous.get("videos", []),
        "files": {"img": sorted(oids), "videos": previous.get("files", {}).get("videos", [])},
    }
    (out / "manifest.json").write_text(json.dumps(manifest, separators=(",", ":")))
    shutil.copyfile(HERE / "index.html", out / "index.html")
    log(f"Built {out}: {len(need)} new images.")


# ---------------------------------------------------------------- videos


def ffprobe_duration(path):
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        return round(float(out))
    except (subprocess.CalledProcessError, ValueError):
        return None


def compress_video(pieces, target, poster):
    """Joins the 3 minute pieces and keeps only the frames that change (variable frame rate): a screen that stays still
    costs almost nothing, and the times stay real."""
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as listing:
        for piece in pieces:
            listing.write(f"file '{piece}'\n")
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", listing.name,
         "-vf", "fps=10,mpdecimate=max=50,scale='min(960,iw)':-2", "-fps_mode", "vfr", "-an",
         "-c:v", "libx264", "-preset", "slow", "-crf", "32", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
         str(target)],
        check=True,
    )
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-ss", "5", "-i", str(target), "-frames:v", "1",
         "-vf", "scale=480:-2", "-q:v", "6", str(poster)],
        check=False,
    )
    os.unlink(listing.name)


def command_videos(args):
    out = Path(args.out)
    manifest_path = out / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    repo = manifest["repo"]
    known = {v["run"]: v for v in manifest.get("videos", [])}
    try:
        runs = github(f"/repos/{repo}/actions/workflows/{GAME_DAY_WORKFLOW}/runs?per_page=10&status=completed")["workflow_runs"]
    except (urllib.error.URLError, KeyError, OSError) as error:
        log(f"No game day runs ({error}).")
        runs = []
    for run in runs[:KEEP_GAME_DAYS]:
        if run["id"] in known:
            continue
        entry = {
            "run": run["id"], "number": run["run_number"], "title": run.get("display_title") or run["name"],
            "date": run["created_at"], "conclusion": run.get("conclusion"), "sha": run["head_sha"],
            "url": run["html_url"], "items": [],
        }
        artifacts = github(f"/repos/{repo}/actions/runs/{run['id']}/artifacts?per_page=100").get("artifacts", [])
        for artifact in sorted(artifacts, key=lambda a: a["name"]):
            if not VIDEO_ARTIFACTS.match(artifact["name"]) or artifact.get("expired"):
                continue
            log(f"Game day {run['run_number']}: {artifact['name']} ({artifact['size_in_bytes'] / 1e6:.0f} MB)")
            with tempfile.TemporaryDirectory() as tmp:
                data = github(artifact["archive_download_url"], raw=True)
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    archive.extractall(tmp)
                del data
                pieces = sorted(p for p in Path(tmp).rglob("*.mp4") if p.stat().st_size > 0)
                if not pieces:
                    continue
                folder = out / "videos" / str(run["id"])
                folder.mkdir(parents=True, exist_ok=True)
                name = artifact["name"]
                target, poster = folder / f"{name}.mp4", folder / f"{name}.jpg"
                compress_video(pieces, target, poster)
                entry["items"].append({
                    "name": name,
                    "src": f"videos/{run['id']}/{name}.mp4",
                    "poster": f"videos/{run['id']}/{name}.jpg" if poster.exists() else None,
                    "duration": ffprobe_duration(target),
                    "size": target.stat().st_size,
                })
                log(f"  -> {target.stat().st_size / 1e6:.1f} MB")
        known[run["id"]] = entry
    kept = sorted(known.values(), key=lambda v: v["date"], reverse=True)[:KEEP_GAME_DAYS]
    manifest["videos"] = kept
    manifest["files"]["videos"] = sorted(
        path for v in kept for item in v["items"] for path in (item["src"], item["poster"]) if path
    )
    manifest_path.write_text(json.dumps(manifest, separators=(",", ":")))


# ---------------------------------------------------------------- publish

HOSTING = "https://firebasehosting.googleapis.com/v1beta1"


def wanted_paths(manifest):
    paths = {"index.html", "manifest.json"}
    for oid in manifest["files"]["img"]:
        paths.add(f"img/{oid}.png")
        paths.add(f"thumb/{oid}.webp")
    paths.update(manifest["files"].get("videos", []))
    return paths


def hosting(method, path, token, body=None, project=None):
    url = path if path.startswith("http") else f"{HOSTING}/{path}"
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"Authorization": f"Bearer {token}"})
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if project:
        request.add_header("x-goog-user-project", project)
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            text = response.read()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{method} {url}: {error.code} {error.read().decode()[:500]}") from None


def command_publish(args):
    token = os.environ.get("HOSTING_TOKEN") or subprocess.run(
        ["gcloud", "auth", "print-access-token"], capture_output=True, text=True, check=True
    ).stdout.strip()
    out = Path(args.out)
    manifest = json.loads((out / "manifest.json").read_text())
    site = args.site

    try:
        hosting("GET", f"projects/{args.project}/sites/{site}", token, project=args.project)
    except RuntimeError as error:
        if ": 404 " not in str(error):
            raise
        log(f"Creating the Hosting site {site}.")
        hosting("POST", f"projects/{args.project}/sites?siteId={site}", token, {}, project=args.project)
    # Hosting keeps old versions with their files. Two are enough (the current one and one to roll back to).
    hosting("PATCH", f"sites/{site}/config?updateMask=maxVersions", token, {"maxVersions": 2}, project=args.project)

    # The files of the live version: path -> hash. They stay in the new version without uploading them again.
    existing = {}
    releases = hosting("GET", f"sites/{site}/releases?pageSize=1", token, project=args.project).get("releases", [])
    if releases and releases[0].get("version", {}).get("name"):
        page = ""
        while True:
            listing = hosting(
                "GET", f"{releases[0]['version']['name']}/files?pageSize=1000&pageToken={page}", token, project=args.project
            )
            for item in listing.get("files", []):
                existing[item["path"].lstrip("/")] = item["hash"]
            page = listing.get("nextPageToken")
            if not page:
                break

    files = {}
    uploads = {}
    for path in sorted(wanted_paths(manifest)):
        local = out / path
        if local.exists():
            content = gzip.compress(local.read_bytes(), compresslevel=9, mtime=0)
            digest = hashlib.sha256(content).hexdigest()
            uploads[digest] = content
            files["/" + path] = digest
        elif path in existing:
            files["/" + path] = existing[path]
        else:
            sys.exit(f"{path} is neither in the build nor on the site.")

    immutable = [{"key": "Cache-Control", "value": "public, max-age=31536000, immutable"}]
    config = {
        "headers": [
            {"glob": "**", "headers": {"X-Robots-Tag": "noindex, nofollow"}},
            {"glob": "/img/**", "headers": {h["key"]: h["value"] for h in immutable}},
            {"glob": "/thumb/**", "headers": {h["key"]: h["value"] for h in immutable}},
            {"glob": "/videos/**", "headers": {h["key"]: h["value"] for h in immutable}},
            {"glob": "/manifest.json", "headers": {"Cache-Control": "no-cache"}},
            {"glob": "/index.html", "headers": {"Cache-Control": "no-cache"}},
        ]
    }
    version = hosting("POST", f"sites/{site}/versions", token, {"config": config}, project=args.project)["name"]
    paths = list(files.items())
    required = set()
    upload_url = None
    for start in range(0, len(paths), 1000):
        result = hosting(
            "POST", f"{version}:populateFiles", token, {"files": dict(paths[start : start + 1000])}, project=args.project
        )
        required.update(result.get("uploadRequiredHashes", []))
        upload_url = result.get("uploadUrl", upload_url)
    log(f"{len(files)} files, {len(required)} to upload ({sum(len(uploads[h]) for h in required) / 1e6:.1f} MB).")

    def upload(digest):
        request = urllib.request.Request(
            f"{upload_url}/{digest}", data=uploads[digest], method="POST",
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/octet-stream"},
        )
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=600):
                    return
            except (urllib.error.URLError, OSError):
                if attempt == 3:
                    raise
                time.sleep(2 ** attempt)

    with ThreadPoolExecutor(8) as pool:
        list(pool.map(upload, sorted(required)))
    hosting("PATCH", f"{version}?updateMask=status", token, {"status": "FINALIZED"}, project=args.project)
    hosting("POST", f"sites/{site}/releases?versionName={version}", token, {"message": manifest["head"][:12]}, project=args.project)
    log(f"Published https://{site}.web.app")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    build = sub.add_parser("build")
    build.add_argument("--out", default=str(ROOT / "build" / "screenshot-gallery"))
    build.add_argument("--previous", help="URL of the published site, or a manifest.json")
    build.add_argument("--versions", type=int, default=DEFAULT_VERSIONS)
    build.add_argument("--head", default="HEAD")
    videos = sub.add_parser("videos")
    videos.add_argument("--out", default=str(ROOT / "build" / "screenshot-gallery"))
    publish = sub.add_parser("publish")
    publish.add_argument("--out", default=str(ROOT / "build" / "screenshot-gallery"))
    publish.add_argument("--site", required=True)
    publish.add_argument("--project", required=True)
    args = parser.parse_args()
    {"build": command_build, "videos": command_videos, "publish": command_publish}[args.command](args)


if __name__ == "__main__":
    main()
