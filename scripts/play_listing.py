#!/usr/bin/env python3
"""Uploads the Google Play store listing of the app: texts, screenshots and feature graphic, in every language.

    python3 scripts/play_listing.py check            # reads everything and asks Play, changes nothing
    python3 scripts/play_listing.py check --local    # only the local files, without Play
    python3 scripts/play_listing.py upload           # replaces the listing on Play

The texts come from docs/play-store/listing.md (the first block of a language is the short description, the second
the full one). The images come from docs/play-store/: `phone/<lang>/*.png` as phone screenshots, `tablet/<lang>/*.png`
as 7-inch and 10-inch tablet screenshots, `feature-graphic/<lang>.png` as the feature graphic. Images go in the order
of their file names. The title of a language that is already on Play stays as it is; a new language gets APP_NAME.

`upload` works in one Play edit: it replaces the texts and every screenshot and feature graphic of each language, and
commits the edit at the end, so Play shows all of it or nothing. The Chromebook screenshots have no place in the API:
upload those by hand in Play Console.

It signs in with Application Default Credentials (google-github-actions/auth in the workflow). The service account
needs the Play Console permission "Manage store presence" on the app. See docs/play-store/README.md.
"""

from __future__ import annotations

import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "docs" / "play-store"
PACKAGE = "com.etologic.mahjongtournamentsuite"
APP_NAME = "MTS - Mahjong Tournament Suite"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

# The folder of each language and its Play code, as in the headings of listing.md.
LANGUAGES = {
    "en": "en-US",
    "es": "es-ES",
    "fr": "fr-FR",
    "it": "it-IT",
    "de": "de-DE",
    "sv": "sv-SE",
    "nl": "nl-NL",
    "ru": "ru-RU",
}
# The Play image types and the folder their images come from.
SCREENSHOTS = {
    "phoneScreenshots": "phone",
    "sevenInchScreenshots": "tablet",
    "tenInchScreenshots": "tablet",
}
SHORT_MAX = 80
FULL_MAX = 4000
SCREENSHOTS_MAX = 8


def texts(markdown: str) -> dict[str, tuple[str, str]]:
    """The short and full description of each Play language code, from listing.md."""
    result = {}
    sections = re.split(r"^## .*\(([a-z]{2}-[A-Z]{2})\)\s*$", markdown, flags=re.M)
    for code, body in zip(sections[1::2], sections[2::2]):
        blocks = re.findall(r"^```\n(.*?)\n```", body, flags=re.M | re.S)
        if len(blocks) < 2:
            raise SystemExit(f"listing.md: {code} needs a block for the short and one for the full description")
        result[code] = (blocks[0].strip(), blocks[1].strip())
    return result


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path.relative_to(ROOT)} is not a PNG (a Git LFS pointer? run git lfs pull)")
    return struct.unpack(">II", data[16:24])


def images(folder: str, language: str) -> list[Path]:
    return sorted((STORE / folder / language).glob("*.png"), key=lambda p: (int(p.name.split("-")[0]), p.name))


def check_local() -> dict[str, tuple[str, str]]:
    """Reads and checks every text and image. Stops with the problems found, if any."""
    listing = texts((STORE / "listing.md").read_text(encoding="utf-8"))
    problems = []
    for language, code in LANGUAGES.items():
        if code not in listing:
            problems.append(f"listing.md has no section for {code}")
            continue
        short, full = listing[code]
        if len(short) > SHORT_MAX:
            problems.append(f"{code}: short description has {len(short)} characters (max {SHORT_MAX})")
        if len(full) > FULL_MAX:
            problems.append(f"{code}: full description has {len(full)} characters (max {FULL_MAX})")
        for folder, size in (("phone", (1080, 1920)), ("tablet", (1920, 1080))):
            shots = images(folder, language)
            if not 2 <= len(shots) <= SCREENSHOTS_MAX:
                problems.append(f"{folder}/{language}: {len(shots)} screenshots (Play takes 2 to {SCREENSHOTS_MAX})")
            problems += [f"{p.relative_to(STORE)}: {png_size(p)} instead of {size}" for p in shots if png_size(p) != size]
        graphic = STORE / "feature-graphic" / f"{language}.png"
        if not graphic.exists():
            problems.append(f"missing {graphic.relative_to(STORE)}")
        elif png_size(graphic) != (1024, 500):
            problems.append(f"{graphic.relative_to(STORE)}: {png_size(graphic)} instead of (1024, 500)")
        print(f"{code}: short {len(short)}, full {len(full)}, phone {len(images('phone', language))}, tablet {len(images('tablet', language))}")
    if problems:
        raise SystemExit("Problems:\n- " + "\n- ".join(problems))
    return listing


