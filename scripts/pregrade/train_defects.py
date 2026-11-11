"""Trains and evaluates the defect detector on data fetched by fetch_roboflow.py.

Model: torchvision Faster R-CNN with a MobileNetV3 FPN backbone (BSD licence, no AGPL code). Uses CUDA with
mixed precision when a GPU is present, otherwise the CPU. Checkpoints after every epoch and resumes.

Split: by ORIGINAL photo (70/15/15), so augmented copies of one photo never sit on both sides. The model is
chosen on validation; the test part is scored once.

    python3 train_defects.py DATA_DIR OUT_DIR [--epochs 30] [--size 640] [--batch 4]
    python3 train_defects.py --selftest            # runs the whole pipeline on generated pictures (no real result)

DATA_DIR holds one folder per source, each with images/ and defects.json.
"""
import json, os, sys, time
import numpy as np
import torch, torchvision
from PIL import Image
from torchvision.models.detection import fasterrcnn_mobilenet_v3_large_fpn
from torchvision.models.detection.faster_rcnn import FastRCNNPredictor
import pgdata as D

SEED = 7


def load(data_dir):
    items, classes = [], None
    for source in sorted(os.listdir(data_dir)):
        f = os.path.join(data_dir, source, "defects.json")
        if not os.path.isfile(f):
            continue
        d = json.load(open(f))
        classes = classes or d["classes"]
        assert classes == d["classes"], "sources disagree on the class list"
        for im in d["images"]:
            items.append({**im, "path": os.path.join(data_dir, source, "images", im["file"]), "group": f"{source}/{im['original']}", "source": source})
    return items, classes


class Cards(torch.utils.data.Dataset):
    def __init__(self, items, size, train):
        self.items, self.size, self.train = items, size, train

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        it = self.items[i]
        im = Image.open(it["path"]).convert("RGB")
        k = self.size / max(im.size)
        im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.BILINEAR)
        boxes = np.array([[x * k, y * k, (x + w) * k, (y + h) * k] for _, x, y, w, h in it["boxes"]], np.float32).reshape(-1, 4)
        labels = np.array([c + 1 for c, *_ in it["boxes"]], np.int64)      # 0 is background
        x = torch.from_numpy(np.asarray(im, np.uint8).copy()).permute(2, 0, 1).float() / 255
        # only a mirror flip: no invented scratches or whitening
        if self.train and torch.rand(1).item() < 0.5:
            x = x.flip(-1)
            if len(boxes):
                boxes[:, [0, 2]] = im.width - boxes[:, [2, 0]]
        return x, {"boxes": torch.from_numpy(boxes), "labels": torch.from_numpy(labels)}


def build(n_classes, pretrained=True, size=640):
    # min_size/max_size: torchvision would otherwise enlarge every picture to 800 px
    m = fasterrcnn_mobilenet_v3_large_fpn(weights="DEFAULT" if pretrained else None, weights_backbone=None, min_size=size, max_size=size)
    m.roi_heads.box_predictor = FastRCNNPredictor(m.roi_heads.box_predictor.cls_score.in_features, n_classes + 1)
    return m


def iou(a, b):
    x0, y0 = np.maximum(a[0], b[:, 0]), np.maximum(a[1], b[:, 1])
    x1, y1 = np.minimum(a[2], b[:, 2]), np.minimum(a[3], b[:, 3])
    inter = np.clip(x1 - x0, 0, None) * np.clip(y1 - y0, 0, None)
    return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[:, 2] - b[:, 0]) * (b[:, 3] - b[:, 1]) - inter + 1e-9)


