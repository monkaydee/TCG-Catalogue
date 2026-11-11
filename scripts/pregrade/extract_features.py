"""Reads one shard of graded-card scans and writes, per physical card, everything the grade models need:

  * the measurements the app already makes (centering, corner/edge wear: center.py, wear.py),
  * learned features of 28 close-ups (4 corners + 10 edge windows per side) and of the whole card,
    from a small ImageNet network (MobileNetV3-small) that is NOT trained here,
  * the manifest record of every image (hash, size, label, provenance, rights).

The scans themselves are not kept: the shard is read in place and can be deleted afterwards.

    python3 extract_features.py SHARD.tar metadata.csv OUT.npz [--limit N]
"""
import csv, io, os, sys, tempfile, time, hashlib
import numpy as np
import torch, torchvision
from PIL import Image
import center as C, wear as Wr, edge_only as E
import pgdata as D

SRC = "jyesr"
CROP = 256            # close-up size in scan pixels (about 9.5 mm at the scans' ~27 px/mm)
NET_IN = 224
SIDES = ("front", "back")
# close-up centres as fractions of the card: 4 corners, then 3 windows on each long edge, 2 on each short one
WINDOWS = [("TL", 0, 0), ("TR", 1, 0), ("BR", 1, 1), ("BL", 0, 1),
           ("L1", 0, .25), ("L2", 0, .5), ("L3", 0, .75), ("R1", 1, .25), ("R2", 1, .5), ("R3", 1, .75),
           ("T1", .33, 0), ("T2", .67, 0), ("B1", .33, 1), ("B2", .67, 1)]
VIEWS = [f"{s}_{w[0]}" for s in SIDES for w in WINDOWS] + [f"{s}_whole" for s in SIDES]

torch.set_num_threads(os.cpu_count() or 2)
_net = torchvision.models.mobilenet_v3_small(weights=torchvision.models.MobileNet_V3_Small_Weights.IMAGENET1K_V1).eval()
_MEAN = torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1)
_STD = torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1)
TAPS = (3, 8)         # early and middle layers: texture such as whitening lives there, not in the last layer
DIM = 576 + 2 * (24 + 48)


@torch.no_grad()
def embed(batch):
    """[n, 3, 224, 224] uint8 -> [n, DIM]: final pooled features plus mean and max of two earlier layers."""
    x = (batch.float() / 255 - _MEAN) / _STD
    extra = []
    for i, layer in enumerate(_net.features):
        x = layer(x)
        if i in TAPS:
            extra += [x.mean((2, 3)), x.amax((2, 3))]
    return torch.cat([x.mean((2, 3))] + extra, 1).numpy().astype(np.float16)


def card_box(im, tmp):
    """The card's outline in the scan (the scans leave a thin background strip), found as the app's Python
    twin does it, on a 1400 px copy."""
    small = C.load(tmp)
    h, w = small.shape[:2]
    k = im.height / h
    e = {s: E.edge(small, s) for s in "LRTB"}
    return e["L"] * k, e["T"] * k, im.width - e["R"] * k, im.height - e["B"] * k


def views(im, box):
    l, t, r, b = box
    out = []
    for _, fx, fy in WINDOWS:
        cx, cy = l + fx * (r - l), t + fy * (b - t)
        # keep the window inside the picture but always include the card's rim
        x0 = int(round(min(max(cx - CROP / 2, 0), im.width - CROP)))
        y0 = int(round(min(max(cy - CROP / 2, 0), im.height - CROP)))
        out.append(im.crop((x0, y0, x0 + CROP, y0 + CROP)).resize((NET_IN, NET_IN), Image.BILINEAR))
    out.append(im.crop(tuple(int(round(v)) for v in box)).resize((NET_IN, NET_IN), Image.BILINEAR))
    return out


def measure(tmp):
    """The app's own measurements (same code path as measure_psa_scans.py)."""
    a = C.load(tmp)
    h, w = a.shape[:2]
    e = {s: E.edge(a, s) for s in "LRTB"}
    box = tuple(int(round(x)) for x in (e["L"], e["T"], w - e["R"], h - e["B"]))
    flat = np.asarray(Image.fromarray(a.astype("uint8")).crop(box).resize((Wr.W, Wr.H), Image.BICUBIC), np.float32)
    c = C.centering(tmp)
    return Wr.wear(flat), (None if not c else (float(c["lr"]), float(c["tb"])))


