#!/usr/bin/env python3
"""Builds the card-number indexes the app uses for games without a dedicated API.

For each game it downloads every set (TCGplayer "group") from TCGCSV (https://tcgcsv.com,
a daily mirror of TCGplayer's catalogue and prices) and writes a compact JSON file:

    {
      "game": "UNION_ARENA",
      "updated": "2026-10-02T20:00:00Z",
      "groups": {"<groupId>": ["UE01BT: BLEACH ...", "UE01BT", <cards in set>]},
      "cards": [["UE01BT/BLC-1-001", "Asguiaro Ebern", <groupId>, "R", <productId>, {"Normal": 0.25}], ...]
    }

The app looks up the code printed on a card (e.g. "UE01BT/BLC-1-001", "FB01-139",
"HOL/W91-001") in this index. Runs daily in GitHub Actions (.github/workflows/card-index.yml).
"""
import json
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

GAMES = {
    "WEISS_SCHWARZ": 20,
    "DRAGON_BALL_SUPER": 27,
    "DRAGON_BALL_FW": 80,
    "UNION_ARENA": 81,
    "NARUTO": 93,
}


def get(url, tries=4):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "TCG-Catalogue index builder"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)["results"]
        except Exception as e:  # noqa: BLE001 - retry any network error
            if attempt == tries - 1:
                raise
            print(f"  retry {url}: {e}", file=sys.stderr)
            time.sleep(2 ** attempt)


def number_of(product):
    for e in product.get("extendedData", []):
        if e["name"] == "Number" and e["value"]:
            # Weiss Schwarz numbers carry the rarity after a space: "HOL/WE44-E01 N".
            return e["value"].split(" ")[0].strip().upper()
    return None


def rarity_of(product):
    for e in product.get("extendedData", []):
        if e["name"] == "Rarity":
            return e["value"]
    return None


def build(game, category):
    groups = get(f"https://tcgcsv.com/tcgplayer/{category}/groups")
    out_groups, cards = {}, []
    for g in groups:
        gid = g["groupId"]
        products = get(f"https://tcgcsv.com/tcgplayer/{category}/{gid}/products")
        prices = get(f"https://tcgcsv.com/tcgplayer/{category}/{gid}/prices")
        by_product = {}
        for p in prices:
            value = p.get("marketPrice") or p.get("midPrice") or p.get("lowPrice")
            if value:
                by_product.setdefault(p["productId"], {})[p["subTypeName"]] = round(value, 2)
        numbers = set()
        for p in products:
            number = number_of(p)
            if not number:
                continue  # sealed product, accessories, ...
            numbers.add(number)
            cards.append([number, p["name"], gid, rarity_of(p), p["productId"], by_product.get(p["productId"], {})])
        if numbers:
            out_groups[str(gid)] = [g["name"], g.get("abbreviation") or "", len(numbers)]
        time.sleep(0.05)
    return {
        "game": game,
        "updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "groups": out_groups,
        "cards": cards,
    }


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "index")
    out.mkdir(parents=True, exist_ok=True)
    for game, category in GAMES.items():
        print(f"{game} (category {category})")
        data = build(game, category)
        path = out / f"{game}.json"
        path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
        print(f"  {len(data['groups'])} sets, {len(data['cards'])} cards, {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
