#!/usr/bin/env python3
"""Checks that every language folder has the same keys and placeholders as the English base.

Run it from the repository root: python3 scripts/check-localization.py
It exits with 1 when a text is missing, is extra, or uses other placeholders than the English text.
"""
import re
import sys
from pathlib import Path

MODULES = ["ui", "playerApp", "adminApp"]
ERRORS = []


def read(path: Path) -> tuple[set[str], dict[str, list[str]]]:
    text = path.read_text(encoding="utf-8")
    keys = set(re.findall(r'<(?:string|plurals) name="([^"]+)"', text))
    placeholders = {
        name: sorted(re.findall(r"%\d\$[sd]", body))
        for name, body in re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S)
    }
    return keys, placeholders


for module in MODULES:
    base = Path(module) / "src/commonMain/composeResources"
    english_keys, english_placeholders = read(base / "values/strings.xml")
    for folder in sorted(base.glob("values-*")):
        file = folder / "strings.xml"
        if not file.exists():
            continue
        keys, placeholders = read(file)
        for key in sorted(english_keys - keys):
            ERRORS.append(f"{file}: missing {key}")
        for key in sorted(keys - english_keys):
            ERRORS.append(f"{file}: extra {key}")
        for key, found in placeholders.items():
            if key in english_placeholders and english_placeholders[key] != found:
                ERRORS.append(f"{file}: placeholders of {key} differ from English")

print("\n".join(ERRORS) if ERRORS else "All language folders match the English texts.")
sys.exit(1 if ERRORS else 0)