@torch.no_grad()
def evaluate(model, data, classes, device, thr=0.5, score=0.5):
    """Per class: AP at IoU 0.5 (all-point interpolation), and precision/recall/F1 at a 0.5 score."""
    model.eval()
    dets = {c: [] for c in range(1, len(classes) + 1)}; n_gt = {c: 0 for c in dets}; small = {"tp": 0, "gt": 0}
    for i in range(len(data)):
        x, t = data[i]
        out = model([x.to(device)])[0]
        pb, pl, ps = out["boxes"].cpu().numpy(), out["labels"].cpu().numpy(), out["scores"].cpu().numpy()
        gb, gl = t["boxes"].numpy(), t["labels"].numpy()
        for c in dets:
            g = gb[gl == c]; n_gt[c] += len(g); used = np.zeros(len(g), bool)
            for j in np.argsort(-ps):
                if pl[j] != c:
                    continue
                hit = False
                if len(g):
                    o = iou(pb[j], g); k = int(np.argmax(o))
                    if o[k] >= thr and not used[k]:
                        used[k] = hit = True
                dets[c].append((float(ps[j]), hit))
            area = (g[:, 2] - g[:, 0]) * (g[:, 3] - g[:, 1]) if len(g) else np.zeros(0)
            small["gt"] += int((area < 32 * 32).sum()); small["tp"] += int((used & (area < 32 * 32)).sum())
    out = {"per_class": {}, "images": len(data)}
    aps = []
    for c, d in dets.items():
        d.sort(key=lambda t: -t[0])
        tp = np.cumsum([h for _, h in d]); fp = np.cumsum([not h for _, h in d])
        rec = tp / max(1, n_gt[c]); prec = tp / np.maximum(1, tp + fp)
        ap = 0.0
        if n_gt[c] and len(d):
            mrec = np.concatenate([[0], rec, [1]]); mpre = np.concatenate([[0], prec, [0]])
            mpre = np.maximum.accumulate(mpre[::-1])[::-1]
            idx = np.where(mrec[1:] != mrec[:-1])[0]
            ap = float(np.sum((mrec[idx + 1] - mrec[idx]) * mpre[idx + 1]))
        at = [h for s, h in d if s >= score]
        p = float(np.mean(at)) if at else None
        r = float(np.sum(at) / n_gt[c]) if n_gt[c] else None
        out["per_class"][classes[c - 1]] = {"ground_truth": n_gt[c], "ap50": ap if n_gt[c] else None, "precision": p, "recall": r,
                                           "f1": (2 * p * r / (p + r) if p and r else None)}
        if n_gt[c]:
            aps.append(ap)
    out["map50"] = float(np.mean(aps)) if aps else None
    out["small_defect_recall"] = small["tp"] / small["gt"] if small["gt"] else None
    return out


