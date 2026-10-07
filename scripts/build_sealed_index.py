#!/usr/bin/env python3
"""Cardmarket sealed catalogue, with explicit aggregate reference prices (EUR).

The public catalogue does not establish a listing's printed language. Rows are product
templates; the app requires confirmation and gets exact-language asking prices separately.
"""
import json
import re
import sys
import urllib.request
from html import unescape
from urllib.parse import urljoin
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
    "OP14": ("The Azure Sea's Seven", "蒼海の七傑"),
    "OP15": ("Adventure on Kami's Island", "神の島の冒険"),
    "OP16": ("The Time of Battle", "決戦の刻"),
    "OP17": ("The World's Strongest Warriors", "世界最強の戦士"),
    "OP18": ("The Dominance of God", "神の支配"),
    "PRB02": ("One Piece Card The Best vol.2", "ONE PIECE CARD THE BEST vol.2"),
    "EB02": ("Anime 25th Collection", "Anime 25th collection"),
    "EB03": ("One Piece Heroines Edition", "ONE PIECE Heroines Edition"),
    "EB04": ("Egghead Crisis", "EGGHEAD CRISIS"),
    "EB05": ("One Piece Heroines Edition vol.2", "ONE PIECE Heroines Edition vol.2"),
}

OFFICIAL_OP = "https://www.onepiece-cardgame.com"

def parse_official_boosters(text, today=None):
    """Publisher-backed Japanese packs; do not turn English/Asian rows into Japanese ones."""
    today = today or datetime.now(timezone.utc).date().isoformat()
    entries = []
    for block in re.findall(r'<li\b[^>]*class="linkListColBox"[^>]*>(.*?)</li>', text, re.S):
        title = re.search(r'<h4\b[^>]*class="linkListColTitle"[^>]*>(.*?)</h4>', block, re.S)
        if not title:
            continue
        title = unescape(re.sub(r'<[^>]+>', '', title[1])).strip()
        code = re.search(r'【((?:OP|PRB|EB)-\d{2})】', title)
        released = re.search(r'datetime="(\d{4}-\d{2}-\d{2})"', block)
        link = re.search(r'<a\b[^>]*href="([^"]+)"', block)
        image = re.search(r'<img\b[^>]*data-src="([^"]+)"', block)
        if not code or not released or released[1] > today or not link or not image:
            continue
        code = code[1].replace('-', '')
        native = title.split('【')[0]
        native = re.sub(r'^(?:ブースターパック|エクストラブースター|プレミアムブースター)\s*', '', native).strip()
        entries.append({'code': code, 'native': native,
                        'sourceUrl': urljoin(OFFICIAL_OP, unescape(link[1])),
                        'imageUrl': urljoin(OFFICIAL_OP, unescape(image[1]))})
    return entries

def official_boosters():
    entries = {}
    for page in range(1, 11):
        url = f'{OFFICIAL_OP}/products/?subcategory=boosters&page={page}'
        req = urllib.request.Request(url, headers={'User-Agent': 'CardNavo sealed catalogue'})
        with urllib.request.urlopen(req, timeout=45) as r:
            text = r.read().decode('utf-8')
        for entry in parse_official_boosters(text):
            entries[entry['code']] = entry
        pages = [int(n) for n in re.findall(r'page=(\d+)', text)]
        if page >= max(pages, default=page):
            break
    if 'OP14' not in entries:
        raise ValueError('Publisher Japanese booster catalogue is incomplete: OP14 missing')
    return list(entries.values())

