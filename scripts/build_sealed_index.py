#!/usr/bin/env python3
"""Cardmarket sealed catalogue, with explicit aggregate reference prices (EUR).

The public catalogue does not establish a listing's printed language. Rows are product
templates; the app requires confirmation and gets exact-language asking prices separately.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path
from datetime import datetime, timezone

BASE = "https://downloads.s3.cardmarket.com/productCatalog"
JP_SETS = {
    "SV8a": ("Terastal Festival ex", "テラスタルフェスex"),
    "SV4a": ("Shiny Treasure ex", "シャイニートレジャーex"),
    "SV2a": ("Pokemon Card 151", "ポケモンカード151"),
    "S12a": ("VSTAR Universe", "VSTARユニバース"),
    "S8b": ("VMAX Climax", "VMAXクライマックス"),
    "S4a": ("Shiny Star V", "シャイニースターV"),
    "SM12a": ("Tag All Stars", "タッグオールスターズ"),
    "SV1S": ("Scarlet ex", "スカーレットex"), "SV1V": ("Violet ex", "バイオレットex"),
    "SV2D": ("Clay Burst", "クレイバースト"), "SV2P": ("Snow Hazard", "スノーハザード"),
    "SV3": ("Ruler of the Black Flame", "黒炎の支配者"),
    "SV3a": ("Raging Surf", "レイジングサーフ"),
    "SV4K": ("Ancient Roar", "古代の咆哮"), "SV4M": ("Future Flash", "未来の一閃"),
    "SV5K": ("Wild Force", "ワイルドフォース"), "SV5M": ("Cyber Judge", "サイバージャッジ"),
    "SV5a": ("Crimson Haze", "クリムゾンヘイズ"), "SV6": ("Mask of Change", "変幻の仮面"),
    "SV6a": ("Night Wanderer", "ナイトワンダラー"), "SV7": ("Stellar Miracle", "ステラミラクル"),
    "SV7a": ("Paradise Dragona", "楽園ドラゴーナ"), "SV8": ("Super Electric Breaker", "超電ブレイカー"),
    "SV9": ("Battle Partners", "バトルパートナーズ"), "SV9a": ("Heat Wave Arena", "熱風のアリーナ"),
    "SV10": ("The Glory of Team Rocket", "ロケット団の栄光"),
    "SV11B": ("Black Bolt", "ブラックボルト"), "SV11W": ("White Flare", "ホワイトフレア"),
    "M1L": ("Mega Brave", "メガブレイブ"), "M1S": ("Mega Symphonia", "メガシンフォニア"),
    "M2": ("Inferno X", "インフェルノX"), "M4": ("Ninja Spinner", "ニンジャスピナー"),
    "S6a": ("Eevee Heroes", "イーブイヒーローズ"), "S7R": ("Blue Sky Stream", "蒼空ストリーム"),
    "S7D": ("Skyscraping Perfection", "摩天パーフェクト"), "S8": ("Fusion Arts", "フュージョンアーツ"),
    "S9": ("Star Birth", "スターバース"), "S10a": ("Dark Phantasma", "ダークファンタズマ"),
    "S11": ("Lost Abyss", "ロストアビス"), "S11a": ("Incandescent Arcana", "白熱のアルカナ"),
    "S12": ("Paradigm Trigger", "パラダイムトリガー"),
}
OP_SETS = {
    "OP01": ("Romance Dawn", "ロマンスドーン"), "OP02": ("Paramount War", "頂上決戦"),
    "OP03": ("Pillars of Strength", "強大な敵"), "OP04": ("Kingdoms of Intrigue", "謀略の王国"),
    "OP05": ("Awakening of the New Era", "新時代の主役"), "OP06": ("Wings of the Captain", "双璧の覇者"),
    "OP07": ("500 Years in the Future", "500年後の未来"), "OP08": ("Two Legends", "二つの伝説"),
    "OP09": ("Emperors in the New World", "新たなる皇帝"), "OP10": ("Royal Blood", "王族の血統"),
    "OP11": ("A Fist of Divine Speed", "神速の拳"), "OP12": ("Legacy of the Master", "師弟の絆"),
    "OP13": ("Carrying on His Will", "受け継がれる意志"), "PRB01": ("The Best", "プレミアムブースター"),
    "EB01": ("Memorial Collection", "メモリアルコレクション"),
}

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "CardNavo sealed catalogue"})
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.load(r)

def key(s):
    return re.sub(r"[^a-z0-9]", "", s.lower().replace("é", "e"))

def build(game, products, guides, localized=None):
    prices = {p["idProduct"]: p for p in guides}
    rows = []
    for p in products:
        category = p["categoryName"]
        if re.search(r"coins|lots?|\bPCG Set\b", category, re.I):
            continue
        name = p["name"]
        if re.search(r"playmat|sleeve|coin|binder|deck box|rubber|poster|plush", name, re.I):
            continue
        aliases = []
        for code, (english, native) in (JP_SETS if game == "POKEMON" else OP_SETS).items():
            if key(english) in key(name):
                aliases.extend([code, native, english])
        for english, native in (localized or {}).items():
            if key(english) in key(name):
                aliases.append(native)
        # Namespace avoids collisions with existing positive TCGplayer product ids.
        guide = prices.get(p["idProduct"], {})
        reference = guide.get("trend") or guide.get("avg30") or guide.get("avg7")
        if not isinstance(reference, (int, float)) or reference <= 0:
            reference = None
        rows.append({"productId": -p["idProduct"], "cardmarketId": p["idProduct"], "name": name,
                     "groupName": category, "languages": ["JA"] if re.search(r"\bJapanese\b|\bJapan\b", name, re.I) else ["DE"] if re.search(r"\bGerman\b|\bDeutsch\b", name, re.I) else [], "aliases": sorted(set(aliases)), "referencePrice": reference})
    return {"schemaVersion": 2, "game": game, "updated": datetime.now(timezone.utc).isoformat(),
            "priceScope": "aggregate-reference-not-language-specific", "items": rows}

def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    english = {p["id"]: p["name"] for p in fetch("https://api.tcgdex.net/v2/en/sets")}
    german = {p["id"]: p["name"] for p in fetch("https://api.tcgdex.net/v2/de/sets")}
    localized = {name: german[code] for code, name in english.items() if code in german}
    for game, number in [("POKEMON", 6), ("ONE_PIECE", 18)]:
        products = fetch(f"{BASE}/productList/products_nonsingles_{number}.json")["products"]
        guides = fetch(f"{BASE}/priceGuide/price_guide_{number}.json")["priceGuides"]
        data = build(game, products, guides, localized if game == "POKEMON" else None)
        (out / f"SEALED_REGIONAL_{game}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
        print(game, len(data["items"]), "sealed product templates")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "out")
