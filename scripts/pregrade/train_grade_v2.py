"""Fits and evaluates grade models on the features written by extract_features.py.

Three models are compared on the SAME held-out cards:
  measured  the 50 measurements the app makes (centering, rim wear), the model design shipped as GradeWeights.kt
  learned   features of 28 close-ups and the whole card from a frozen ImageNet network
  combined  both

Split: by printed card (year, set, number, name), so the same artwork never sits in two parts, 70/15/15,
spread by grade. Every choice (regularisation, which model) is made on the validation part; the test part
is scored once at the end. A second test holds out whole runs of consecutive certificates, which are
usually one submission scanned in one batch.

    python3 train_grade_v2.py OUT_DIR FEATURES.npz [FEATURES.npz ...]
"""
import csv, json, os, sys, time
import numpy as np
from scipy.special import expit
from sklearn.decomposition import PCA
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score
import pgdata as D
import train_grade_model as V1

SIDES = ("front", "back")
SEED = 7


# ---------- data ----------

def load(paths, meta_path):
    meta = {r["cert_id"]: r for r in csv.DictReader(open(meta_path, encoding="utf-8", errors="replace"))}
    certs, emb, wear, cent, dh, views = [], [], [], [], [], None
    for p in paths:
        z = np.load(p, allow_pickle=True)
        views = list(z["views"])
        certs += list(z["certs"]); emb.append(z["emb"]); wear += list(z["wear"]); cent += list(z["centering"]); dh += list(z["dhash"])
    emb = np.concatenate(emb).astype(np.float32)
    rows = [meta[c] for c in certs]
    grade = np.array([D.psa_grade(r["grade"])[0] or np.nan for r in rows])
    qualified = np.array([D.psa_grade(r["grade"])[1] is not None for r in rows])
    keep = ~np.isnan(grade) & ~qualified
    info = {"cards_read": len(certs), "no_numeric_grade": int(np.isnan(grade).sum()), "qualified_grade": int(qualified.sum())}
    idx = np.where(keep)[0]
    return ([certs[i] for i in idx], [rows[i] for i in idx], np.floor(grade[idx]).astype(int), emb[idx],
            [wear[i] for i in idx], [cent[i] for i in idx], [int(dh[i]) for i in idx], views, info)


def measured_features(wear, cent):
    """The 50 features of train_grade_model.py, built the same way."""
    X = []
    for w, c in zip(wear, cent):
        row = []
        for side in SIDES:
            cc = c.get(side)
            row.append(max(cc[0], 100 - cc[0], cc[1], 100 - cc[1]) if cc else np.nan)
            ws = w.get(side)
            if not ws:
                row += [np.nan] * 24
                continue
            for z in V1.ZONES:
                d, white, strength = ws[z]
                row += [np.log(d + V1.LOG_EPS), np.log(white + V1.LOG_EPS), strength]
        X.append(row)
    return np.clip(np.array(X, float), V1.CLIP_LOW, V1.CLIP_HIGH)


def pooled(emb, views):
    """Per side: mean and max over the 4 corner close-ups, mean and max over the 10 edge close-ups, the whole card.
    The max keeps the worst corner visible; a missing side is filled with the training mean later."""
    n = len(views) // 2 - 1          # close-ups per side
    out = []
    for si in range(2):
        v = emb[:, si * n:(si + 1) * n]
        corners, edges = v[:, :4], v[:, 4:]
        out += [np.nanmean(corners, 1), np.nanmax(corners, 1), np.nanmean(edges, 1), np.nanmax(edges, 1), emb[:, 2 * n + si]]
    return np.concatenate(out, 1)


# ---------- ordinal model ----------

