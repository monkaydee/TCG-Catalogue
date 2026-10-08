#!/usr/bin/env python3
"""Blind phone-photo evaluation. Range-only predictions and repeat captures are supported.

JSONL rows: certificate, split=train|test, grade=1..10, predicted=int|null,
low/high=int|null, game, language, device, certificateVerified=bool.
No certificate identifiers or photos are included in the public report.
"""
import collections
import json
import sys


def evaluate(rows):
    splits = collections.defaultdict(set)
    labels = {}
    for row in rows:
        certificate = row.get("certificate")
        if not isinstance(certificate, str) or not certificate.strip():
            raise ValueError("Physical card/certificate identity missing")
        if row.get("split") not in ("train", "test"):
            raise ValueError("Split must be train or test")
        if not isinstance(row.get("grade"), int) or isinstance(row["grade"], bool) or not 1 <= row["grade"] <= 10:
            raise ValueError("Grade outside 1–10")
        if certificate in labels and labels[certificate] != row["grade"]:
            raise ValueError("Conflicting professional labels for one physical card")
        labels[certificate] = row["grade"]
        splits[certificate].add(row["split"])
        prediction = row.get("predicted")
        if prediction is not None and (not isinstance(prediction, int) or isinstance(prediction, bool) or not 1 <= prediction <= 10):
            raise ValueError("Prediction outside 1–10")
        low, high = row.get("low"), row.get("high")
        if (low is None) != (high is None):
            raise ValueError("Both range endpoints are required")
        if low is not None and (not isinstance(low, int) or not isinstance(high, int) or not 1 <= low <= high <= 10):
            raise ValueError("Invalid estimated range")
        if any(not row.get(key) for key in ("game", "language", "device")):
            raise ValueError("Game, language and device strata are required")
    if any(len(value) > 1 for value in splits.values()):
        raise ValueError("Physical certificate leaks between train/test splits")
    test = [row for row in rows if row["split"] == "test"]
    if not test:
        raise ValueError("No blind test cards supplied")

    def metrics(captures):
        counts = collections.Counter(row["certificate"] for row in captures)
        weight = lambda row: 1 / counts[row["certificate"]]
        total = len(counts)
        point = [row for row in captures if row.get("predicted") is not None]
        ranged = [row for row in captures if row.get("low") is not None]
        def fraction(group, predicate):
            denominator = sum(weight(row) for row in group)
            return sum(weight(row) for row in group if predicate(row)) / denominator if denominator else None
        assessed = sum(weight(row) for row in point)
        matrix = collections.defaultdict(float)
        for row in point:
            matrix[f'{row["grade"]}->{row["predicted"]}'] += weight(row)
        return {"physical_test_cards": total, "capture_count": len(captures),
            "coverage": assessed / total,
            "range_prediction_coverage": sum(weight(row) for row in ranged) / total,
            "exact_accuracy": fraction(point, lambda row: row["grade"] == row["predicted"]),
            "within_one": fraction(point, lambda row: abs(row["grade"] - row["predicted"]) <= 1),
            "mean_absolute_error": sum(weight(row) * abs(row["grade"] - row["predicted"]) for row in point) / assessed if assessed else None,
            "false_tens": sum(weight(row) for row in point if row["predicted"] == 10 and row["grade"] != 10),
            "confusion": dict(matrix),
            "range_coverage": fraction(ranged, lambda row: row["low"] <= row["grade"] <= row["high"]),
            "mean_range_width": sum(weight(row)*(row["high"]-row["low"]) for row in ranged)/sum(weight(row) for row in ranged) if ranged else None,
            "verified_label_fraction": fraction(captures, lambda row: row.get("certificateVerified") is True)}
    report = metrics(test)
    report["strata"] = {dimension: {value: metrics([row for row in test if row[dimension] == value])
        for value in sorted({row[dimension] for row in test})} for dimension in ("game", "language", "device")}
    report["validated_for_release"] = False
    return report


if __name__ == "__main__":
    with open(sys.argv[1]) as stream:
        rows = [json.loads(line) for line in stream if line.strip()]
    print(json.dumps(evaluate(rows), indent=2))
