# Pre-grading reliability and commercial readiness

The outline editor sets four physical card corners. The separate centering editor sets eight
outer/inner guide positions on a corrected photo. Missing automatic centering opens that editor;
results also provide direct front/back edit actions. A truly borderless design can explicitly
skip centering; it must not be assigned an invented measurement or grade.

Centering potential applies to One Piece and other card games as a geometry assessment.
It tests only PSA's published Gem Mint 10 criterion: about 55/45 front and 75/25 reverse.
Source: https://www.psacard.com/gradingstandards (checked 2026-10-06).
The app distinguishes within limits, borderline, outside limits, and incomplete/poor quality.
A one-image-pixel border-width sensitivity interval exposes boundary placements. This is not
an empirical confidence interval, and the criterion cannot establish a final grade.
Other grade thresholds and companies are not inferred from an unverified approximate table.

Overall grade probabilities previously displayed confidence percentages without phone-photo
validation. Those percentages are removed. The existing Pokémon scan model is labelled
experimental; it is not extended to One Piece by bypassing its domain guard. Manual borders
can support centering potential without becoming uncalibrated inputs to that model.
Bad-quality or unconfirmed photos do not produce a "clean" wear assessment.

## Before selling an overall grade prediction

This requirement remains open; passing unit tests does not establish grading accuracy.

- Collect rights-cleared front/back phone photos paired with verified certificate grades,
  across each supported game, foil/full-art designs, languages, devices and lighting.
- Split by physical card/certificate, not individual photo; keep a blind test set separate
  from training and threshold tuning. Certification scans alone do not test the capture domain.
- Benchmark border placement error, whole-card outline failures, photo-quality rejection,
  per-grade confusion, exact/within-one-grade accuracy, grade error and PSA-10 false positives.
  Include fully borderless cards, glare, white artwork, sleeves, scratches and dents.
- Determine and meet product acceptance thresholds before enabling paid overall predictions.
  Keep abstentions and missing surface evidence visible rather than filling training means.
- Validate real CameraX capture and imported photos on supported Android devices, including
  low-memory phones and accessibility font sizes. JVM rendering tests are not device validation.
- Surface, alteration and authenticity need additional inspection evidence; two static
  photos cannot guarantee these properties. A future surface workflow needs multiple angles.

Current shared recognition feedback is not a verified-grade training pipeline. The user's
ungraded One Piece photos test measurement/capture behavior, not final-grade accuracy.
