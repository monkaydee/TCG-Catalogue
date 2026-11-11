"""Model versions for the pre-grader: register an evaluated model, compare it with the deployed one,
promote it only if it is better on the agreed measure, and roll back.

    python3 registry.py register NAME OUT_DIR          # OUT_DIR holds evaluation.json (train_grade_v2.py)
    python3 registry.py compare NAME                    # against the deployed version
    python3 registry.py promote NAME [--allow-quarantined]
    python3 registry.py rollback
    python3 registry.py list

The registry (models/registry.json) is committed; the model files themselves are not. A version trained on a
source whose commercial use is not 'yes' in sources.json is marked quarantined and cannot be promoted
without --allow-quarantined, which is meant for private research builds only.
"""
import json, os, sys, time
import pgdata as D

HERE = os.path.dirname(os.path.abspath(__file__))
PATH = os.path.join(HERE, "models", "registry.json")
MEASURE, MARGIN = "mae", 0.01          # lower is better; a new model must win by more than the margin


def load():
    return json.load(open(PATH)) if os.path.isfile(PATH) else {"deployed": None, "previous": None, "versions": {}}


def save(r):
    os.makedirs(os.path.dirname(PATH), exist_ok=True)
    json.dump(r, open(PATH, "w"), indent=1)


def register(name, out_dir, sources=("jyesr",)):
    ev = json.load(open(os.path.join(out_dir, "evaluation.json")))
    winner = ev["selection"]["winner_by_validation"]
    r = load()
    assert name not in r["versions"], "a version is never overwritten: choose a new name"
    r["versions"][name] = {
        "registered": time.strftime("%Y-%m-%d"), "kind": "grade", "model": winner, "sources": list(sources),
        "quarantined": not all(D.usable_for(s, "commercial") for s in sources),
        "domain": "PSA scans of slabbed cards", "validated_on_phone_photos": False,
        "cards": ev["data"]["cards_used"], "test": {k: ev["models"][winner][k] for k in ("n", "mae", "exact", "within_1", "range_75_coverage")},
        "validation": {"mae": ev["selection"][winner]["val_mae"], "within_1": ev["selection"][winner]["val_within_1"]},
    }
    save(r)
    return r["versions"][name]


def better(new, old):
    return old is None or new["validation"][MEASURE] < old["validation"][MEASURE] - MARGIN


def main(a):
    r = load()
    if a[:1] == ["register"]:
        print(json.dumps(register(a[1], a[2]), indent=1))
    elif a[:1] == ["list"]:
        for n, v in r["versions"].items():
            print(("* " if n == r["deployed"] else "  ") + n, v["model"], "val MAE", round(v["validation"]["mae"], 3), "quarantined" if v["quarantined"] else "")
    elif a[:1] == ["compare"]:
        old = r["versions"].get(r["deployed"]) if r["deployed"] else None
        print("better than deployed:", better(r["versions"][a[1]], old))
    elif a[:1] == ["promote"]:
        v = r["versions"][a[1]]
        old = r["versions"].get(r["deployed"]) if r["deployed"] else None
        if v["quarantined"] and "--allow-quarantined" not in a:
            sys.exit("Not promoted: trained on a source that is not cleared for commercial use (sources.json).")
        if not v["validated_on_phone_photos"] and "--allow-quarantined" not in a:
            sys.exit("Not promoted: not validated on phone photos.")
        if not better(v, old):
            sys.exit("Not promoted: not better than the deployed version on validation.")
        r["previous"], r["deployed"] = r["deployed"], a[1]
        save(r); print("deployed:", a[1])
    elif a[:1] == ["rollback"]:
        if not r["previous"]:
            sys.exit("Nothing to roll back to.")
        r["deployed"], r["previous"] = r["previous"], None
        save(r); print("deployed:", r["deployed"])
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