def publisher():
    from google.auth import default
    from googleapiclient.discovery import build

    credentials, _ = default(scopes=[SCOPE])
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def check(local_only: bool) -> int:
    check_local()
    if local_only:
        return 0
    service = publisher()
    edits = service.edits()
    edit = edits.insert(packageName=PACKAGE, body={}).execute()
    try:
        listed = edits.listings().list(packageName=PACKAGE, editId=edit["id"]).execute().get("listings", [])
        on_play = {item["language"]: item for item in listed}
        details = edits.details().get(packageName=PACKAGE, editId=edit["id"]).execute()
        print(f"\nOn Play (default language {details.get('defaultLanguage')}):")
        for code in LANGUAGES.values():
            item = on_play.get(code)
            if item is None:
                print(f"  {code}: no listing yet, it will be created with the title {APP_NAME!r}")
                continue
            counts = []
            for image_type in ("phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots", "featureGraphic"):
                found = edits.images().list(packageName=PACKAGE, editId=edit["id"], language=code, imageType=image_type).execute()
                counts.append(f"{image_type} {len(found.get('images', []))}")
            print(f"  {code}: title {item.get('title')!r}, " + ", ".join(counts))
        others = sorted(set(on_play) - set(LANGUAGES.values()))
        if others:
            print(f"  Other languages on Play, left as they are: {', '.join(others)}")
    finally:
        edits.delete(packageName=PACKAGE, editId=edit["id"]).execute()
    print("\nNothing was changed.")
    return 0


def upload() -> int:
    from googleapiclient.http import MediaFileUpload

    listing = check_local()
    service = publisher()
    edits = service.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    try:
        listed = edits.listings().list(packageName=PACKAGE, editId=edit_id).execute().get("listings", [])
        titles = {item["language"]: item.get("title") for item in listed}
        for language, code in LANGUAGES.items():
            short, full = listing[code]
            body = {"language": code, "title": titles.get(code) or APP_NAME, "shortDescription": short, "fullDescription": full}
            edits.listings().update(packageName=PACKAGE, editId=edit_id, language=code, body=body).execute()
            uploads = {image_type: images(folder, language) for image_type, folder in SCREENSHOTS.items()}
            uploads["featureGraphic"] = [STORE / "feature-graphic" / f"{language}.png"]
            for image_type, files in uploads.items():
                edits.images().deleteall(packageName=PACKAGE, editId=edit_id, language=code, imageType=image_type).execute()
                for file in files:
                    edits.images().upload(
                        packageName=PACKAGE, editId=edit_id, language=code, imageType=image_type,
                        media_body=MediaFileUpload(str(file), mimetype="image/png"),
                    ).execute()
            print(f"{code}: texts, {len(uploads['phoneScreenshots'])} phone and {len(uploads['tenInchScreenshots'])} tablet screenshots, feature graphic")
        edits.validate(packageName=PACKAGE, editId=edit_id).execute()
        edits.commit(packageName=PACKAGE, editId=edit_id).execute()
    except BaseException:
        # Play throws an edit away by itself when another one commits on the app meanwhile (an app upload, say).
        try:
            edits.delete(packageName=PACKAGE, editId=edit_id).execute()
        except Exception:
            pass
        print("Nothing was changed on Play: the edit was thrown away. 'This Edit has been deleted' means that another")
        print("edit of the app (an upload of the app, Play Console) ran at the same time: run this again on its own.")
        raise
    print("\nDone: the listing is on Play (it may wait for Google's review before it shows).")
    return 0


def main(argv: list[str]) -> int:
    if argv[:1] == ["check"]:
        return check(local_only="--local" in argv)
    if argv[:1] == ["upload"]:
        return upload()
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
