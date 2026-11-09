#!/usr/bin/env python3
"""Publishes the Android apps on Google Play. See docs/android-release.md.

    python3 scripts/play.py pending
    python3 scripts/play.py upload <admin|player> <path/to/app-release.aab>

`pending` asks Google Play which version codes each app already has, and lists the apps whose version code in
gradle/libs.versions.toml is not there yet. In GitHub Actions it also writes `admin=true|false` and
`player=true|false` to $GITHUB_OUTPUT, so the workflow only builds and uploads the apps that have a new version.

`upload` uploads the bundle and releases it on the track $PLAY_TRACK (default `internal`) with the status
$PLAY_RELEASE_STATUS (default `completed`, which rolls it out to the testers at once).

Both sign in with Application Default Credentials, which google-github-actions/auth sets up in the workflow.
The service account needs access to both apps in Play Console.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

from google.auth import default
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

sys.path.insert(0, str(Path(__file__).resolve().parent))
from versions import CATALOG, ROOT, read_versions  # noqa: E402

PACKAGES = {
    "admin": "com.etologic.mahjongtournamentsuite",
    "player": "com.etologic.mahjongtournamentsuite.player",
}
LABELS = {"admin": "Admin app", "player": "Player app"}
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def local_versions() -> dict[str, dict[str, str]]:
    return read_versions((ROOT / CATALOG).read_text())


def publisher():
    credentials, _ = default(scopes=[SCOPE])
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def uploaded_codes(service, package: str) -> set[int]:
    """Version codes of every bundle and APK that the app has on Google Play, in any track or none."""
    edit = service.edits().insert(packageName=package, body={}).execute()
    try:
        bundles = service.edits().bundles().list(packageName=package, editId=edit["id"]).execute()
        apks = service.edits().apks().list(packageName=package, editId=edit["id"]).execute()
    finally:
        service.edits().delete(packageName=package, editId=edit["id"]).execute()
    codes = {int(item["versionCode"]) for item in bundles.get("bundles", [])}
    codes |= {int(item["versionCode"]) for item in apks.get("apks", [])}
    return codes


def pending() -> int:
    service = publisher()
    versions = local_versions()
    outputs = []
    for app, package in PACKAGES.items():
        code = int(versions[app]["code"])
        codes = uploaded_codes(service, package)
        highest = max(codes, default=0)
        if code in codes:
            print(f"{LABELS[app]}: version code {code} is already on Google Play. Nothing to upload.")
            new = False
        elif code < highest:
            print(
                f"::error::{LABELS[app]}: version code {code} is lower than {highest}, the highest one on Google Play. "
                f"Raise it with: python3 scripts/versions.py bump {app}"
            )
            return 1
        else:
            print(f"{LABELS[app]}: version code {code} ({versions[app]['name']}) is new. It will be uploaded.")
            new = True
        outputs.append(f"{app}={'true' if new else 'false'}")
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as file:
            file.write("\n".join(outputs) + "\n")
    return 0


def upload(app: str, bundle: Path) -> int:
    package = PACKAGES[app]
    version = local_versions()[app]
    track = os.environ.get("PLAY_TRACK") or "internal"
    status = os.environ.get("PLAY_RELEASE_STATUS") or "completed"
    service = publisher()
    edits = service.edits()
    edit_id = edits.insert(packageName=package, body={}).execute()["id"]
    try:
        media = MediaFileUpload(str(bundle), mimetype="application/octet-stream", resumable=True)
        uploaded = edits.bundles().upload(packageName=package, editId=edit_id, media_body=media).execute()
        code = int(uploaded["versionCode"])
        if code != int(version["code"]):
            raise SystemExit(f"The bundle has version code {code}, but {CATALOG} says {version['code']}.")
        edits.tracks().update(
            packageName=package,
            editId=edit_id,
            track=track,
            body={
                "track": track,
                "releases": [{"name": f"{version['name']} ({code})", "versionCodes": [str(code)], "status": status}],
            },
        ).execute()
        edits.commit(packageName=package, editId=edit_id).execute()
    except BaseException:
        try:
            edits.delete(packageName=package, editId=edit_id).execute()
        except HttpError:
            pass
        raise
    print(f"{LABELS[app]} {version['name']} ({code}) is on the track '{track}' with the status '{status}'.")
    return 0


def main(args: list[str]) -> int:
    if args == ["pending"]:
        return pending()
    if len(args) == 3 and args[0] == "upload" and args[1] in PACKAGES:
        return upload(args[1], Path(args[2]))
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