class Ordinal:
    """One logistic model of "grade k or better" per k; exact-grade chances are differences of neighbours."""

    def __init__(self, C=1.0, pca=None):
        self.C, self.pca_n = C, pca

    def fit(self, X, y):
        self.mean = np.nan_to_num(np.nanmean(X, 0))
        Xf = np.where(np.isnan(X), self.mean, X)
        self.scale = Xf.std(0) + 1e-6
        Z = (Xf - self.mean) / self.scale
        self.pca = PCA(self.pca_n, random_state=SEED).fit(Z) if self.pca_n and self.pca_n < Z.shape[1] else None
        Z = self.pca.transform(Z) if self.pca else Z
        # the same grade buckets as the shipped model (10 … 5, then "4 or below"), so all models are scored alike
        self.ks = [k for k in V1.GRADES if 30 <= (y >= k).sum() <= len(y) - 30]
        self.models = [LogisticRegression(C=self.C, max_iter=2000).fit(Z, (y >= k).astype(int)) for k in self.ks]
        return self

    def _z(self, X):
        Z = (np.where(np.isnan(X), self.mean, X) - self.mean) / self.scale
        return self.pca.transform(Z) if self.pca else Z

    def at_least(self, X):
        Z = self._z(X)
        P = np.stack([m.predict_proba(Z)[:, 1] for m in self.models], 1)   # columns: k = 10, 9, ...
        return np.maximum.accumulate(P, 1)                                   # a lower bar is never less likely

    def proba(self, X):
        """[n, grades]: columns for ks[0] (10) down to ks[-1], then "below the lowest k"."""
        A = self.at_least(X)
        P = np.concatenate([A[:, :1], np.diff(A, axis=1), 1 - A[:, -1:]], 1)
        return np.clip(P, 0, 1) / np.clip(P, 0, 1).sum(1, keepdims=True)

    @property
    def grades(self):
        return np.array(self.ks + [self.ks[-1] - 1])


def measured_v1(Xtr, ytr, Xte):
    """The shipped design itself: train_grade_model.fit (weights constrained so that worse measurements never help)."""
    mean = np.nan_to_num(np.nanmean(Xtr, 0))
    f = lambda X: np.where(np.isnan(X), mean, X)
    scale = f(Xtr).std(0) + 1e-9
    W = V1.fit((f(Xtr) - mean) / scale, ytr)
    P = V1.predict(W, (f(Xte) - mean) / scale)
    grades = np.array(V1.GRADES + [V1.GRADES[-1] - 1])
    return np.array([[p[g] for g in grades] for p in P]), grades


# ---------- metrics ----------

def interval(p, grades, mass=0.75):
    """The app's rule: the smallest run of neighbouring grades around the most likely one that covers `mass`."""
    order = np.argsort(-grades)       # grades descending -> index
    g, q = grades[order], p[order]
    i = int(np.argmax(q)); lo = hi = i; m = q[i]
    while m < mass:
        up = q[lo - 1] if lo > 0 else -1
        down = q[hi + 1] if hi + 1 < len(q) else -1
        if up < 0 and down < 0:
            break
        if up >= down:
            lo -= 1; m += up
        else:
            hi += 1; m += down
    return g[hi], g[lo]


def ece(prob, truth, bins=10):
    edges = np.linspace(0, 1, bins + 1); out = 0.0; table = []
    for a, b in zip(edges[:-1], edges[1:]):
        m = (prob >= a) & (prob < b if b < 1 else prob <= b)
        if m.sum():
            out += m.mean() * abs(prob[m].mean() - truth[m].mean())
            table.append([round(float(a), 1), int(m.sum()), round(float(prob[m].mean()), 3), round(float(truth[m].mean()), 3)])
    return float(out), table


def metrics(P, grades, y, rng=None):
    floor = grades.min()
    yt = np.maximum(y, floor)                     # grades below the lowest modelled one share its bucket
    pred = grades[np.argmax(P, 1)]
    expected = P @ grades
    out = {
        "n": int(len(y)),
        "mae": float(np.mean(np.abs(pred - yt))),
        "mae_expected": float(np.mean(np.abs(expected - yt))),
        "exact": float(np.mean(pred == yt)),
        "within_1": float(np.mean(np.abs(pred - yt) <= 1)),
    }
    for k in (10, 9, 8, 7):
        if k in grades and 0 < (yt >= k).sum() < len(yt):
            s = P[:, grades >= k].sum(1)
            out[f"auc_ge_{k}"] = float(roc_auc_score(yt >= k, s))
    lohi = np.array([interval(p, grades) for p in P])
    out["range_75_coverage"] = float(np.mean((yt >= lohi[:, 0]) & (yt <= lohi[:, 1])))
    out["range_75_mean_width"] = float(np.mean(lohi[:, 1] - lohi[:, 0] + 1))
    out["ece_ge_10"], out["reliability_ge_10"] = ece(P[:, grades >= 10].sum(1), (yt >= 10).astype(float))
    if 9 in grades:
        out["ece_ge_9"], _ = ece(P[:, grades >= 9].sum(1), (yt >= 9).astype(float))
    labels = sorted(set(grades.tolist()), reverse=True)
    out["confusion_rows_true_cols_pred"] = {"grades": labels, "counts": [[int(((yt == t) & (pred == p)).sum()) for p in labels] for t in labels]}
    out["per_grade_recall"] = {str(t): (float(np.mean(pred[yt == t] == t)) if (yt == t).any() else None) for t in labels}
    out["per_grade_within_1"] = {str(t): (float(np.mean(np.abs(pred[yt == t] - t) <= 1)) if (yt == t).any() else None) for t in labels}
    if rng is not None:                           # 95 % bootstrap intervals over cards
        b = [rng.integers(0, len(y), len(y)) for _ in range(500)]
        for name, f in (("mae", lambda i: np.mean(np.abs(pred[i] - yt[i]))), ("within_1", lambda i: np.mean(np.abs(pred[i] - yt[i]) <= 1)),
                        ("exact", lambda i: np.mean(pred[i] == yt[i]))):
            v = np.sort([f(i) for i in b]); out[name + "_ci95"] = [float(v[12]), float(v[487])]
    return out


