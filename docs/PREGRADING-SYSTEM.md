# Pre-grading system: technical report

11 November 2026. Scope: Pokémon and One Piece. This report covers the work done on that day: what was
inspected, which data could be obtained, what was built, what was trained and how it scored, and what is
still open. Measured numbers are in [PREGRADING-RESULTS.md](PREGRADING-RESULTS.md), generated from the run.
Commands are in [scripts/pregrade/README.md](../scripts/pregrade/README.md). The earlier workflow notes
remain in [PREGRADING-VALIDATION.md](PREGRADING-VALIDATION.md).

## Summary

- **Trained for real:** a grade model on 6,388 PSA-graded Pokémon cards (12,777 scans), scored on 955 held-out
  cards whose artwork the model never saw. Feature extraction took 97 minutes on 2 CPU cores.
- **What it showed:** the model is right within one grade for 92 % of test cards, but so is a "model" that
  only knows the card's language and print year (90 %). Card by card, looking at the pictures lowers the
  error by 0.05 grades. On these scans a 10 can barely be told from a 9. **This model does not grade
  condition and is not used in the app.**
- **Not trained:** the defect detector (its datasets need an API key that was not available) and anything
  for One Piece (no graded One Piece data exists publicly). The code for the detector is written and its
  pipeline test passes.
- **In the app now:** a findings report with three honest states (confirmed, possible, not assessable),
  markers on the card photo for every finding, a machine-readable JSON report, a request for the extra
  photos that are missing, and a way to record the grade a card later received and export it as a verified
  training sample. Pre-grading is limited to Pokémon and One Piece.
- **The score is still centering only**, labelled experimental. Nothing trained here earned the right to change it.
- **What unlocks a real grade model:** phone photos of cards with the grade they then received. The app
  now collects exactly that.

## 1. Inventory (what existed before)

| Area | State before this work |
| --- | --- |
| App | Android, Kotlin, Compose; no backend for pre-grading. All grading code runs on the phone (`grade/`, 1,649 lines) and photos never leave it. |
| Card finding and straightening | `CardRectifier`, `AutoAlignment`, `StraightEdges`: own edge detection, four draggable corners, perspective correction. Working, tested. |
| Photo quality | `PhotoCheck`, `CaptureQuality`: blur, glare, size; spirit level, motion, best-of-three. Working. |
| Centering | `Centering`, `CenteringPotential`, `CenteringRotation`: measured ratios, manual guides, PSA's published 10 limits (55/45 front, 75/25 back). Working. |
| Corners and edges | `Wear`: colour comparison of the outer 0.6 mm against the border, per corner and edge, with flagged pixels; `ContourCheck`: corner outline asymmetry. Screening only. |
| Surface | No detector. Optional angled-light photos reviewed by the user. |
| Grade model | `GradeModel` + `GradeWeights`: 50 measurements → PSA grade chances, fitted on PSA scans. **Switched off** in the product (`researchOnly`) because it was never validated on phone photos. |
| Score shown | `AutomaticPreGrade`: centering only, labelled experimental. |
| Reports | `PreGradeReportStore`: saved report with photos, CSV export. No findings list, no machine-readable report. |
| Card identification | Separate, complete scanner (`scan/`): OCR, picture search, catalogue for both games. Reused as is. |
| Training code | `scripts/pregrade/`: measurement twins in Python and the fit of `GradeWeights`. No dataset tooling, no rights ledger, no held-out protocol beyond a random split. |
| Hardware for this work | 2 CPU cores, 7.8 GB RAM, no GPU, 26 GB free disk. |

Missing before: a rights ledger, leak-free evaluation, any learned picture model, defect localisation with
states, a structured report, a way to collect verified phone samples, model versioning.

## 2. Data: what could be obtained

The full ledger with licences and reasons is `scripts/pregrade/sources.json` (`python3 pgdata.py sources`).

