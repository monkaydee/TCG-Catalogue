# Pre-grading evidence and validation

## Current user workflow

1. Capture/import front and back; confirm the detected physical outline. Detection uses a reduced
   image, while a lossless source copy is retained in the private session cache (up to 4,000 pixels
   on the longest side for bounded memory). Camera still resolution targets 4,000 × 3,000.
2. Perspective correction precedes centering. Printed-frame auto-alignment fits independent border
   lines and abstains if they disagree. This is a suggested rotation, not guaranteed perfect
   geometry: verify the cut and all eight inner/outer guides, especially on skewed printed frames.
3. Inspect four corners and four edges on each side. Detail comes from a separate rectification
   with up to 2,200 source pixels across the card, never AI upscaling. Edge inspection offers the
   entire edge and four segments with surrounding context. Magenta flags mark colour differences,
   not confirmed defects. Zoom adds no source detail.
4. Corner contour comparison checks silhouette asymmetry only when borders/background support it.
   Artwork, foil, low contrast, normal cutting variation and the rectification can confound it.
5. Add at least two low-angle lighting photos for each side and record surface observations.
   Opposite raking-light directions may reveal dents, scratches, creases and print lines. This
   workflow is manual surface evidence, not automatic depth recovery or authentication.
6. The experimental estimate requires clear confirmed photos, completed region observations,
   centering or an explicit borderless skip, and multi-angle surface observations. It shows a
   broad heuristic range with a non-professional disclaimer. Recorded damage lowers/broadens the
   range. Missing evidence is listed explicitly. Manual centering does not by itself block this
   separate estimate. Sharing exports the observed measurements/findings and disclaimer as text.

All session photos and observations stay on the device. Cache cleanup removes old session files;
there is no automatic photo upload or verified-grade learning claim.

## Capture quality

CameraX uses preview/image analysis and quality-mode still capture. The guide uses phone tilt,
image-to-image motion, sharpness, exposure and whole-card detection before automatic capture.
Tap-to-focus, optional exposure lock, a two-second timer and best-of-three still selection are
available. Surface lighting views use manual capture because raking angles intentionally depart
from the level-phone geometry. Burst selection uses a sharpness heuristic; real-device validation
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
