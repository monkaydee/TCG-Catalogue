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
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

GAMES = {
    # One Piece is recognised via optcgapi.com; its index only supplies TCGplayer product ids.
    "ONE_PIECE": 68,
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


# Sealed products only (the cards come from TCGdex and Scryfall for these games).
SEALED_ONLY = {
    "POKEMON": 3,
    "MAGIC": 1,
}

SEALED_WORDS = re.compile(
    r"booster|box|pack|deck|bundle|collection|tin|display|case|elite trainer|\betb\b|blister|starter|kit|set\b|chest|gift",
    re.IGNORECASE,
)
NOT_SEALED = re.compile(
    r"code card|\bsleeves\b|card sleeve|playmat|play mat|binder|deck box|portfolio|toploader|top loader|dice|token|coin|card protector|storage box|"
    r"oversized|jumbo|poster|pin\b|figure|plush",
    re.IGNORECASE,
)


def sealed_item(p, gid, by_product):
    """A sealed product row [productId, name, groupId, market price], or None for accessories."""
    name = p["name"]
    if not SEALED_WORDS.search(name) or NOT_SEALED.search(name):
        return None
    prices = by_product.get(p["productId"], {})
    price = prices.get("Normal") or next(iter(prices.values()), None)
    return [p["productId"], name, gid, price]


def build(game, category, cards_too=True):
    groups = get(f"https://tcgcsv.com/tcgplayer/{category}/groups")
    out_groups, cards, sealed, sealed_groups = {}, [], [], {}
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
                item = sealed_item(p, gid, by_product)  # sealed product, or an accessory (skipped)
                if item:
                    sealed.append(item)
                    sealed_groups[str(gid)] = g["name"]
                continue
            if not cards_too:
                continue
            numbers.add(number)
            cards.append([number, p["name"], gid, rarity_of(p), p["productId"], by_product.get(p["productId"], {})])
        if numbers:
            out_groups[str(gid)] = [g["name"], g.get("abbreviation") or "", len(numbers)]
        time.sleep(0.05)
    updated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    index = {"game": game, "updated": updated, "groups": out_groups, "cards": cards}
    sealed_index = {"game": game, "updated": updated, "groups": sealed_groups, "items": sealed}
    return index, sealed_index


def write_sealed(out, game, data):
    path = out / f"SEALED_{game}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"  {len(data['items'])} sealed products, {path.stat().st_size // 1024} KB")


CARDMARKET = "https://downloads.s3.cardmarket.com/productCatalog"
CODE = re.compile(r"\(([A-Z]{1,4}\d{0,2}-\d{3})\)")
EXPANSION_SUFFIXES = re.compile(
    r"\s+(Booster Box Case.*|Booster Box.*|Sleeved Booster.*|Booster.*|Dash Pack.*|Deck Pack.*|Premium Storage.*|Display.*)$"
)


def get_cardmarket(path):
    req = urllib.request.Request(f"{CARDMARKET}/{path}", headers={"User-Agent": "TCG-Catalogue index builder"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def build_cardmarket_one_piece():
    """Cardmarket's One Piece listings by card code, with their EUR prices.

    Cardmarket sells every print of a card (original, reprints, alt arts, promos ...) as its own
    product, named "Brannew (OP03-089)", and tells them apart as V.1, V.2, ... within a set.
    Set names come from the sealed products of each expansion ("The Best Booster Box" -> "The Best").
    """
    singles = get_cardmarket("productList/products_singles_18.json")["products"]
    sealed = get_cardmarket("productList/products_nonsingles_18.json")["products"]
    guide = {g["idProduct"]: g for g in get_cardmarket("priceGuide/price_guide_18.json")["priceGuides"]}
    names, non_english = {}, set()
    for p in sorted(sealed, key=lambda p: len(p["name"])):
        names.setdefault(p["idExpansion"], EXPANSION_SUFFIXES.sub("", p["name"]).strip())
        if "Non-English" in p["name"] or "Asia" in p["name"]:
            non_english.add(p["idExpansion"])
    for e in non_english:
        if "Non-English" not in names[e]:
            names[e] += " (Non-English)"
    by_print = {}
    for p in singles:
        m = CODE.search(p["name"])
        if m:
            by_print.setdefault((p["idExpansion"], p["name"]), []).append(p)
    cards = []
    for (expansion, name), prints in by_print.items():
        prints.sort(key=lambda p: p["idProduct"])
        for i, p in enumerate(prints):
            g = guide.get(p["idProduct"], {})
            cards.append([
                CODE.search(name).group(1),
                name.split(" (")[0],
                p["idProduct"],
                expansion,
                i + 1 if len(prints) > 1 else 0,
                g.get("trend"), g.get("low"), g.get("avg7"), g.get("avg30"),
            ])
    return {
        "updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "expansions": {str(k): v for k, v in names.items()},
        "cards": cards,
    }


def build_cardmarket_pokemon():
    """Cardmarket prices of Pokémon cards that have look-alike siblings.

    Cardmarket groups every print of a card (same name and attacks) in a set under one
    "metacard": the regular Zekrom 51/113 and the gold Zekrom 115/113 are two products of the
    same metacard. TCGdex sometimes links a card to the wrong one, so the app re-picks the
    product by collector-number order. Only products with siblings are needed for that.
    Rows: [idProduct, idExpansion, idMetacard, trend, low, avg, avg1, avg7, avg30,
           trend-holo, low-holo, avg-holo, avg1-holo, avg7-holo, avg30-holo]
    """
    singles = get_cardmarket("productList/products_singles_6.json")["products"]
    guide = {g["idProduct"]: g for g in get_cardmarket("priceGuide/price_guide_6.json")["priceGuides"]}
    groups = {}
    for p in singles:
        groups.setdefault((p["idExpansion"], p["idMetacard"]), []).append(p["idProduct"])
    fields = ["trend", "low", "avg", "avg1", "avg7", "avg30"]
    rows = []
    for (expansion, metacard), products in groups.items():
        if len(products) < 2 or not metacard:
            continue
        for pid in products:
            g = guide.get(pid, {})
            rows.append([pid, expansion, metacard] + [g.get(f) for f in fields] + [g.get(f + "-holo") for f in fields])
    return {"updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "cards": rows}


# Pokémon cards come from TCGdex, which adds new sets and promos with a delay. This index holds
# only the promo sets and the sets of the last half year, coded "MEP-091" / "PFL-012", so brand-new
# cards can be found until TCGdex has them.
POKEMON_PROMO_GROUPS = {22872, 24451}  # SV: Scarlet & Violet Promo Cards, ME: Mega Evolution Promo
POKEMON_RECENT_DAYS = 180


def build_pokemon_new():
    from datetime import timedelta
    groups = get("https://tcgcsv.com/tcgplayer/3/groups")
    since = datetime.now(timezone.utc) - timedelta(days=POKEMON_RECENT_DAYS)
    out_groups, cards = {}, []
    for g in groups:
        published = (g.get("publishedOn") or "")[:10]
        recent = published and datetime.strptime(published, "%Y-%m-%d").replace(tzinfo=timezone.utc) >= since
        if g["groupId"] not in POKEMON_PROMO_GROUPS and not recent:
            continue
        abbr = (g.get("abbreviation") or "").strip().upper()
        if not abbr:
            continue
        products = get(f"https://tcgcsv.com/tcgplayer/3/{g['groupId']}/products")
        prices = get(f"https://tcgcsv.com/tcgplayer/3/{g['groupId']}/prices")
        by_product = {}
        for p in prices:
            value = p.get("marketPrice") or p.get("midPrice") or p.get("lowPrice")
            if value:
                by_product.setdefault(p["productId"], {})[p["subTypeName"]] = round(value, 2)
        count = 0
        for p in products:
            number = number_of(p)
            if not number:
                continue
            digits = re.sub(r"\D", "", number.split("/")[0])
            if not digits:
                continue
            name = re.sub(r"\s+-\s+[A-Z]*\d+[a-z]?(?=\s|$)", "", p["name"]).strip()  # "Mega Dragonite ex - 091" → "Mega Dragonite ex"
            cards.append([f"{abbr}-{int(digits):03d}", name, g["groupId"], rarity_of(p), p["productId"], by_product.get(p["productId"], {})])
            count += 1
        if count:
            out_groups[str(g["groupId"])] = [g["name"], abbr, count]
        time.sleep(0.05)
    return {"game": "POKEMON", "updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "groups": out_groups, "cards": cards}


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "index")
    out.mkdir(parents=True, exist_ok=True)
    for game, category in GAMES.items():
        print(f"{game} (category {category})")
        data, sealed = build(game, category)
        path = out / f"{game}.json"
        path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
        print(f"  {len(data['groups'])} sets, {len(data['cards'])} cards, {path.stat().st_size // 1024} KB")
        write_sealed(out, game, sealed)
    for game, category in SEALED_ONLY.items():
        print(f"{game} sealed (category {category})")
        _, sealed = build(game, category, cards_too=False)
        write_sealed(out, game, sealed)
    print("POKEMON (new sets and promos)")
    data = build_pokemon_new()
    path = out / "POKEMON.json"
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"  {len(data['groups'])} sets, {len(data['cards'])} cards, {path.stat().st_size // 1024} KB")
    print("CARDMARKET_POKEMON")
    data = build_cardmarket_pokemon()
    path = out / "CARDMARKET_POKEMON.json"
    path.write_text(json.dumps(data, separators=(",", ":")))
    print(f"  {len(data['cards'])} listings, {path.stat().st_size // 1024} KB")
    print("CARDMARKET_ONE_PIECE")
    data = build_cardmarket_one_piece()
    path = out / "CARDMARKET_ONE_PIECE.json"
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"  {len(data['cards'])} listings, {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