def add_official_one_piece(index, entries):
    """Stable separate identities for native packs/boxes missing from the regional feed."""
    rows = index['items']
    for entry in entries:
        code = entry['code']
        english = OP_SETS.get(code, (entry['native'], ''))[0]
        aliases = sorted(set([code, re.sub(r'(\D+)(\d+)', r'\1-\2', code), english, entry['native']]))
        for unit, suffix in [('pack', 'Booster Pack'), ('box', 'Booster Box')]:
            existing = next((row for row in rows if 'JA' in row['candidateLanguages']
                             and code in row['aliases']
                             and ('box' in row['name'].lower()) == (unit == 'box')
                             and not re.search(r'case|sleeved|pre.?release|double|\d+\s*x', row['name'], re.I)), None)
            if existing is not None:
                existing['aliases'] = sorted(set(existing['aliases'] + aliases))
                existing['availabilityEvidence'] = f"Japanese set confirmed by Bandai · {code}"
                if unit == 'pack':
                    existing['imageUrl'] = entry['imageUrl']
                continue
            family = {'OP': 1, 'PRB': 2, 'EB': 3}[re.match(r'\D+', code)[0]]
            number = int(re.search(r'\d+', code)[0])
            rows.append({'productId': -(9_000_000_000_000 + family * 10_000 + number * 10 + (1 if unit == 'pack' else 2)),
                         'name': f'{english} Japanese {suffix}',
                         'groupName': f'One Piece Japanese {suffix}s',
                         'languages': ['JA'], 'candidateLanguages': ['JA'], 'aliases': aliases,
                         'referencePrice': None, 'imageUrl': entry['imageUrl'] if unit == 'pack' else None,
                         'sourceUrl': entry['sourceUrl'], 'availabilityEvidence': f'Japanese set confirmed by Bandai · {code}'})
    return index

def image_identity(name):
    # Preserve edition, contents, packaging and region qualifiers when deduplicating.
    name = re.sub(r'\((?:Non-English|English|Japanese|German)\)', '', name, flags=re.I)
    name = re.sub(r'\bbooster\b(?!\s*(?:box|pack|bundle|display))', 'Booster Pack', name, flags=re.I)
    return key(name)

def attach_catalogue_images(index, native):
    images = {}
    for row in native.get('items', []):
        language = row[4] if len(row) > 4 else 'EN'
        images.setdefault((language, image_identity(row[1])), row[0])
    for row in index['items']:
        # An English package is never used to depict a German or Japanese version.
        for language in row['candidateLanguages']:
            pid = images.get((language, image_identity(row['name'])))
            if pid:
                row.setdefault('imageUrls', {})[language] = f'https://tcgplayer-cdn.tcgplayer.com/product/{pid}_in_400x400.jpg'
                row.setdefault('catalogueProductIds', {})[language] = pid
    return index

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "CardNavo sealed catalogue"})
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.load(r)

def key(s):
    return re.sub(r"[^a-z0-9]", "", s.lower().replace("é", "e"))

def candidate_languages(game, name, aliases):
    # Candidate availability is separate from proof of a listing's printed language.
    # Bandai lists EN/JA/FR/ZH/KO, not DE: never clone generic One Piece rows into DE.
    explicit = [(code, pattern) for code, pattern in [
        ("JA", r"\bJapanese\b|\bJapan\b"), ("DE", r"\bGerman\b|\bDeutsch\b"),
        ("ZH", r"\bChinese\b"), ("KO", r"\bKorean\b"), ("FR", r"\bFrench\b"),
        ("EN", r"\bEnglish\b")]
        if re.search(pattern, name, re.I) and not (code == "EN" and "non-english" in name.lower())]
    if explicit:
        return [code for code, _ in explicit]
    if game == "ONE_PIECE":
        return ["JA"] if "non-english" in name.lower() else ["EN"]
    native_set = any(re.fullmatch(r"(?:SV|SM|S|M)\d+[A-Za-z]*", a) for a in aliases)
    return ["JA"] if native_set else ["EN", "DE"]

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
        matching = [(code, english, native) for code, (english, native) in (JP_SETS if game == "POKEMON" else OP_SETS).items() if key(english) in key(name)]
        if matching:
            code, english, native = max(matching, key=lambda item: len(key(item[1])))
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
                     "groupName": category, "languages": ["JA"] if re.search(r"\bJapanese\b|\bJapan\b", name, re.I) else ["DE"] if re.search(r"\bGerman\b|\bDeutsch\b", name, re.I) else [],
                     "candidateLanguages": candidate_languages(game, name, aliases), "aliases": sorted(set(aliases)), "referencePrice": reference})
    return {"schemaVersion": 4, "game": game, "updated": datetime.now(timezone.utc).isoformat(),
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
        if game == 'ONE_PIECE':
            data = add_official_one_piece(data, official_boosters())
        native_path = out / f'SEALED_{game}.json'
        if native_path.exists():
            attach_catalogue_images(data, json.loads(native_path.read_text()))
        encoded = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        (out / f"SEALED_REGIONAL_{game}.json").write_text(encoded)
        (out / f"SEALED_REGIONAL_V4_{game}.json").write_text(encoded)
        print(game, len(data["items"]), "sealed product templates")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "out")