| Source | Outcome |
| --- | --- |
| jyesr/pokemon-tcg-grading (Hugging Face) | **Used for research.** PSA scans with certificate ids and PSA grade names. No licence on the dataset; the pictures are PSA's. Quarantined from commercial shipping. |
| pacoalberola/Poke-Grader-Dataset-Images-PSA | **Not used.** Labels inspected: marketplace photos of slabs at 20–70 kB, and PSA rows carry subgrades such as 9.4, which PSA does not issue, so the subgrades are synthetic. |
| lding101/pokemon-card-grades | **Blocked.** Gated; needs the owner's Hugging Face account to accept the conditions. Not bypassed. |
| Roboflow defect datasets (2) and corner keypoints (1) | **Blocked.** Exports need a Roboflow API key; the pages refuse this machine (HTTP 403). The download and training code is ready and its pipeline test passes. |
| vegapull-records (One Piece artwork) | **Not used.** Clean artwork says nothing about condition; the app already identifies One Piece cards. |
| PSA / CGC / TAG certificate pages | **Not used.** No scraping; bulk use needs an agreement. |
| One Piece physical cards with grades or defects | **Nothing usable found** on Hugging Face or the web (one Roboflow project of that name is empty). |
| Own verified phone photos | **Empty today.** The app can now produce them (section 5). |

Consequences, stated plainly:

- **No defect detector was trained.** There was no annotated defect data to train it on.
- **No One Piece model exists.** Zero graded One Piece cards.
- **Grade labels are PSA cert records as listed in the dataset**, not re-checked against PSA by us.
- Everything fitted here is **scan-domain and research-only**.

## 3. What was built

### Data and training tooling (`scripts/pregrade/`)

- `pgdata.py`: resumable downloads with exponential back-off; archives are read member by member without
  extraction (links, paths and oversized members are skipped); images are fully decoded before use;
  64-bit difference hashes for near-duplicates; manifest records in the agreed schema with licence,
  provenance and split; grouped, grade-spread 70/15/15 splits.
- `sources.json`, `taxonomy.json`: rights ledger and defect classes with class maps for outside datasets.
- `extract_features.py`: per card the app's own measurements plus learned features of 28 close-ups and the
  whole card on both sides. Scans are read in place and deleted afterwards; only features and the manifest stay.
- `train_grade_v2.py`: fits three picture models and two references, chooses on validation, scores once on test:
  MAE, exact, within one, per-grade recall, confusion matrix, AUC, calibration, the app's 75 % range, bootstrap
  intervals, subgroups, and a second test with whole submissions held out.
- `export_onnx.py`: ONNX feature network and JSON head with equality checks. `stress_test.py`: robustness.
- `fetch_roboflow.py`, `train_defects.py`: defect data conversion and a Faster R-CNN (MobileNetV3-FPN,
  torchvision, BSD) trainer with CUDA/mixed precision when available, checkpoints, resume, AP50 and
  per-class precision/recall/F1, small-defect recall. `--selftest` passes (generated pictures; proves the
  code path, says nothing about cards).
- `registry.py`: versions, comparison, promotion, rollback. Promotion is refused for a model fitted on an
  uncleared source or not validated on phone photos.
- `make_report.py`: writes the results document from the run's output.

No AGPL code is used. Python dependencies: NumPy, SciPy, scikit-learn, Pillow, PyTorch, torchvision, ONNX, ONNX Runtime.

### App (`app/`)

- **Structured report** (`grade/Evidence.kt`): one machine-readable report per pre-grade in the agreed JSON
  format (section 6), with explicit nulls and an English explanation. Export as JSON from the result page.
- **Three finding states.** Confirmed = the user looked and recorded damage. Possible = an automatic check
  flagged it. Not assessable = these photos cannot show it (white border, foil at the rim, bad photo, flat
  photo for the surface). "Nothing flagged" is worded as such, never as "flawless".
