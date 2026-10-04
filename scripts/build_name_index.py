"""Builds NAMES_POKEMON.json: every Pokémon card's name in English, German, French, Italian,
Spanish and Portuguese (TCGdex), so the app can find a card by the name printed on it in any of
these languages without asking TCGdex each time (works offline, much faster).

Format: {"updated": ..., "languages": ["de", "fr", ...],
         "cards": [[id, localId, englishName, nameDe|null, nameFr|null, ...], ...]}
A local name equal to the English one is stored as null to keep the file small.
"""
import json
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

LANGUAGES = ["de", "fr", "it", "es", "pt"]


def cards(lang):
    for attempt in range(4):
        try:
            req = urllib.request.Request(f"https://api.tcgdex.net/v2/{lang}/cards", headers={"User-Agent": "CardNavo name index"})
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001
            print(f"  {lang}: {e}, retrying")
            time.sleep(5 * (attempt + 1))
    raise SystemExit(f"TCGdex {lang} not reachable")


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "index")
    out.mkdir(parents=True, exist_ok=True)
    english = {c["id"]: c for c in cards("en")}
    local = {lang: {c["id"]: c.get("name") for c in cards(lang)} for lang in LANGUAGES}
    ids = set(english) | {i for names in local.values() for i in names}
    rows = []
    for i in sorted(ids):
        en = english.get(i, {})
        en_name = en.get("name")
        local_id = en.get("localId") or i.rsplit("-", 1)[-1]
        names = [local[lang].get(i) for lang in LANGUAGES]
        names = [n if n and n != en_name else None for n in names]
        if not en_name and not any(names):
            continue
        rows.append([i, local_id, en_name] + names)
    data = {"updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "languages": LANGUAGES, "cards": rows}
    path = out / "NAMES_POKEMON.json"
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"NAMES_POKEMON: {len(rows)} cards, {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
