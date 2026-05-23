#!/usr/bin/env python3
"""Generate JOSM binary .lang files for this plugin."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TRANSLATIONS = ROOT / "tools" / "i18n" / "translations.json"
OUTPUT_DIR = ROOT / "src" / "main" / "resources" / "data"


def encode_text(value: str) -> bytes:
    data = value.encode("utf-8")
    if len(data) > 0xFFFE:
        raise ValueError(f"translation is too long: {value[:80]}")
    return len(data).to_bytes(2, "big") + data


def write_lang(path: Path, values: list[str]) -> None:
    content = bytearray()
    for value in values:
        content.extend(encode_text(value))
    content.extend((0xFF, 0xFF))
    path.write_bytes(content)


def main() -> None:
    data = json.loads(TRANSLATIONS.read_text(encoding="utf-8"))
    languages = data["languages"]
    entries = data["entries"]

    sources = [entry["source"] for entry in entries]
    if len(sources) != len(set(sources)):
        duplicates = sorted({source for source in sources if sources.count(source) > 1})
        raise ValueError(f"duplicate source strings: {duplicates}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    write_lang(OUTPUT_DIR / "en.lang", sources)

    for language in languages:
        translations: list[str] = []
        for entry in entries:
            if language not in entry:
                raise ValueError(f"missing {language} translation for {entry['source']}")
            translations.append(entry[language])
        write_lang(OUTPUT_DIR / f"{language}.lang", translations)


if __name__ == "__main__":
    main()
