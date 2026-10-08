# Pre-grader data sources (provenance)

What the pre-grader was calibrated or tested on. Nothing from these datasets is shipped in the app:
only the fitted numbers in `GradeWeights.kt` (50 feature weights per grade) end up in the APK.

| Source | What | Used for | License / terms | Shipped? |
|---|---|---|---|---|
| [jyesr/pokemon-tcg-grading](https://huggingface.co/datasets/jyesr/pokemon-tcg-grading) (Hugging Face) | about 52,000 PSA cert scans (front + back) of Pokémon cards with their PSA grade (`metadata.csv`: grade, card, cert id) | fitting the grade model and the corner/edge wear levels; checking centering against PSA 10s | no license given on the dataset card; images are PSA's cert scans. Used for research and calibration only. Before redistributing anything derived beyond model weights, clear it with the dataset author and PSA. | no (weights only) |
| Own photos (repo `monkaydee/pre-grading`: Luffy & Ace, 16320×9180) | real phone photos of cards on a mat | testing card finding and straightening (`CardRectifier`) on real photos | the user's own photos | no |

## Reproducing

```sh
# 1. download shards of the dataset and resize to max 1400 px (images <cert>_front/back.jpg)
# 2. measure centering and wear
python3 measure_psa_scans.py img/ metadata.csv measurements.json
# 3. fit and write app/src/main/java/com/monkaydee/tcgcatalogue/grade/GradeWeights.kt
python3 train_grade_model.py measurements.json
```

`center.py`, `wear.py` and `edge_only.py` are the Python versions of `Centering.kt` and `Wear.kt`;
the Kotlin code was checked against them on the same cards.

## Known limits

- PSA scans are flat, evenly lit and cut close to the card; phone photos are not. The app
  straightens the photo first, but glare, blur and shadows still shift the measurements, so the app
  shows a likely range, not a single grade.
- The model only sees centering and corner/edge wear. Surface scratches, dents, print lines and
  creases (a large part of why cards miss a 10) are not measured yet ("Premium" pre-grading).
- Pokémon only so far; other games have different borders and backs.
