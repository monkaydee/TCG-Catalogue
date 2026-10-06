#!/usr/bin/env python3
"""Evaluate held-out phone-photo predictions from a JSONL manifest, grouped by certificate.

Rows: {certificate, split:train|test, grade:1..10, predicted:int|null, low:int|null,
       high:int|null, game, language, device}. Test cards must never overlap train cards.
This evaluator does not manufacture labels or declare unvalidated thresholds acceptable.
"""
import collections
import json
import sys

def evaluate(rows):
    seen=collections.defaultdict(set)
    for row in rows:
        seen[row["certificate"]].add(row["split"])
        if not 1 <= row["grade"] <= 10:
            raise ValueError("Grade outside 1–10")
    if any(len(s)>1 for s in seen.values()):
        raise ValueError("Physical certificate leaks between train/test splits")
    test=[r for r in rows if r["split"]=="test"]
    if not test:
        raise ValueError("No blind test cards supplied")
    # Repeated captures must not inflate the apparent number of independent cards.
    certificates=collections.Counter(r["certificate"] for r in test)
    weight=lambda r:1/certificates[r["certificate"]]
    usable=[r for r in test if r.get("predicted") is not None]
    total=sum(weight(r) for r in test); assessed=sum(weight(r) for r in usable)
    ratio=lambda predicate:sum(weight(r) for r in usable if predicate(r))/assessed if assessed else None
    matrix=collections.defaultdict(float)
    for r in usable:matrix[f'{r["grade"]}->{r["predicted"]}']+=weight(r)
    return {"physical_test_cards":len(certificates),"capture_count":len(test),"coverage":assessed/total,
        "exact_accuracy":ratio(lambda r:r["grade"]==r["predicted"]),
        "within_one":ratio(lambda r:abs(r["grade"]-r["predicted"])<=1),
        "mean_absolute_error":sum(weight(r)*abs(r["grade"]-r["predicted"]) for r in usable)/assessed if assessed else None,
        "false_tens":sum(weight(r) for r in usable if r["predicted"]==10 and r["grade"]!=10),
        "confusion":dict(matrix),"range_coverage":ratio(lambda r:r.get("low") is not None and r["low"]<=r["grade"]<=r["high"]),
        "validated_for_release":False}

if __name__=="__main__":
    rows=[json.loads(line) for line in open(sys.argv[1]) if line.strip()]
    print(json.dumps(evaluate(rows),indent=2))
