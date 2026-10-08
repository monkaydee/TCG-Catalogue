# Pre-grading evidence and validation

## Current user workflow

1. Capture/import front and back. The uncropped working photo opens with four draggable corners
   before the card is cropped. Accept the detected physical outline or correct it; when detection
   fails, a suggested rectangle is only a starting point for manual placement, never grade evidence.
   Perspective correction uses the confirmed corners exactly, without snapping to nearby artwork
   or automatically trimming the card again. Crossed, overlapping and out-of-image corners are rejected.
2. Photo-quality checks and centering measurement run automatically after confirming the outline.
   Clear measurable photos advance to the next side and reveal. The original working photo remains
   available through corner-adjustment actions in photo review and the live result. Centering guides
   remain optional; region/surface observation checklists stay removed. Failed photos require a retake
   or outline correction, and missing centering requires guide adjustment or retake. Skipping the back
   produces an explicitly front-only estimate capped at 9. Working photos retain the full field of view
   at up to 2,400 pixels on the longest side, preserving original source scale for quality checks.
3. No validated surface-grading model is available. As requested, the fallback uses centering only:
   corners, edges, surface and authenticity do not determine the score. Automatic corner/edge
   screening continues without manual input, but does not certify physical damage or a clean surface.
4. The reveal displays the actual rectified photo in a PSA-style **pre-grade display** with a
   1.6-second entrance, a slightly shaking paper cover and a synchronized right-arrow/pull hint
   after 2.2 seconds. Swipe the paper right; a screen-reader action also reveals it. “Nice!” (9+)
   or “meeh!” (8 or less) opens the result. The hidden grade is absent from accessibility semantics.
5. The result shows an experimental ordinal centering score, a conservative heuristic range,
   measured ratios and a short explanation. Only PSA's published 10-centering thresholds (front
   55/45, back 75/25) are official references; the remaining ordinal bands are our own heuristic.
   They are **not** validated PSA grade predictions, probability estimates or certificates.
6. Save writes an atomic private report and durable cropped photo copies. Open saved reports with
   the history icon in Pre-grade. CSV export uses Android's document picker, UTC dates, stable decimal
   formatting, quoted/formula-escaped user text and explicit assessment scope/disclaimer.

Photos and reports stay on the device. There is no upload, cloud inference, verified-grade learning
claim or automatic save. Saved reports are separate from the temporary inspection/photo cache.

## Capture quality

CameraX uses preview/image analysis and quality-mode still capture. The guide uses phone tilt,
image-to-image motion, sharpness, exposure and whole-card detection before automatic capture.
Tap-to-focus, optional exposure lock, a two-second timer and best-of-three still selection are
available. Burst selection uses a sharpness heuristic; real-device validation
is still required. A stable level reading alone no longer triggers capture.

White pixel share alone cannot distinguish printed white from reflections. The photo rejection
rule now combines severe clipping with lack of sharp detail; live capture warns about possible
clipping. This avoids treating ordinary white printed areas as definite glare, but cannot guarantee
that all reflections are detected. Users must still inspect lighting and retake obscured areas.

## Centering standards

The only official criterion used in centering potential is PSA Gem Mint 10: approximately 55/45
front and 75/25 reverse. Source: https://www.psacard.com/gradingstandards, checked 2026-10-06.
A one-image-pixel guide sensitivity interval exposes borderline placements. This is not a
statistical confidence interval. Centering alone never establishes a whole-card grade.

## Retired scan model and training corrections

The old certification-scan weights are retired from the user-facing result. They can only be
called with an explicit research flag. In the old weights, worse front centering could increase
PSA-10 probability. Those numbers are not silently relabelled as phone-photo confidence.

`train_grade_model.py` now constrains all defect/centering feature coefficients non-positive and
fits imputation/scaling using training cards only for held-out evaluation. Export refits only
following that evaluation. Existing weights have not been regenerated without the source data.
Install `scripts/pregrade/requirements.txt` to reproduce the research pipeline.

The displayed heuristic ranges are not trained or statistically calibrated PSA predictions.
There is no claim of exact grade accuracy, calibrated probability, automated physical damage
recognition, or professional validation. Neither good unit tests nor a disclaimer establish that.

## Before claiming validated grading accuracy

Collect rights-cleared phone photos paired with verified certificate grades, including every
supported game/language, foil/full-art and white borders, multiple devices and lighting. Annotate
corner/edge defects separately and include multi-angle surface captures. Group train/test splits
by physical card/certificate; repeat captures must not inflate the independent test population.

`validate_phone_dataset.py` evaluates a JSONL manifest of held-out predictions, checks certificate
split leakage, weights repeated captures per card, and reports coverage, exact/within-one grade
accuracy, mean absolute error, false tens, range-only coverage/width and confusion, stratified by game,
language and device. Conflicting professional labels and incomplete range endpoints are rejected. It never auto-declares
commercial acceptance. Benchmark outline/border-placement errors, corner/edge precision/recall,
photo-quality rejections and abstentions as well, with acceptance thresholds chosen before testing.

Unit and rendered UI tests verify geometry, evidence gating, image lifetime and result flow.
Device tests must still assess autofocus/exposure behaviour, motion/glare thresholds, capture
mapping, memory limits and accessibility sizing. A labelled phone-photo dataset is not in this
repository, so trained defect detection and professional prediction validation remain open.

See [DEVICE-VALIDATION.md](DEVICE-VALIDATION.md) for the private manifest and physical-device
matrix. Camera motion/blur gates have synthetic regressions; Android 8/14 emulator smoke checks
verify binding, analysis, still capture and reopening without claiming optical validation.
