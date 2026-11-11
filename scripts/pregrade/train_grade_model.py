"""Fits the pre-grader's grade model and writes app/.../grade/GradeWeights.kt.

Input: the JSON written by measure_psa_scans.py (centering and corner/edge wear of PSA's own scans
of graded cards, see DATASETS.md). The model is an ordinal logistic one: for every grade k a logistic
model of "PSA k or better" over the same 50 features (per side: worst centering, and for each
corner and edge the wear measured by Wear.kt).

    python3 train_grade_model.py measurements.json [more.json ...]
"""
import json, re, sys, os
import numpy as np
from scipy.optimize import minimize
from scipy.special import expit
from sklearn.model_selection import train_test_split
from sklearn.metrics import roc_auc_score

ZONES = ["T", "R", "B", "L", "TL", "TR", "BR", "BL"]
# Per side: worst centering, then per edge and corner the log share of defect pixels, the log share
# of whitened pixels and how strongly they stand out (see Wear.kt).
FEATURES = [f"{side}_{x}" for side in ("front", "back")
            for x in ["centering"] + [f"{z}_{m}" for z in ZONES for m in ("defects", "white", "strength")]]
GRADES = [10, 9, 8, 7, 6, 5]
LOG_EPS = 0.005
# Centering beyond these is almost surely a mismeasure; the app clips the same way.
_side_high = [float(np.log(1 + LOG_EPS)), float(np.log(1 + LOG_EPS)), 10.0] * 8
CLIP_LOW = [50.0] + [float(np.log(LOG_EPS)), float(np.log(LOG_EPS)), 0.0] * 8 + [50.0] + [float(np.log(LOG_EPS)), float(np.log(LOG_EPS)), 0.0] * 8
CLIP_HIGH = [80.0] + _side_high + [90.0] + _side_high
OUT = os.path.join(os.path.dirname(__file__), "../../app/src/main/java/com/monkaydee/tcgcatalogue/grade/GradeWeights.kt")


def grade(label):
    m = re.search(r"(\d+(?:\.5)?)\s*(?:\(|$)", label.strip())
    return int(float(m.group(1))) if m else None


def rows(res):
    X, Y, zones10, zones_low = [], [], [], []
    for o in res.values():
        g = grade(o.get("grade", ""))
        if g is None or not (o.get("front") or o.get("back")):
            continue
        row = []
        for side in ("front", "back"):
            c = o.get(side + "_c")
            row.append(max(c[0], 100 - c[0], c[1], 100 - c[1]) if c else np.nan)
            w = o.get(side)
            if not w:
                row += [np.nan] * 24
                continue
            for z in ZONES:
                d, white, strength = w[z]
                row += [np.log(d + LOG_EPS), np.log(white + LOG_EPS), strength]
            (zones10 if g == 10 else zones_low if g <= 6 else []).extend(w[z][0] for z in ZONES)
        X.append(row)
        Y.append(g)
    return np.array(X, float), np.array(Y), np.array(zones10), np.array(zones_low)


def fit(Z, Y):
    # Worse centering or stronger wear must never improve grade probability.
    # Coefficients are constrained non-positive; intercept remains unconstrained.
    A = np.column_stack([np.ones(len(Z)), Z])
    weights = []
    for k in GRADES:
        y = (Y >= k).astype(float)
        def objective(w):
            logits = A @ w
            loss = np.mean(np.logaddexp(0, logits) - y * logits) + 0.01 * np.sum(w[1:] ** 2)
            gradient = A.T @ (expit(logits) - y) / len(y)
            gradient[1:] += 0.02 * w[1:]
            return loss, gradient
        result = minimize(objective, np.zeros(A.shape[1]), jac=True, method="L-BFGS-B",
                          bounds=[(None, None)] + [(None, 0)] * Z.shape[1])
        if not result.success:
            raise RuntimeError(result.message)
        weights.append(result.x)
    return weights


