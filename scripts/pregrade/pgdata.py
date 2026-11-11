"""Dataset handling for the pre-grader: resumable downloads, safe archive reading, image checks,
manifest records, near-duplicate detection and leak-free splits. See README.md.

    python3 pgdata.py sources                      # the rights ledger as a table
    python3 pgdata.py download jyesr 0 1 --to DIR  # shards 0 and 1 of a source (resumable)
    python3 pgdata.py manifest FEATURES.npz OUT.jsonl
"""
import csv, hashlib, io, json, os, re, sys, tarfile, time, urllib.request, urllib.error
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = json.load(open(os.path.join(HERE, "sources.json"), encoding="utf-8"))["sources"]


# ---------- rights ledger ----------

def source(name):
    return next(s for s in SOURCES if s["id"] == name)


def usable_for(name, purpose):
    """purpose: 'research' (measure, fit, evaluate privately) or 'commercial' (weights shipped in a paid product)."""
    s = source(name)
    return s["research_use"] if purpose == "research" else s["commercial_use"] == "yes"


# ---------- download ----------

def download(url, dest, tries=8, chunk=1 << 20, token=None):
    """Resumes a partial file with an HTTP Range request; retries with exponential backoff."""
    os.makedirs(os.path.dirname(dest) or ".", exist_ok=True)
    part = dest + ".part"
    for attempt in range(tries):
        have = os.path.getsize(part) if os.path.exists(part) else 0
        req = urllib.request.Request(url, headers={"Range": f"bytes={have}-", "User-Agent": "cardnavo-pregrade/1"})
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                if r.status == 200 and have:      # server ignored the range: start over
                    have = 0
                with open(part, "ab" if have else "wb") as f:
                    while True:
                        b = r.read(chunk)
                        if not b:
                            break
                        f.write(b)
            os.replace(part, dest)
            return dest
        except urllib.error.HTTPError as e:
            if e.code == 416:                      # already complete
                os.replace(part, dest)
                return dest
            if e.code in (401, 403, 404):
                raise PermissionError(f"{url}: HTTP {e.code} (needs access or does not exist)") from e
            wait = min(120, 2 ** attempt)
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            wait = min(120, 2 ** attempt)
        time.sleep(wait)
    raise IOError(f"gave up on {url}")


def sha256(path, chunk=1 << 20):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(chunk), b""):
            h.update(b)
    return h.hexdigest()


# ---------- archives and images ----------

SAFE_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$")
MAX_MEMBER = 40 << 20


def tar_images(path):
    """Yields (name, bytes) for the image files of a tar WITHOUT extracting to disk. Members with path
    separators, links, devices, odd names or absurd sizes are skipped, so a hostile archive can neither
    write outside a directory nor fill the disk."""
    with tarfile.open(path, "r|*") as t:
        for m in t:
            name = os.path.basename(m.name)
            if not m.isfile() or name != m.name.lstrip("./") or not SAFE_NAME.match(name):
                continue
            if not name.lower().endswith((".jpg", ".jpeg", ".png")) or not 0 < m.size <= MAX_MEMBER:
                continue
            f = t.extractfile(m)
            if f is not None:
                yield name, f.read()


def decode(data, min_side=600):
    """A fully decoded RGB image, or None for a corrupt, truncated or too-small file."""
    from PIL import Image
    try:
        Image.open(io.BytesIO(data)).verify()
        im = Image.open(io.BytesIO(data)).convert("RGB")
        im.load()
    except Exception:
        return None
    return im if min(im.size) >= min_side else None


def dhash(im, size=8):
    """64-bit difference hash; near-duplicate images differ in only a few bits."""
    from PIL import Image
    g = np.asarray(im.convert("L").resize((size + 1, size), Image.BILINEAR), np.int16)
    bits = (g[:, 1:] > g[:, :-1]).flatten()
    return int("".join("1" if b else "0" for b in bits), 2)


def near_duplicates(hashes, max_bits=4):
    """Groups of indices whose hashes differ by at most max_bits (banded lookup, exact within bands)."""
    bands = {}
    for i, h in enumerate(hashes):
        for b in range(4):
            bands.setdefault((b, (h >> (16 * b)) & 0xFFFF), []).append(i)
    parent = list(range(len(hashes)))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for members in bands.values():
        if len(members) > 400:      # a band shared by hundreds (plain card backs): too coarse to pair up
            continue
        for a in range(len(members)):
            for b in range(a + 1, len(members)):
                i, j = members[a], members[b]
                if bin(hashes[i] ^ hashes[j]).count("1") <= max_bits:
                    parent[find(i)] = find(j)
    groups = {}
    for i in range(len(hashes)):
        groups.setdefault(find(i), []).append(i)
    return [g for g in groups.values() if len(g) > 1]


