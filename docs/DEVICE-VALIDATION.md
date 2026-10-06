# Phone validation and release limits

The current range is experimental. Automated geometry/quality/flow tests do not establish
professional grade accuracy, physical surface-defect detection or real camera reliability.
No labelled phone dataset or connected physical phones are available in the build workspace.

## Blind grading manifest

Capture rights-cleared front/back photographs and multi-angle surface photographs before
looking at the professional grade. Use a physical identity including the grader (e.g. PSA:cert),
and verify the certificate/card/grade rather than accepting an arbitrary label. Keep photos and
certificate numbers private. Do not put them in release artifacts.

Run `python scripts/pregrade/validate_phone_dataset.py private-manifest.jsonl`. Rows require
`certificate`, `split`, `grade`, `game`, `language`, `device`, `certificateVerified`. Record the
app's `low`/`high` range; `predicted` may be null. It is not valid to invent a midpoint prediction.
The evaluator rejects cross-split physical cards and conflicting labels, weights repeat captures,
reports range coverage AND width, and breaks results down by game/language/device.

Pre-register acceptance thresholds and the target population before collecting the holdout set.
Include damage grades, white borders, foil/full art, alternative cuts and all supported languages.
A small convenient sample or high coverage from a very wide range cannot establish accuracy.
Professional validation remains incomplete until the verified holdout has actually been collected
and the report independently reviewed. The evaluator never automatically approves a model.

## Physical device checks (to record for each supported device)

- Android 8/11/14/15, low-memory devices, narrow screens, alternate camera vendors.
- Permission denial/regrant, camera dismissal/reopening, background/foreground and interruptions.
- Tap focus, supported/unsupported AE lock, best-of-three selection, timer cancellation.
- Stable/blurred/moving frames, dim/bright light, white ink and foil reflections.
- Capture crop/rotation maps to the preview guides; no clipping of actual card corners.
- Source-resolution zoom, four edge segments, multiple surface views, removed-view reset.
- German/Japanese text wrapping, enlarged fonts, screen readers and button reachability.
- Memory usage across repeated front/back/surface captures, including gallery imports.

The JVM suite covers motion/quality gating and manual evidence requirements; the emulator smoke
job checks Android lifecycle/camera binding where virtual hardware is available. Physical camera
optics, glare and focus still require the device matrix above, with real cards.
