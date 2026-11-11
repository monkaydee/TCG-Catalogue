"""Exports the learned grade model for use outside Python and checks that the export gives the same numbers.

  closeup_features.onnx   the frozen feature network: [n, 3, 224, 224] RGB 0..255 floats -> [n, 720]
  grade_head.json         the fitted part as one linear map per grade threshold over the pooled features
                          (pooling as in train_grade_v2.pooled), plus the view order and the grade buckets

    python3 export_onnx.py RUN_DIR FEATURES.npz

RUN_DIR is the output of train_grade_v2.py. FEATURES.npz is only used for the equality check.
"""
import json, os, pickle, sys
import numpy as np
import torch
import extract_features as X
import train_grade_v2 as T


class Features(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.net = X._net

    def forward(self, x):
        x = (x / 255 - X._MEAN) / X._STD
        extra = []
        for i, layer in enumerate(self.net.features):
            x = layer(x)
            if i in X.TAPS:
                extra += [x.mean((2, 3)), x.amax((2, 3))]
        return torch.cat([x.mean((2, 3))] + extra, 1)


def affine(m):
    """Folds standardising, PCA and the logistic models into logits = W @ pooled + b (one row per threshold)."""
    A = np.diag(1 / m.scale)                               # z = (x - mean) / scale
    c = -m.mean / m.scale
    if m.pca is not None:
        A = m.pca.components_ @ A                          # p = components @ (z - pca.mean)
        c = m.pca.components_ @ (c - m.pca.mean_)
    W = np.stack([mm.coef_[0] @ A for mm in m.models])
    b = np.array([mm.coef_[0] @ c + mm.intercept_[0] for mm in m.models])
    return W, b


def main(run, feats):
    sys.modules["__main__"].Ordinal = T.Ordinal     # the models were pickled by train_grade_v2.py run as a script
    saved = pickle.load(open(os.path.join(run, "models.pkl"), "rb"))
    m = saved["final"]["learned"]
    W, b = affine(m)
    z = np.load(feats, allow_pickle=True)
    P = T.pooled(z["emb"][:200].astype(np.float32), list(z["views"]))
    Pf = np.where(np.isnan(P), m.mean, P)
    want = m.at_least(P)
    got = np.maximum.accumulate(1 / (1 + np.exp(-(Pf @ W.T + b))), 1)
    head_gap = float(np.abs(want - got).max())
    json.dump({"model": "learned", "views": saved["views"], "pooling": "per side: mean and max of the 4 corner views, mean and max of the 10 edge views, the whole-card view",
               "thresholds_grade_or_better": m.ks, "below_lowest_means": f"{m.ks[-1] - 1} or lower",
               "fill_missing_with": [round(float(v), 6) for v in m.mean],
               "weights": [[round(float(v), 7) for v in row] for row in W], "bias": [round(float(v), 7) for v in b]},
              open(os.path.join(run, "grade_head.json"), "w"))
    net = Features().eval()
    x = torch.randint(0, 256, (4, 3, 224, 224)).float()
    path = os.path.join(run, "closeup_features.onnx")
    torch.onnx.export(net, x, path, input_names=["images"], output_names=["features"], dynamic_axes={"images": {0: "n"}, "features": {0: "n"}}, opset_version=17, dynamo=False)
    import onnxruntime as ort
    out = ort.InferenceSession(path, providers=["CPUExecutionProvider"]).run(None, {"images": x.numpy()})[0]
    net_gap = float(np.abs(out - net(x).detach().numpy()).max())
    result = {"onnx_bytes": os.path.getsize(path), "head_bytes": os.path.getsize(os.path.join(run, "grade_head.json")),
              "max_abs_difference_features_onnx_vs_torch": net_gap, "max_abs_difference_probabilities_head_vs_sklearn": head_gap}
    assert net_gap < 1e-3 and head_gap < 1e-4, result
    json.dump(result, open(os.path.join(run, "export_check.json"), "w"), indent=1)
    print(json.dumps(result))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