def reference(ytr, y, grades):
    """What you get without looking at the card: always the commonest grade, and the training grade mix."""
    floor = grades.min()
    prior = np.array([np.mean(np.maximum(ytr, floor) == g) for g in grades])
    return np.tile(prior, (len(y), 1))


# ---------- run ----------

def main(out_dir, paths):
    os.makedirs(out_dir, exist_ok=True)
    t0 = time.time()
    meta_path = os.environ.get("PG_METADATA", os.path.join(os.path.dirname(paths[0]), "jyesr_metadata.csv"))
    certs, rows, y, emb, wear, cent, dh, views, info = load(paths, meta_path)
    Xm = measured_features(wear, cent)
    Xl = pooled(emb, views)
    ident = [D.card_identity(r) for r in rows]
    # near-identical pictures under a different catalogue name still count as the same printed card
    dup_key = [None] * len(certs)
    for gi, g in enumerate(D.near_duplicates(dh, max_bits=3)):
        for i in g:
            dup_key[i] = gi
    groups = D.union_groups(ident, dup_key)
    split = D.grouped_split(groups, y, seed=SEED)
    tr, va, te = (np.where(split == s)[0] for s in ("train", "val", "test"))
    assert not (set(np.array(groups)[tr]) & set(np.array(groups)[te])) and not (set(np.array(groups)[va]) & set(np.array(groups)[te]))
    rng = np.random.default_rng(SEED)
    report = {"data": {**info, "cards_used": len(y), "printed_cards": len(set(ident)), "split_groups": len(set(groups)),
                       "train": len(tr), "val": len(va), "test": len(te),
                       "grade_counts": {str(g): int((y == g).sum()) for g in sorted(set(y.tolist()), reverse=True)},
                       "languages": {k: int(v) for k, v in zip(*np.unique([D.language(r) for r in rows], return_counts=True))},
                       "measured_missing_share": float(np.isnan(Xm).mean())},
              "models": {}, "selection": {}}

    # choose regularisation (and PCA size) on validation only
    def pick(X, grid):
        best = None
        for C, pca in grid:
            m = Ordinal(C, pca).fit(X[tr], y[tr])
            s = metrics(m.proba(X[va]), m.grades, y[va])
            score = (s["mae"], -s["within_1"])
            if best is None or score < best[0]:
                best = (score, C, pca, s)
        return best
    grids = {"measured": [(c, None) for c in (0.01, 0.1, 1, 10)],
             "learned": [(c, p) for c in (0.003, 0.01, 0.03, 0.1) for p in (64, 128, 256)],
             "combined": [(c, p) for c in (0.003, 0.01, 0.03, 0.1) for p in (64, 128, 256)]}
    Xs = {"measured": Xm, "learned": Xl}
    final = {}
    for name in ("measured", "learned"):
        _, C, pca, s = pick(Xs[name], grids[name])
        report["selection"][name] = {"C": C, "pca": pca, "val_mae": s["mae"], "val_within_1": s["within_1"]}
        final[name] = Ordinal(C, pca).fit(Xs[name][tr], y[tr])
    # combined: the learned features reduced by the learned model's PCA, next to the raw measurements
    def comb(idx_fit):
        base = final["learned"]
        Zl = base._z(Xl)
        return np.concatenate([Zl / (Zl[idx_fit].std(0) + 1e-6), np.where(np.isnan(Xm), np.nanmean(Xm[idx_fit], 0), Xm)], 1)
    Xc = comb(tr)
    _, C, _, s = pick(Xc, [(c, None) for c in (0.003, 0.01, 0.03, 0.1, 0.3)])
    report["selection"]["combined"] = {"C": C, "val_mae": s["mae"], "val_within_1": s["within_1"]}
    final["combined"] = Ordinal(C, None).fit(Xc[tr], y[tr])
    Xs["combined"] = Xc

    # the shipped design (monotone weights), for an apples-to-apples baseline
    P1, g1 = measured_v1(Xm[tr], y[tr], Xm[te])
    report["models"]["shipped_design_measured_monotone"] = metrics(P1, g1, y[te], rng)
    for name, m in final.items():
        report["models"][name] = metrics(m.proba(Xs[name][te]), m.grades, y[te], rng)
    g = final["learned"].grades
    Pref = reference(y[tr], y[te], g)
    report["models"]["no_information_training_grade_mix"] = metrics(Pref, g, y[te])
    # What the catalogue alone predicts (language and print year, no picture): modern Japanese cards are mostly
    # 10s, old English ones are not. Anything a picture model claims must beat THIS, not just the grade mix.
    years = np.array([int(r["year"]) if r["year"].isdigit() else 0 for r in rows])
    langs = np.array([D.language(r) for r in rows])
    Xcat = np.column_stack([langs == "en", langs == "ja", years == 0] + [(years >= a) & (years < b) for a, b in ((1, 2003), (2003, 2011), (2011, 2017), (2017, 2020), (2020, 2023), (2023, 9999))]).astype(float)
    mc = Ordinal(1.0, None).fit(Xcat[tr], y[tr])
    report["models"]["catalogue_only_language_and_year"] = metrics(mc.proba(Xcat[te]), mc.grades, y[te], rng)
    # The picture's own contribution: the same catalogue facts plus the picture features. The gap between
    # this row and the catalogue-only row is what looking at the card adds.
    def with_catalogue(X, name, Cs=(0.003, 0.01, 0.03, 0.1, 0.3)):
        mean = np.nan_to_num(np.nanmean(X[tr], 0)); Xf = np.where(np.isnan(X), mean, X)
        Xj = np.concatenate([Xcat * 3.0, (Xf - Xf[tr].mean(0)) / (Xf[tr].std(0) + 1e-6)], 1)
        def val_mae(C):
            m = Ordinal(C, None).fit(Xj[tr], y[tr])
            return metrics(m.proba(Xj[va]), m.grades, y[va])["mae"]
        best = min(Cs, key=val_mae)
        mj = Ordinal(best, None).fit(Xj[tr], y[tr])
        report["models"][name] = metrics(mj.proba(Xj[te]), mj.grades, y[te], rng)
        return mj.proba(Xj[te]), mj.grades
    Pj, gj = with_catalogue(final["learned"]._z(Xl), "catalogue_plus_learned")
    with_catalogue(Xm, "catalogue_plus_measured")
    # Paired comparison on the same test cards: how much lower is the absolute error WITH the picture?
    floor = mc.grades.min(); yt_ = np.maximum(y[te], floor)
    err = lambda P, g: np.abs(g[np.argmax(P, 1)] - yt_)
    e_cat, e_both, e_pic = err(mc.proba(Xcat[te]), mc.grades), err(Pj, gj), err(final["learned"].proba(Xl[te]), final["learned"].grades)
    boot = [rng.integers(0, len(yt_), len(yt_)) for _ in range(2000)]
    def paired(a, b):
        d = np.sort([np.mean(a[i] - b[i]) for i in boot])
        return {"mean": float(np.mean(a - b)), "ci95": [float(d[50]), float(d[1949])]}
    report["picture_gain_in_mae"] = {"catalogue_plus_learned_vs_catalogue_only": paired(e_cat, e_both), "learned_vs_catalogue_only": paired(e_cat, e_pic)}
    # Does the picture separate 10s from the rest INSIDE one kind of card? (0.5 = no better than chance)
    era = np.where(years >= 2020, "2020+", np.where(years >= 2011, "2011-2019", "before 2011"))
    report["auc_grade_10_within_groups"] = {}
    Pl = final["learned"].proba(Xl[te]); s10 = Pl[:, final["learned"].grades >= 10].sum(1); sc = mc.proba(Xcat[te])[:, mc.grades >= 10].sum(1)
    for lg in ("en", "ja"):
        for e in ("2020+", "2011-2019", "before 2011"):
            mk = (langs[te] == lg) & (era[te] == e); t = y[te][mk] >= 10
            if mk.sum() >= 80 and 10 <= t.sum() <= mk.sum() - 10:
                report["auc_grade_10_within_groups"][f"{lg} {e}"] = {"n": int(mk.sum()), "share_10": float(t.mean()), "learned": float(roc_auc_score(t, s10[mk])), "catalogue_only": float(roc_auc_score(t, sc[mk]))}

    # which model, by validation
    winner = min(("measured", "learned", "combined"), key=lambda n: (report["selection"][n]["val_mae"], -report["selection"][n]["val_within_1"]))
    report["selection"]["winner_by_validation"] = winner

    # subgroups of the winner on the test part
    m = final[winner]; P = m.proba(Xs[winner][te]); yt = y[te]
    lang = np.array([D.language(rows[i]) for i in te]); year = np.array([int(rows[i]["year"]) if rows[i]["year"].isdigit() else 0 for i in te])
    report["subgroups_" + winner] = {}
    for label, mask in (("english", lang == "en"), ("japanese", lang == "ja"), ("printed_before_2010", (year > 0) & (year < 2010)), ("printed_2020_or_later", year >= 2020)):
        if mask.sum() >= 50:
            s = metrics(P[mask], m.grades, yt[mask])
            report["subgroups_" + winner][label] = {k: s[k] for k in ("n", "mae", "exact", "within_1", "range_75_coverage")}

    # second test: hold out whole runs of consecutive certificates (one submission, one scanner batch)
    block = np.array([int(c) // 50 for c in certs])
    blocks = np.unique(block); rng2 = np.random.default_rng(SEED + 1); rng2.shuffle(blocks)
    held = np.isin(block, blocks[: max(1, len(blocks) // 5)])
    held_ident = set(np.array(ident)[held])
    fit_idx = np.where(~held & ~np.isin(np.array(ident), list(held_ident)))[0]   # also drop the held-out artwork from training
    sel = report["selection"][winner]
    Xw = Xs[winner] if winner != "combined" else Xc
    mb = Ordinal(sel["C"], sel.get("pca")).fit(Xw[fit_idx], y[fit_idx])
    sb = metrics(mb.proba(Xw[held]), mb.grades, y[held])
    report["held_out_submission_blocks_" + winner] = {**{k: sb[k] for k in ("n", "mae", "exact", "within_1", "range_75_coverage", "auc_ge_10")}, "train": int(len(fit_idx))}

    report["seconds"] = round(time.time() - t0, 1)
    json.dump(report, open(os.path.join(out_dir, "evaluation.json"), "w"), indent=1)
    np.savez_compressed(os.path.join(out_dir, "split.npz"), certs=np.array(certs), split=split.astype(str), groups=np.array(groups))
    # the fitted heads, for export_onnx.py
    import pickle
    pickle.dump({"final": final, "views": views, "winner": winner, "comb_stats": None}, open(os.path.join(out_dir, "models.pkl"), "wb"))
    with open(os.path.join(out_dir, "manifest.jsonl"), "w", encoding="utf-8") as f:
        by_cert = dict(zip(certs, zip(split, groups)))
        for p in paths:
            for r in np.load(p, allow_pickle=True)["records"]:
                c = r["physical_card_id"][4:]
                if c in by_cert:
                    r = dict(r); r["split"], r["group"] = by_cert[c][0], int(by_cert[c][1])
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
    show = lambda s: f"MAE {s['mae']:.3f}  exact {s['exact']:.3f}  within-1 {s['within_1']:.3f}  AUC>=10 {s.get('auc_ge_10', float('nan')):.3f}  75%-range covers {s['range_75_coverage']:.3f} (width {s['range_75_mean_width']:.2f})"
    print(json.dumps(report["data"], indent=1))
    for name, s in report["models"].items():
        print(f"{name:<36} {show(s)}")
    print("picture gain (paired):", json.dumps(report["picture_gain_in_mae"]))
    print("AUC for grade 10 within one kind of card:", json.dumps(report["auc_grade_10_within_groups"]))
    print("winner by validation:", winner, report["selection"])
    print("subgroups:", json.dumps(report["subgroups_" + winner]))
    print("held-out submission blocks:", json.dumps(report["held_out_submission_blocks_" + winner]))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