- **Markers on the card** (`ui/screens/PreGradeFindings.kt`): every finding with a location gets a box on
  the flattened photo: red solid = confirmed, amber dashed = possible, grey = not assessable; front/back
  switch; markers can be hidden. A possible finding's box is the tight box around the pixels the
  measurement flagged. No marker exists without a stored finding.
- **Additional photos requested** in the report when the surface was not reviewed under angled light, a
  photo failed its checks, or a corner could not be measured.
- **Grade received** (`VerifiedGrade`, `VerifiedGradeSection`): on a saved report the user enters grader,
  grade and certificate number from the returned slab. **Export training sample** writes a zip with the
  flattened photos, report, findings and `sample.json` in the training manifest format. An estimate alone
  cannot be exported as a sample.
- **Games:** the pre-grade game selector and the card page's pre-grade button offer Pokémon and One Piece only.
- Findings are stored with saved reports and shown again when a report is reopened.
- 21 new strings in all 21 languages.

The score itself is unchanged: centering only, labelled experimental. Section 4 explains why.

## 4. What the trained models show

All numbers: [PREGRADING-RESULTS.md](PREGRADING-RESULTS.md). The run: 6,388 cards from 2 of the 8 shards,
split by printed card into 4,474 / 959 / 955.

| Model | MAE (grades) | Within one grade | AUC "is it a 10?" |
| --- | --- | --- | --- |
| No information | 0.757 | 82.9 % | 0.500 |
| Catalogue only (language, print year; no picture) | 0.554 | 90.3 % | 0.774 |
| The app's 50 measurements, first model's design | 0.639 | 87.5 % | 0.716 |
| Learned close-up features | 0.520 | 92.1 % | 0.794 |
| Catalogue + learned features | 0.508 | 92.4 % | 0.799 |

What follows from this:

1. **Most of the apparent accuracy is the grade mix.** 56 % of these cards are 10s and 82.5 % are 9 or 10.
   Among the test cards printed since 2020, 79 % of the Japanese ones are 10s and 44 % of the English ones.
   A model learns that first.
2. **The picture adds little.** On the same test cards, adding the picture to the catalogue facts lowers the
   error by 0.046 grades (95 % interval 0.008 to 0.085). Inside one kind of card, the picture separates 10s from
   the rest with AUC 0.66 (modern English) and 0.60 (modern Japanese), where 0.5 is chance.
3. **The first model's measurements do worse than the catalogue alone** on these scans (0.61–0.64 against 0.55).
   Switching that model off in the product was the right call.
4. **Lower grades are not recognised.** Recall for PSA 8 is 17 %, for PSA 7 17 %, for PSA 6 8 %.
5. **Its stated certainty cannot be relied on.** The "75 % range" holds the true grade for 83 % of all test
   cards but only 71 % of those printed before 2010, and where the model says "94 % chance of a 10" the
   actual share is 84 %.
6. **Batch effects are likely.** With whole submissions held out (and fewer cards to train on), MAE rises
   from 0.526 to 0.600.
7. **It is not stable under worse pictures.** A mild degradation of the same scan changes the most likely
   grade for 20 % of cards.

Why: the scans show a card inside its slab at about 18 px/mm. What separates a 10 from a 9 (a faint print
line, a hairline scratch, a touch of whitening) is at or below that resolution or needs angled light. A PSA
grade is also not a function of two flat pictures. No head on top of these pictures can recover what is
not in them. Other heads (boosted trees, a small neural network) and other poolings were tried on the
first shard's validation part and were not better.

Decision: `grade-v2-20261111` is registered in `scripts/pregrade/models/registry.json` as **quarantined and
not validated on phone photos**; `registry.py promote` refuses it. The ONNX export exists and matches Python,
but is not bundled in the app.

## 5. The path to a real grade model

What is missing is data in the right domain with trustworthy labels. The app now collects it:

1. Pre-grade a card before sending it in and **save** the report.
2. When it returns, open the report, enter the **grade received**, and **export the training sample**.
3. Collect the zips. They are the `own` source: phone photos, verified grade, cleared for commercial use.

