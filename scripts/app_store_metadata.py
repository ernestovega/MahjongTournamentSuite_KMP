#!/usr/bin/env python3
"""Builds the fastlane `deliver` folders of the App Store listing (docs/app-store/listing.md).

    python3 scripts/app_store_metadata.py <out>

writes `<out>/metadata` (texts and URLs of each language, categories, copyright, review notes) and `<out>/screenshots`
(the images of docs/app-store/<iphone|ipad>/<language>/, which deliver sorts by size). The description of each
language is the full description of the Google Play listing. It checks the limits of App Store Connect and stops
on any problem. The workflow Publish · App Store listing runs it.
"""
import re
import shutil
import struct
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLAY = ROOT / "docs" / "play-store" / "listing.md"
APP_STORE = ROOT / "docs" / "app-store"
NAME = "MTS - Mahjong Tournament Suite"
SITE = "https://mts.mahjongmadrid.com"

# The folder of each language, its heading in the Play listing and its App Store Connect code.
LANGUAGES = {
    "en": ("en-US", "en-US"),
    "es": ("es-ES", "es-ES"),
    "fr": ("fr-FR", "fr-FR"),
    "it": ("it-IT", "it"),
    "de": ("de-DE", "de-DE"),
    "sv": ("sv-SE", "sv"),
    "nl": ("nl-NL", "nl-NL"),
    "ru": ("ru-RU", "ru"),
}
# The size of the screenshots of each device folder.
DEVICES = {"iphone": (1320, 2868), "ipad": (2064, 2752)}
REVIEW_NOTES = (
    "MTS follows Mahjong tournaments. Without an account the app shows every tournament, its tables, hands, "
    "rankings and statistics. The demo account is an administrator: after signing in (Profile button, top right), "
    "the ADMIN button of the top bar opens the management part, where organizers create tournaments, registrations, "
    "tables and rounds. The tournaments of the list are real tournaments of the European Mahjong Association."
)


def play_descriptions() -> dict[str, str]:
    """The full description of each Play heading code."""
    result = {}
    sections = re.split(r"^## .*\(([a-z]{2}-[A-Z]{2})\)\s*$", PLAY.read_text(), flags=re.M)
    for code, body in zip(sections[1::2], sections[2::2]):
        blocks = re.findall(r"^```\n(.*?)\n```", body, flags=re.M | re.S)
        result[code] = blocks[1].strip()
    return result


def app_store_texts() -> dict[str, dict[str, str]]:
    """The subtitle, keywords and promotional text of each App Store code."""
    result = {}
    sections = re.split(r"^## (\S+)\s*$", (APP_STORE / "listing.md").read_text(), flags=re.M)
    for code, body in zip(sections[1::2], sections[2::2]):
        values = dict(re.findall(r"^- ([A-Za-z ]+): `(.*)`\s*$", body, flags=re.M))
        result[code] = {
            "subtitle": values["Subtitle"],
            "keywords": values["Keywords"],
            "promotional_text": values["Promotional text"],
        }
    return result


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path.relative_to(ROOT)} is not a PNG (a Git LFS pointer? run git lfs pull)")
    return struct.unpack(">II", data[16:24])


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    out = Path(sys.argv[1])
    shutil.rmtree(out, ignore_errors=True)
    metadata, screenshots = out / "metadata", out / "screenshots"
    descriptions, texts = play_descriptions(), app_store_texts()
    problems = []

    def write(path: Path, text: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text + "\n")

    for folder, (play_code, code) in LANGUAGES.items():
        description = descriptions.get(play_code)
        values = texts.get(code)
        if description is None or values is None:
            problems.append(f"{code}: missing in the Play or the App Store listing")
            continue
        for key, limit in (("subtitle", 30), ("promotional_text", 170)):
            if len(values[key]) > limit:
                problems.append(f"{code}: {key} has {len(values[key])} characters (max {limit})")
        if len(values["keywords"].encode()) > 100:
            problems.append(f"{code}: keywords have {len(values['keywords'].encode())} bytes (max 100)")
        if len(description) > 4000:
            problems.append(f"{code}: description has {len(description)} characters (max 4000)")
        if "android" in description.lower() or "google play" in description.lower():
            problems.append(f"{code}: the description names another platform")
        language = metadata / code
        write(language / "name.txt", NAME)
        write(language / "description.txt", description)
        for key, value in values.items():
            write(language / f"{key}.txt", value)
        write(language / "support_url.txt", f"{SITE}/support")
        write(language / "marketing_url.txt", f"{SITE}/")
        write(language / "privacy_url.txt", f"{SITE}/privacy/")
        for device, size in DEVICES.items():
            images = sorted((APP_STORE / device / folder).glob("*.png"), key=lambda p: (int(p.name.split("-")[0]), p.name))
            if not images:
                problems.append(f"{device}/{folder}: no screenshots")
            for image in images:
                if png_size(image) != size:
                    problems.append(f"{image.relative_to(ROOT)}: {png_size(image)}, not {size}")
                target = screenshots / code / f"{device}-{image.name}"
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(image, target)

    write(metadata / "copyright.txt", f"{date.today().year} Ernesto Vega")
    write(metadata / "primary_category.txt", "SPORTS")
    write(metadata / "secondary_category.txt", "GAMES")
    write(metadata / "secondary_first_sub_category.txt", "GAMES_BOARD")
    write(metadata / "review_information" / "notes.txt", REVIEW_NOTES)
    if problems:
        raise SystemExit("App Store listing problems:\n" + "\n".join(problems))
    print(f"App Store listing: {len(LANGUAGES)} languages in {out}")


if __name__ == "__main__":
    main()