def main(shard, meta_path, out, limit=None):
    meta = {r["cert_id"]: r for r in csv.DictReader(open(meta_path, encoding="utf-8", errors="replace"))}
    s = D.source(SRC)
    pending, done = {}, []
    certs, emb, wear, cent, hashes, records = [], [], [], [], [], []
    t0 = time.time()
    tmpdir = tempfile.mkdtemp(dir="/dev/shm" if os.path.isdir("/dev/shm") else None)

    def finish(cert, pair):
        row = meta.get(cert)
        if row is None:
            return
        grade, qualifier = D.psa_grade(row.get("grade"))
        e = np.zeros((len(VIEWS), DIM), np.float16)
        ok = np.zeros(len(VIEWS), bool)
        w_out, c_out, hs = {}, {}, {}
        for si, side in enumerate(SIDES):
            data = pair.get(side)
            im = D.decode(data) if data else None
            if im is None:
                continue
            tmp = os.path.join(tmpdir, f"{side}.jpg")
            open(tmp, "wb").write(data)
            try:
                box = card_box(im, tmp)
                vs = views(im, box)
                x = torch.from_numpy(np.stack([np.asarray(v, np.uint8) for v in vs])).permute(0, 3, 1, 2)
                f = embed(x)
                n = len(WINDOWS)
                e[si * n:(si + 1) * n] = f[:n]; ok[si * n:(si + 1) * n] = True
                e[2 * n + si] = f[n]; ok[2 * n + si] = True
                w_out[side], c_out[side] = measure(tmp)
            except Exception as ex:      # one unreadable picture must not stop the shard
                print("skip", cert, side, type(ex).__name__, file=sys.stderr)
                continue
            hs[side] = D.dhash(im)
            records.append(D.record(
                game="pokemon", physical_card_id=f"psa-{cert}", card_name=row.get("subject") or None,
                set=row.get("brand") or None, card_number=row.get("card_number") or None, language=D.language(row),
                side=side, source=SRC, license=s["license"], commercial_use=s["commercial_use"],
                image_path=f"{os.path.basename(shard)}::{cert}_{side}.jpg", image_sha256=hashlib.sha256(data).hexdigest(),
                width=im.width, height=im.height, certified_grader="PSA", verified_grade=grade, grade_qualifier=qualifier,
                label_provenance="psa_cert_listed_in_dataset_metadata_not_rechecked", annotation_status="no_defect_annotations"))
        if not ok.any():
            return
        certs.append(cert); emb.append(np.where(ok[:, None], e, np.nan).astype(np.float16))
        wear.append(w_out); cent.append(c_out); hashes.append(hs.get("front", hs.get("back", 0)))

    for name, data in D.tar_images(shard):
        cert, _, rest = name.partition("_")
        side = rest.split(".")[0]
        if side not in SIDES:
            continue
        pending.setdefault(cert, {})[side] = data
        if len(pending[cert]) == 2:
            finish(cert, pending.pop(cert))
            if len(certs) % 200 == 0 and certs:
                print(f"{len(certs)} cards, {(time.time() - t0) / len(certs):.2f} s/card", flush=True)
            if limit and len(certs) >= limit:
                break
    else:
        for cert, pair in pending.items():   # cards with only one side in this shard
            finish(cert, pair)
    np.savez_compressed(out, certs=np.array(certs), emb=np.stack(emb), views=np.array(VIEWS),
                        wear=np.array(wear, dtype=object), centering=np.array(cent, dtype=object),
                        dhash=np.array(hashes, dtype=np.uint64), records=np.array(records, dtype=object))
    print(f"wrote {out}: {len(certs)} cards, {len(records)} images, {time.time() - t0:.0f} s")


if __name__ == "__main__":
    a = sys.argv[1:]
    lim = int(a[a.index("--limit") + 1]) if "--limit" in a else None
    main(a[0], a[1], a[2], lim)
