# Pre-grader: data, training, evaluation

Everything that fits or measures the pre-grader lives here. What it produced and what that means is in
[docs/PREGRADING-SYSTEM.md](../../docs/PREGRADING-SYSTEM.md). Pokémon and One Piece only.

## Files

| File | What it does |
| --- | --- |
| `sources.json` | Rights ledger: every data source, its licence, where its labels come from, and whether weights fitted on it may ship in a paid app. |
| `taxonomy.json` | The defect classes and how outside datasets' class names map onto them. |
| `pgdata.py` | Resumable downloads with back-off, safe reading of archives, image checks, near-duplicate hashes, manifest records, leak-free splits. |
| `extract_features.py` | Reads a shard of graded scans; writes the app's measurements, learned features of 28 close-ups per card and the manifest. |
| `train_grade_v2.py` | Fits and compares grade models on held-out cards; writes `evaluation.json`, `manifest.jsonl`, `split.npz`, `models.pkl`. |
| `export_onnx.py` | Exports the feature network (ONNX) and the fitted head (JSON) and checks both against Python. |
| `stress_test.py` | Same unseen cards, pictured worse: how far the model's answer moves. |
| `fetch_roboflow.py` | Downloads the defect datasets (needs `ROBOFLOW_API_KEY`) and converts them. |
| `train_defects.py` | Trains and scores the defect detector; `--selftest` runs the pipeline on generated pictures. |
| `registry.py` | Model versions: register, compare, promote (blocked for quarantined or phone-unvalidated models), roll back. |
| `center.py`, `wear.py`, `edge_only.py`, `measure_psa_scans.py`, `train_grade_model.py` | The first model (50 measurements → `GradeWeights.kt`). Kept: v2 reuses its measurements and scores it as the baseline. |
| `validate_phone_dataset.py` | Blind scoring of predictions on phone photos with verified grades (the test a model must pass before it may move the score in the app). |

## Reproduce the grade model

```sh
pip install -r requirements.txt        # torch: add --index-url https://download.pytorch.org/whl/cpu for CPU wheels
D=~/pg-data && mkdir -p $D
curl -L -o $D/jyesr_metadata.csv https://huggingface.co/datasets/jyesr/pokemon-tcg-grading/resolve/main/metadata.csv

# per shard (0..7, 2.1 GB each): download, extract features, delete the scans. About 45 min per shard on 2 CPU cores.
for n in 0 1 2; do
  python3 pgdata.py download jyesr $n --to $D/shards
  python3 extract_features.py $D/shards/data_shard_000$n.tar $D/jyesr_metadata.csv $D/feat_000$n.npz
  rm $D/shards/data_shard_000$n.tar
done

python3 train_grade_v2.py $D/run $D/feat_000*.npz          # prints the table, writes $D/run/evaluation.json
python3 export_onnx.py $D/run $D/feat_0000.npz             # ONNX + head, with an equality check
python3 stress_test.py $D/run PART_OF_AN_UNUSED_SHARD.tar $D/jyesr_metadata.csv
python3 registry.py register grade-v2-YYYYMMDD $D/run      # then: compare / promote / rollback
```

Seeds are fixed (`SEED = 7`); the same feature files give the same split and the same numbers.

## Train the defect detector (blocked until there is a key)

```sh
export ROBOFLOW_API_KEY=...            # free account at roboflow.com → Settings → API keys
python3 fetch_roboflow.py rf_defect  $D/defects
python3 fetch_roboflow.py rf_surface $D/defects
python3 train_defects.py $D/defects $D/defect_run --epochs 30 --size 640
```

`fetch_roboflow.py` prints the source classes it could not map: add them to `taxonomy.json` (or map them
to `null`) and run again. Without a GPU, expect many hours; with one, mixed precision is used automatically.

## Rules the code enforces

- **Splits are by group.** Grade models: by printed card (year, set, number, name) plus near-identical
  pictures, so the same artwork is never on both sides. Defect detector: by original photo, so augmented
  copies stay together. Both sides of one card are one row.
- **Choices are made on validation only.** The test part is scored once.
- **Labels keep their origin.** `verified_grade` is only a grade with a certificate; qualified PSA grades
  (OC, MC, …) and "Authentic" are excluded from the ordinary scale; an app estimate is `estimated_grade`
  and never becomes ground truth.
- **Rights travel with the data.** Each manifest record carries its source's licence and
  `commercial_use`; `registry.py promote` refuses a model fitted on a source that is not cleared.
- **No invented damage.** The only augmentation is a mirror flip.

## Adding verified phone photos (the data that is actually missing)

In the app: Pre-grade a card → Save → after the card comes back from grading, open the saved report, enter
the grade under "Grade received" → "Export training sample". Each zip holds the flattened front/back
photos, the report, the findings and `sample.json` in the manifest format above
(`label_provenance: grade_entered_by_user_from_returned_slab`). Collect the zips in one folder; they are the
`own` source in `sources.json`, the only one that is both in the right domain and cleared for commercial use.