def train(items, classes, out_dir, epochs, size, batch, pretrained=True):
    os.makedirs(out_dir, exist_ok=True)
    torch.manual_seed(SEED)
    main_class = [max(set(c for c, *_ in it["boxes"]), key=[c for c, *_ in it["boxes"]].count) if it["boxes"] else -1 for it in items]
    split = D.grouped_split([it["group"] for it in items], main_class, seed=SEED)
    parts = {s: [it for it, p in zip(items, split) if p == s] for s in ("train", "val", "test")}
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = build(len(classes), pretrained, size).to(device)
    opt = torch.optim.SGD([p for p in model.parameters() if p.requires_grad], lr=0.01, momentum=0.9, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, epochs)
    scaler = torch.amp.GradScaler(enabled=device.type == "cuda")
    ckpt = os.path.join(out_dir, "last.pt"); start, best, log = 0, -1.0, []
    if os.path.isfile(ckpt):
        s = torch.load(ckpt, map_location=device, weights_only=False)
        model.load_state_dict(s["model"]); opt.load_state_dict(s["opt"]); sched.load_state_dict(s["sched"]); start, best, log = s["epoch"], s["best"], s["log"]
    loader = torch.utils.data.DataLoader(Cards(parts["train"], size, True), batch_size=batch, shuffle=True, collate_fn=lambda b: tuple(zip(*b)), num_workers=min(4, os.cpu_count() or 1))
    val = Cards(parts["val"], size, False)
    t0 = time.time()
    for epoch in range(start, epochs):
        model.train(); total = 0.0
        for xs, ts in loader:
            xs = [x.to(device) for x in xs]; ts = [{k: v.to(device) for k, v in t.items()} for t in ts]
            with torch.autocast(device.type, enabled=device.type == "cuda"):
                loss = sum(model(xs, ts).values())
            opt.zero_grad(); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); total += loss.item()
        sched.step()
        v = evaluate(model, val, classes, device)
        log.append({"epoch": epoch + 1, "train_loss": total / max(1, len(loader)), "val_map50": v["map50"]})
        print(log[-1], flush=True)
        if (v["map50"] or 0) > best:
            best = v["map50"] or 0; torch.save(model.state_dict(), os.path.join(out_dir, "best.pt"))
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(), "epoch": epoch + 1, "best": best, "log": log}, ckpt)
    if os.path.isfile(os.path.join(out_dir, "best.pt")):
        model.load_state_dict(torch.load(os.path.join(out_dir, "best.pt"), map_location=device))
    result = {"device": str(device), "classes": classes, "images": {k: len(v) for k, v in parts.items()},
              "original_photos": len({it["group"] for it in items}), "boxes": sum(len(it["boxes"]) for it in items),
              "epochs": epochs, "size": size, "seconds": round(time.time() - t0, 1), "log": log,
              "validation": evaluate(model, val, classes, device), "test": evaluate(model, Cards(parts["test"], size, False), classes, device)}
    json.dump(result, open(os.path.join(out_dir, "defect_evaluation.json"), "w"), indent=1)
    return result


def selftest():
    """Generated pictures with drawn rectangles. Proves the code path runs end to end; says NOTHING about cards."""
    import tempfile
    from PIL import ImageDraw
    root = tempfile.mkdtemp(); src = os.path.join(root, "data", "generated"); os.makedirs(os.path.join(src, "images"))
    rng = np.random.default_rng(0); images = []
    for i in range(24):
        im = Image.new("RGB", (240, 320), (40, 60, 120)); d = ImageDraw.Draw(im); x, y = int(rng.integers(20, 160)), int(rng.integers(20, 240))
        d.rectangle([x, y, x + 50, y + 40], fill=(250, 250, 250))
        for copy in range(2):
            name = f"g{i}_jpg.rf.{copy:032x}.jpg"; im.save(os.path.join(src, "images", name))
            images.append({"file": name, "width": 240, "height": 320, "original": f"g{i}_jpg", "boxes": [[0, x, y, 50, 40]]})
    json.dump({"classes": ["corner_wear", "edge_wear"], "images": images}, open(os.path.join(src, "defects.json"), "w"))
    items, classes = load(os.path.join(root, "data"))
    r = train(items, classes, os.path.join(root, "out"), epochs=1, size=256, batch=4, pretrained=False)
    groups = {s: {it["group"] for it, p in zip(items, D.grouped_split([i["group"] for i in items], [0] * len(items), seed=SEED)) if p == s} for s in ("train", "val", "test")}
    assert not (groups["train"] & groups["test"]) and not (groups["train"] & groups["val"]), "copies of one photo leaked across the split"
    assert os.path.isfile(os.path.join(root, "out", "last.pt")) and r["test"]["images"] > 0
    print("selftest ok:", r["images"], "loss", round(r["log"][0]["train_loss"], 3))


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["--selftest"]:
        selftest()
    else:
        opt = lambda k, d: int(a[a.index(k) + 1]) if k in a else d
        items, classes = load(a[0])
        if not items:
            sys.exit(f"No defect data in {a[0]}. Run fetch_roboflow.py first (needs ROBOFLOW_API_KEY).")
        r = train(items, classes, a[1], opt("--epochs", 30), opt("--size", 640), opt("--batch", 4))
        print(json.dumps({"test": r["test"], "validation_map50": r["validation"]["map50"]}, indent=1))
