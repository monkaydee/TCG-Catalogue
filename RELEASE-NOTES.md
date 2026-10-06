Centering, recognition, pricing and collection update.

- Camera and uploaded-photo matching now check printed names and preserve language and slab details. Japanese, Chinese and Korean OCR models are bundled for offline recognition.
- Pricing matches language, printing and grade qualifiers. Missing slab or foreign-language quotes stay unknown. BGS Black Label and CGC premium grades remain distinct.
- A per-copy cost ledger tracks purchase, manual grading fees, shipping and tax in the lot's original currency. Current unrealized PnL includes all costs; partial sales allocate costs FIFO and subtract sale fees from realized PnL.
- Added grading submissions, durable photo reviews, quote evidence and price challenges, plus local CSV/PDF insurance exports.
- Optional shared learning collects text and individually approved card crops, with private storage, deletion, 90-day retention and moderated daily recognition hints. Production uses bounded private D1 image storage (32 MiB); R2 can expand capacity later. Cloudflare dashboard moderation is available without another app secret.
- New centering editor: eight independent draggable guides (outer card edge and inner printed frame on each side), measured border gaps, live left/right and top/bottom ratios, up to 20× detail and one-pixel adjustments after confirming the whole card. Manual ratios remain separate from the overall grade model.
- Converting a raw card to graded now requires an explicit grading fee instead of assuming grading was free.
- Added company-specific PSA, CGC, BGS and SGC slab layouts, full-resolution pre-grading capture, whole-card outline confirmation and quality gates.
- Database upgrades and older JSON restores preserve slab identities. Duplicate certificates are rejected across card, grade and language disagreements.

Validation: Android unit/migration/screenshot tests, Worker tests and type checking, release build and lint, and private D1/R2 upload/moderation/deletion integration. Private pre-grading reference fixtures were tested locally and are not distributed.

Pre-grading still needs verified-grade phone photos for calibration; unsupported games show measurements only. Exact-language/graded price coverage depends on the providers. Shared reports do not automatically train new neural weights.

Download the APK below and install it over the existing app. Release signing remains compatible with the previous version.
