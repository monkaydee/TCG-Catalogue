"""Downloads a Roboflow Universe dataset version as COCO and converts it to the pre-grader's defect format.
Needs ROBOFLOW_API_KEY (a free Roboflow account). Never run without one: the public pages are not scraped.

    ROBOFLOW_API_KEY=... python3 fetch_roboflow.py rf_defect OUT_DIR
    ROBOFLOW_API_KEY=... python3 fetch_roboflow.py rf_surface OUT_DIR

Writes OUT_DIR/<source>/images/ and OUT_DIR/<source>/defects.json:
  {"classes": [...], "images": [{"file", "width", "height", "original", "boxes": [[class_id, x, y, w, h], ...]}]}
"original" is the name of the photo an augmented copy was made from; train_defects.py splits by it, so
copies of one photo never land on both sides of a split.
"""
import io, json, os, re, sys, urllib.request, zipfile
import pgdata as D

HERE = os.path.dirname(os.path.abspath(__file__))
TAX = json.load(open(os.path.join(HERE, "taxonomy.json"), encoding="utf-8"))
WHERE = {"rf_defect": ("hansraj-kumar", "pokemon-card-defect-detection", 6),
         "rf_surface": ("one-piece-tcg", "pokemon-surface-damage-detection-gfhuu", 1),
         "rf_corners": ("one-piece-tcg", "card-corner-a2c7o", 6)}


def original_name(file_name):
    """Roboflow names copies '<original>_jpg.rf.<hash>.jpg'."""
    return re.sub(r"\.rf\.[0-9a-f]+\.(jpg|jpeg|png)$", "", os.path.basename(file_name), flags=re.I)


def convert(coco, source):
    """COCO dict -> our format, classes renamed by taxonomy.json. Unknown class names are reported, not guessed."""
    names = {c["id"]: c["name"].strip().lower() for c in coco["categories"]}
    mapping = TAX["maps"].get(source, {})
    ids = {c["name"]: c["id"] for c in TAX["classes"]}
    unknown = sorted({n for n in names.values() if n not in mapping} - {coco.get("info", {}).get("description", "")})
    by_image = {}
    for a in coco.get("annotations", []):
        target = mapping.get(names.get(a["category_id"], ""))
        if target is None or a.get("iscrowd"):
            continue
        x, y, w, h = a["bbox"]
        if w > 1 and h > 1:
            by_image.setdefault(a["image_id"], []).append([ids[target], float(x), float(y), float(w), float(h)])
    images = [{"file": i["file_name"], "width": i["width"], "height": i["height"], "original": original_name(i["file_name"]),
               "boxes": by_image.get(i["id"], [])} for i in coco["images"]]
    return {"classes": [c["name"] for c in TAX["classes"]], "images": images, "unmapped_source_classes": unknown}


def main(source, out):
    key = os.environ.get("ROBOFLOW_API_KEY")
    if not key:
        sys.exit("ROBOFLOW_API_KEY is not set. Create a free key at roboflow.com (Settings -> API keys); see README.md.")
    ws, project, version = WHERE[source]
    info = json.load(urllib.request.urlopen(f"https://api.roboflow.com/{ws}/{project}/{version}/coco?api_key={key}", timeout=60))
    link = info["export"]["link"]
    dest = os.path.join(out, source)
    archive = D.download(link, os.path.join(dest, "export.zip"))
    merged = {"classes": [c["name"] for c in TAX["classes"]], "images": [], "unmapped_source_classes": []}
    os.makedirs(os.path.join(dest, "images"), exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for part in ("train", "valid", "test"):          # Roboflow's own split is ignored: we split by original photo
            name = f"{part}/_annotations.coco.json"
            if name not in z.namelist():
                continue
            c = convert(json.load(z.open(name)), source)
            merged["unmapped_source_classes"] = sorted(set(merged["unmapped_source_classes"]) | set(c["unmapped_source_classes"]))
            for im in c["images"]:
                data = z.read(f"{part}/{im['file']}")
                if D.decode(data, min_side=64) is None:   # corrupt or tiny
                    continue
                safe = os.path.basename(im["file"])
                open(os.path.join(dest, "images", safe), "wb").write(data)
                im["file"] = safe
                merged["images"].append(im)
    json.dump(merged, open(os.path.join(dest, "defects.json"), "w"))
    print(f"{source}: {len(merged['images'])} images, {len({i['original'] for i in merged['images']})} original photos, "
          f"{sum(len(i['boxes']) for i in merged['images'])} boxes; unmapped classes: {merged['unmapped_source_classes']}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
