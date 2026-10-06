Pre-grading workflow and assessment fixes after v0.1.139.

- Clearly separates the four-corner card-outline tool from a dedicated eight-guide centering screen.
- Inner/outer selectors, zoom and fine-adjustment controls are visible without scrolling past large photos. Missing automatic front measurements open manual centering before continuing; results provide direct front/back editing.
- Card pages carry the card game into pre-grading. The capture flow also offers game selection.
- Added PSA 10 centering potential across games, including One Piece: within limits, borderline, outside limits or incomplete. Boundary sensitivity is marked; this is a centering assessment, not a final grade prediction.
- Prevents low-quality/unconfirmed photos from displaying a clean wear assessment, replaces the misleading missing-front text, and retains one-decimal ratios.
- Removed unvalidated grade-confidence percentages. Overall Pokémon estimates are explicitly experimental. One Piece overall grade prediction still requires verified-grade phone-photo validation; the app does not invent a grade for unavailable evidence.

Validation: Android unit, reference-photo and UI regression tests; release lint and signed APK build. Tests cover visible inner/outer controls, missing-front recovery and manual One Piece centering potential.

Install the APK over your current app. Full overall-grade accuracy has not yet been validated for a paid pre-grading feature; see docs/PREGRADING-VALIDATION.md.