About 300 cards per game across grades give a first honest phone-photo evaluation with
`validate_phone_dataset.py`; a model that moves the score needs more, and needs to beat the catalogue-only
reference on them. For defects, the two annotated datasets become available with a free Roboflow key.

## 6. Report format

`Evidence.json(report)` writes this structure (values illustrative, long texts shortened):

```json
{
  "schema_version": 1,
  "game": "pokemon",
  "card": { "name": "Test card", "set": null, "number": null },
  "analysis_status": "completed_with_limitations",
  "quality_checks": {
    "front_usable": true, "back_usable": true, "problems": [],
    "additional_images_required": ["tilted_front_surface", "tilted_back_surface"]
  },
  "centering": { "front_horizontal": "54/46", "front_vertical": "51/49", "back_horizontal": "54/46", "back_vertical": "51/49" },
  "defects": [
    {
      "id": "defect_001", "type": "corner_whitening", "side": "front", "location": "top_right",
      "severity": null, "finding_status": "possible", "confidence": null,
      "bounding_box": { "x": 0.89, "y": 0.0, "width": 0.11, "height": 0.07 },
      "evidence": "4.0% of the rim pixels differ from the border colour, 3.0% are brighter."
    }
  ],
  "component_assessments": { "centering": "within_psa_10_allowance", "corners": "possible_damage", "edges": "no_anomaly_flagged", "surface": "not_fully_assessable" },
  "pre_grade": {
    "standard": "PSA-style", "estimated_grade": 10, "estimated_range": [9, 10],
    "calibration_status": "not_yet_validated",
    "limitations": ["The score uses centering only. …", "Surface visibility is incomplete.", "Not a grade from PSA, BGS, CGC or TAG. …"]
  },
  "method": "measurements-v2 (centering, rim colour comparison, corner contour); no learned defect detector",
  "explanation": ["Front centering measures 54/46 left/right and 51/49 top/bottom.", "Possible corner whitening on the front, top right: …"]
}
```

- `finding_status`: `confirmed`, `possible`, `not_assessable`.
- `severity` and `confidence` stay `null` until a scale or a detector has been calibrated on phone photos.
- `bounding_box`: fractions of the flattened card from its top-left corner.
- `analysis_status`: `completed_with_limitations`, `needs_better_images`, `incomplete`, `unsupported_game`.

## 7. Tests

- Android: 268 unit and screenshot tests, 0 failures, 3 existing skips; `assembleRelease` and `lintRelease` pass with 0 errors.
  New: `EvidenceTest` (8: states, boxes, nulls, JSON round trip, games, verified grade rules, sample export),
  `PreGradeFindingsTest` (2: markers and list for all three states, grade entry validation), screenshot `pregrade_findings`.
- Python: `train_defects.py --selftest` passes (pipeline, grouped split, checkpoint). `export_onnx.py` asserts
  equality of the export. `train_grade_v2.py` asserts that no group is shared between train/validation and test.

## 8. Open points and blockers

| Item | Who | What is needed |
| --- | --- | --- |
| Defect detector | Owner | A free Roboflow API key; then `fetch_roboflow.py` and `train_defects.py` (a GPU is strongly advised). Licence of the pictures to be confirmed before shipping weights. |
| Gated dataset `lding101` | Owner | Accept its conditions on Hugging Face, provide `HF_TOKEN`. |
| Commercial use of the PSA scans | Owner | Ask the dataset author and PSA, or do not ship weights fitted on them. |
| Phone photos with verified grades | Owner and users | Collect with the app (section 5). The only route to a validated grade and to any One Piece calibration. |
| Surface (scratches, dents, print lines) | Open | Needs angled-light photos with annotations; no public data found. |
| Rest of the scans | Optional | 6 more shards (about 19,600 cards) can be added with the same commands; the conclusion in section 4 does not depend on it. |
| On-device model | Deferred | The ONNX export exists; integrating a runtime is pointless until a model has earned its place. |
