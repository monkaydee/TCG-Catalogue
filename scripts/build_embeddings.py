#!/usr/bin/env python3
"""Builds the picture index the app uses to find cards by their picture alone.

Every card image is turned into a fingerprint (an "embedding") by a small, free on-device image
model (MediaPipe's MobileNetV3-Large image embedder, Apache 2.0): one for the whole card and one
for its artwork window, joined together. The phone computes the same fingerprint of a scanned card
and looks for the closest ones.

To keep the download small, the fingerprints are reduced to 256 numbers (PCA, fitted here) and
stored as bytes. Outputs (published on the `embeddings` branch, see
.github/workflows/embeddings.yml):

    EMBED_META.json        {"model": url, "input": 224, "dims": 256, "games": {...}, "updated": ...}
    EMBED_PCA.bin          float32 mean[2560], float16 components[2560][256]   (little-endian)
    EMBED_<GAME>.bin       "TCGE", int32 count, int32 dims, float32 scale[dims], int8 rows[count][dims]
    EMBED_<GAME>.json      ["<cardId>" or "<cardId>|<printing>", ...] in row order

Embeddings of images seen before are kept in a cache file (restored by the workflow), so a weekly
run only downloads and embeds new cards.
"""
import concurrent.futures as cf
import io
import json
import os
import struct
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from PIL import Image

MODEL_URL = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/latest/mobilenet_v3_large.tflite"
INPUT = 224
DIMS = 256
# The artwork window of a card, as fractions of its width and height (same numbers in the app).
ART = (0.08, 0.10, 0.92, 0.55)
LIMIT = int(os.environ.get("EMBED_LIMIT", "0"))  # for local tests: cards per game


def get(url, tries=4, timeout=60):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "TCG-Catalogue picture index builder"})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - retry any network error
            if attempt == tries - 1:
                raise
            time.sleep(2 ** attempt)


def pokemon_cards():
    cards = json.loads(get("https://api.tcgdex.net/v2/en/cards", timeout=180))
    return [(c["id"], c["image"] + "/low.webp") for c in cards if c.get("image")]


def one_piece_cards():
    out = []
    for endpoint in ("allSetCards", "allSTCards"):
        for c in json.loads(get(f"https://optcgapi.com/api/{endpoint}/", timeout=180)):
            code, image_id, image = c.get("card_set_id"), c.get("card_image_id"), c.get("card_image")
            if code and image_id and image:
                # The printing (alt art, parallel …) is kept, so a picture match preselects it.
                out.append((f"{code}|{image_id}", image))
    return list(dict.fromkeys(out))


GAMES = {"POKEMON": pokemon_cards, "ONE_PIECE": one_piece_cards}


class Embedder:
    def __init__(self, model_path):
        from ai_edge_litert.interpreter import Interpreter

        self.it = Interpreter(model_path=model_path, num_threads=os.cpu_count() or 2)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]["index"]
        self.out = self.it.get_output_details()[0]["index"]

    def one(self, img):
        a = np.asarray(img.resize((INPUT, INPUT), Image.BILINEAR), dtype=np.float32)[None] / 255.0
        self.it.set_tensor(self.inp, a)
        self.it.invoke()
        v = self.it.get_tensor(self.out)[0].astype(np.float32)
        return v / (np.linalg.norm(v) + 1e-9)

    def card(self, img):
        w, h = img.size
        art = img.crop((int(w * ART[0]), int(h * ART[1]), int(w * ART[2]), int(h * ART[3])))
        v = np.concatenate([self.one(img), self.one(art)])
        return v / (np.linalg.norm(v) + 1e-9)


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "embeddings")
    cache_path = Path(sys.argv[2] if len(sys.argv) > 2 else "embed-cache.npz")
    out.mkdir(parents=True, exist_ok=True)
    model = out.parent / "mobilenet_v3_large.tflite"
    if not model.exists():
        model.write_bytes(get(MODEL_URL))
    embedder = Embedder(str(model))

    cache = {}
    if cache_path.exists():
        z = np.load(cache_path, allow_pickle=False)
        cache = dict(zip(z["keys"].tolist(), z["vectors"]))
    print(f"cache: {len(cache)} embeddings")

    per_game = {}
    for game, list_cards in GAMES.items():
        cards = list_cards()
        if LIMIT:
            cards = cards[:LIMIT]
        todo = [(cid, url) for cid, url in cards if url not in cache]
        print(f"{game}: {len(cards)} cards, {len(todo)} new")

        def fetch(item):
            cid, url = item
            try:
                return url, Image.open(io.BytesIO(get(url))).convert("RGB")
            except Exception as e:  # noqa: BLE001 - skip broken images
                print(f"  skip {cid}: {e}", file=sys.stderr)
                return url, None

        with cf.ThreadPoolExecutor(32) as pool:
            for n, (url, img) in enumerate(pool.map(fetch, todo)):
                if img is not None:
                    cache[url] = embedder.card(img).astype(np.float16)
                if n % 1000 == 999:
                    print(f"  {n + 1}/{len(todo)}")
        per_game[game] = [(cid, url) for cid, url in cards if url in cache]

    np.savez(cache_path, keys=np.array(list(cache.keys())), vectors=np.stack(list(cache.values())))

    # PCA over all cards of all games, so every game uses the same reduction.
    everything = np.stack([cache[url] for rows in per_game.values() for _, url in rows]).astype(np.float32)
    mean = everything.mean(0)
    centered = everything - mean
    cov = centered.T @ centered / len(everything)
    values, vectors = np.linalg.eigh(cov)
    components = vectors[:, ::-1][:, :DIMS].astype(np.float32)  # [2560][DIMS], largest first
    with open(out / "EMBED_PCA.bin", "wb") as f:
        f.write(mean.astype("<f4").tobytes())
        f.write(components.astype("<f2").tobytes())

    meta = {"model": MODEL_URL, "input": INPUT, "dims": DIMS, "art": ART, "games": {},
            "updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    for game, rows in per_game.items():
        x = (np.stack([cache[url] for _, url in rows]).astype(np.float32) - mean) @ components
        x /= np.linalg.norm(x, axis=1, keepdims=True) + 1e-9
        scale = np.abs(x).max(0) / 127.0 + 1e-12
        q = np.clip(np.round(x / scale), -127, 127).astype(np.int8)
        with open(out / f"EMBED_{game}.bin", "wb") as f:
            f.write(b"TCGE")
            f.write(struct.pack("<ii", len(rows), DIMS))
            f.write(scale.astype("<f4").tobytes())
            f.write(q.tobytes())
        (out / f"EMBED_{game}.json").write_text(json.dumps([cid for cid, _ in rows], separators=(",", ":")))
        meta["games"][game] = len(rows)
        print(f"{game}: {len(rows)} cards, {(out / f'EMBED_{game}.bin').stat().st_size // 1024} KB")
    (out / "EMBED_META.json").write_text(json.dumps(meta, indent=1))


if __name__ == "__main__":
    main()