# ---------- labels ----------

def psa_grade(label):
    """'GEM MT 10' -> (10.0, None); 'NM 7 (MC)' -> (7.0, 'MC'); 'N0: AUTHENTIC' / '' -> (None, ...).
    Qualified grades (OC, MC, MK, ST, PD, OF) are a different scale and never train the ordinary model."""
    s = (label or "").strip()
    q = re.search(r"\((OC|MC|MK|ST|PD|OF)\)", s)
    m = re.search(r"(\d+(?:\.5)?)\s*(?:\(|$)", s)
    if not m or "AUTHENTIC" in s.upper():
        return None, q.group(1) if q else None
    return float(m.group(1)), q.group(1) if q else None


def card_identity(row):
    """The printed card (not the physical copy): the same artwork must not sit on both sides of a split."""
    return re.sub(r"\s+", " ", f"{row.get('year', '')}|{row.get('brand', '')}|{row.get('card_number', '')}|{row.get('subject', '')}".upper())


def language(row):
    name = f"{row.get('brand', '')} {row.get('card_name', '')}".upper()
    for word, code in (("JAPANESE", "ja"), ("KOREAN", "ko"), ("CHINESE", "zh"), ("GERMAN", "de"), ("FRENCH", "fr"),
                       ("ITALIAN", "it"), ("SPANISH", "es"), ("PORTUGUESE", "pt")):
        if word in name:
            return code
    return "en"


# ---------- splits ----------

def grouped_split(groups, labels, seed=7, parts=(0.70, 0.15, 0.15)):
    """Train/validation/test by GROUP, roughly stratified by label: every group lands in exactly one part.
    Groups are assigned largest first to whichever part is furthest below its share for the group's
    main label, so rare grades are spread instead of landing in one part by chance."""
    rng = np.random.default_rng(seed)
    by = {}
    for i, g in enumerate(groups):
        by.setdefault(g, []).append(i)
    keys = list(by)
    rng.shuffle(keys)
    keys.sort(key=lambda k: -len(by[k]))
    counts = {}
    out = np.empty(len(groups), dtype=object)
    names = ("train", "val", "test")
    for k in keys:
        idx = by[k]
        ls, cs = np.unique([labels[i] for i in idx], return_counts=True)
        main = ls[np.argmax(cs)]
        c = counts.setdefault(main, np.zeros(3))
        want = np.array(parts) * (c.sum() + len(idx))
        p = int(np.argmax(want - c))
        c[p] += len(idx)
        for i in idx:
            out[i] = names[p]
    return out


def union_groups(*keys_per_item):
    """Connected components: items sharing ANY key (same printed card, same submission block,
    near-duplicate picture) end up in one group."""
    n = len(keys_per_item[0])
    parent = list(range(n))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for keys in keys_per_item:
        first = {}
        for i, k in enumerate(keys):
            if k is None:
                continue
            if k in first:
                parent[find(i)] = find(first[k])
            else:
                first[k] = i
    return [find(i) for i in range(n)]


# ---------- manifest ----------

def record(**kw):
    """One image in the manifest. Unknown stays None: nothing is filled in with a guess."""
    base = {"game": None, "physical_card_id": None, "card_name": None, "set": None, "card_number": None, "language": None,
            "side": None, "source": None, "license": None, "commercial_use": None, "image_path": None, "image_sha256": None,
            "width": None, "height": None, "certified_grader": None, "verified_grade": None, "grade_qualifier": None,
            "estimated_grade": None, "label_provenance": "unverified", "defects": [], "annotation_status": "pending_review",
            "split": None, "group": None}
    unknown = set(kw) - set(base)
    assert not unknown, unknown
    base.update(kw)
    return base


def main(argv):
    if argv[:1] == ["sources"]:
        for s in SOURCES:
            print(f"{s['id']:<22} research={str(s['research_use']):<5} commercial={s['commercial_use']:<8} {s['status']}")
    elif argv[:1] == ["download"]:
        to = argv[argv.index("--to") + 1]
        s = source(argv[1])
        for n in argv[2:argv.index("--to")]:
            url = s["shard_url"].format(n=int(n))
            print(download(url, os.path.join(to, os.path.basename(url)), token=os.environ.get("HF_TOKEN")))
    elif argv[:1] == ["manifest"]:
        z = np.load(argv[1], allow_pickle=True)
        with open(argv[2], "w", encoding="utf-8") as f:
            for r in z["records"]:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
        print(len(z["records"]), "records")
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
