"""How much does the scan-trained grade model move when the same card is pictured worse?

For cards the model never saw, the learned features are computed twice: from the scan as it is, and from a
copy degraded the way a phone photo typically is (60 % resolution, slight blur, JPEG quality 70, a brightness
and colour shift). Both go through the fitted model. A model that is safe to use on phone photos must give
nearly the same answer for both. This is a necessary check, NOT a validation on phone photos: real photos add
glare, perspective, sleeves and shadows that are not simulated here.

    python3 stress_test.py RUN_DIR TAR metadata.csv [--limit 300]

TAR may be the first part of a shard (a truncated archive is read up to the cut).
"""
import csv, io, json, os, pickle, sys, tarfile, tempfile
import numpy as np
import torch
from PIL import Image, ImageEnhance, ImageFilter
import extract_features as X
import pgdata as D
import train_grade_v2 as T


def degrade(im, rng):
    w, h = im.size
    out = im.resize((int(w * 0.6), int(h * 0.6)), Image.BILINEAR).filter(ImageFilter.GaussianBlur(0.8)).resize((w, h), Image.BILINEAR)
    out = ImageEnhance.Brightness(out).enhance(float(rng.uniform(0.85, 1.15)))
    out = ImageEnhance.Color(out).enhance(float(rng.uniform(0.85, 1.1)))
    buf = io.BytesIO(); out.save(buf, "JPEG", quality=70)
    return Image.open(io.BytesIO(buf.getvalue())).convert("RGB")


def card_features(pair, tmpdir, change=None):
    n = len(X.WINDOWS)
    e = np.full((len(X.VIEWS), X.DIM), np.nan, np.float32)
    for si, side in enumerate(X.SIDES):
        im = D.decode(pair[side])
        if im is None:
            return None
        tmp = os.path.join(tmpdir, side + ".jpg"); open(tmp, "wb").write(pair[side])
        box = X.card_box(im, tmp)                      # the outline is taken from the original for both versions
        if change:
            im = change(im)
        vs = X.views(im, box)
        f = X.embed(torch.from_numpy(np.stack([np.asarray(v, np.uint8) for v in vs])).permute(0, 3, 1, 2)).astype(np.float32)
        e[si * n:(si + 1) * n] = f[:n]; e[2 * n + si] = f[n]
    return e


def main(run, tar, meta_path, limit=300):
    sys.modules["__main__"].Ordinal = T.Ordinal
    m = pickle.load(open(os.path.join(run, "models.pkl"), "rb"))["final"]["learned"]
    meta = {r["cert_id"]: r for r in csv.DictReader(open(meta_path, encoding="utf-8", errors="replace"))}
    rng = np.random.default_rng(3); tmpdir = tempfile.mkdtemp()
    clean, worse, y, pending = [], [], [], {}
    try:
        for name, data in D.tar_images(tar):
            cert, _, rest = name.partition("_"); side = rest.split(".")[0]
            pending.setdefault(cert, {})[side] = data
            if len(pending[cert]) < 2:
                continue
            pair = pending.pop(cert)
            g, q = D.psa_grade(meta.get(cert, {}).get("grade"))
            if g is None or q:
                continue
            a = card_features(pair, tmpdir); b = card_features(pair, tmpdir, lambda im: degrade(im, rng))
            if a is None or b is None:
                continue
            clean.append(a); worse.append(b); y.append(int(g))
            if len(y) >= limit:
                break
    except (tarfile.ReadError, EOFError):
        pass                                           # the archive was cut on purpose
    y = np.array(y); views = X.VIEWS
    Pa = m.proba(T.pooled(np.stack(clean), views)); Pb = m.proba(T.pooled(np.stack(worse), views))
    ga, gb = m.grades[np.argmax(Pa, 1)], m.grades[np.argmax(Pb, 1)]
    yt = np.maximum(y, m.grades.min())
    out = {"cards": int(len(y)), "grade_counts": {str(k): int((y == k).sum()) for k in sorted(set(y.tolist()), reverse=True)},
           "mae_scan": float(np.mean(np.abs(ga - yt))), "mae_degraded": float(np.mean(np.abs(gb - yt))),
           "within_1_scan": float(np.mean(np.abs(ga - yt) <= 1)), "within_1_degraded": float(np.mean(np.abs(gb - yt) <= 1)),
           "same_most_likely_grade": float(np.mean(ga == gb)),
           "mean_shift_in_expected_grade": float(np.mean(Pb @ m.grades - Pa @ m.grades)),
           "mean_absolute_shift_in_expected_grade": float(np.mean(np.abs(Pb @ m.grades - Pa @ m.grades))),
           "share_called_10_scan": float(np.mean(ga == 10)), "share_called_10_degraded": float(np.mean(gb == 10)),
           "degradation": "60 % resolution, Gaussian blur 0.8 px, JPEG quality 70, brightness x0.85-1.15, colour x0.85-1.1"}
    json.dump(out, open(os.path.join(run, "stress_test.json"), "w"), indent=1)
    print(json.dumps(out, indent=1))


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0], a[1], a[2], int(a[a.index("--limit") + 1]) if "--limit" in a else 300)