def predict(W, Z):
    out = []
    for z in Z:
        prev, at = 0.0, {}
        for k, w in zip(GRADES, W):
            prev = at[k] = max(prev, 1 / (1 + np.exp(-(w[0] + w[1:] @ z))))
        p = {GRADES[0]: at[GRADES[0]]}
        for a, b in zip(GRADES[1:], GRADES[:-1]):
            p[a] = at[a] - at[b]
        p[GRADES[-1] - 1] = 1 - at[GRADES[-1]]
        out.append(p)
    return out


def main(paths):
    res = {}
    for p in paths:
        res.update(json.load(open(p)))
    X, Y, z10, zlow = rows(res)
    X = np.clip(X, CLIP_LOW, CLIP_HIGH)
    # res is keyed by physical certificate: both sides stay in one row.
    # Phone validation must also group repeat captures by physical card/certificate.
    idx = np.arange(len(Y))
    tr, te = train_test_split(idx, test_size=.25, random_state=0)
    # All learned preprocessing is fitted only on training cards.
    mean = np.nanmean(X[tr], 0)
    mean = np.nan_to_num(mean)
    filled = np.where(np.isnan(X), mean, X)
    scale = filled[tr].std(0) + 1e-9
    Z = (filled - mean) / scale
    P = predict(fit(Z[tr], Y[tr]), Z[te])
    yt = np.minimum(Y[te], 10)
    pred = np.array([max(p, key=p.get) for p in P])
    print(f"cards {len(Y)}, test {len(te)}")
    print(f"exact {np.mean(pred == yt):.3f} (always-10: {np.mean(yt == 10):.3f}), "
          f"within 1 {np.mean(abs(pred - yt) <= 1):.3f} (always-10: {np.mean(yt >= 9):.3f})")
    for k in (10, 9, 8):
        s = [sum(v for kk, v in p.items() if kk >= k) for p in P]
        print(f"AUC grade>={k}: {roc_auc_score((yt >= k).astype(int), s):.3f}")

    # Refit for export only after the held-out metrics have been reported.
    mean = np.nan_to_num(np.nanmean(X, 0))
    filled = np.where(np.isnan(X), mean, X)
    scale = filled.std(0) + 1e-9
    Z = (filled - mean) / scale
    W = fit(Z, Y)
    # corner/edge levels: light = above 90 % of PSA 10 zones, visible = above 99 %,
    # heavy = typical worst zone of PSA 6 and lower
    levels = [float(np.percentile(z10, 90)), float(np.percentile(z10, 99)), float(np.percentile(zlow, 90))]
    levels = sorted(levels)
    write(mean, scale, W, levels, len(Y))


def num(x):
    s = f"{x:.6g}"
    return s if any(c in s for c in ".en") else s + ".0"   # Kotlin needs a Double literal


def arr(v):
    return "doubleArrayOf(" + ", ".join(num(x) for x in v) + ")"


def write(mean, scale, W, levels, n):
    feats = ",\n        ".join(", ".join(f'"{f}"' for f in FEATURES[i:i + 5]) for i in range(0, len(FEATURES), 5))
    weights = ",\n        ".join(arr(w) for w in W)
    kt = f"""package com.monkaydee.tcgcatalogue.grade

// Written by scripts/pregrade/train_grade_model.py from {n} PSA-graded cards — do not edit by hand.
internal object GradeWeights {{
    val FEATURES = listOf(
        {feats},
    )
    val CLIP_LOW = {arr(CLIP_LOW)}
    val CLIP_HIGH = {arr(CLIP_HIGH)}
    val MEAN = {arr(mean)}
    val SCALE = {arr(scale)}
    const val LOG_EPS = {LOG_EPS}
    val GRADES = listOf({", ".join(map(str, GRADES))})
    /** Per grade: intercept, then one weight per feature (on standardised features). */
    val WEIGHTS = listOf(
        {weights},
    )
    /** Defect share of one corner or edge from which it counts as light / visible / heavy wear. */
    val ZONE_LEVELS = {arr(levels)}
}}
"""
    open(OUT, "w").write(kt)
    print("wrote", os.path.normpath(OUT), "levels", [round(x, 4) for x in levels])


if __name__ == "__main__":
    main(sys.argv[1:])
