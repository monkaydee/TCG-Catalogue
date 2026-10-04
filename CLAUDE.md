# TCG Catalogue — notes for Claude

Android app (Kotlin 2.1, Compose, Room, CameraX, ML Kit, LiteRT) that scans and prices trading cards
(Pokémon, One Piece, Magic, Dragon Ball, Union Arena, Weiss Schwarz, Naruto). Free sources only.

- Build/check: `export ANDROID_HOME=/opt/android-sdk; ./gradlew --no-daemon -q testDebugUnitTest assembleRelease lintRelease`
- Price server: `worker/` (Cloudflare Worker, TypeScript, D1 cache, per-provider budgets). `cd worker && npx tsc --noEmit -p . && npx vitest run`.
  Deployed by `.github/workflows/worker.yml` on push to `worker/**`; keys only in GitHub secrets (docs/CLOUDFLARE.md).
- The APK users install must come from CI (build.yml, release `v0.1.<run>`): only CI has PRICE_SERVER_URL/APP_KEY.
  A local build has no price server → "no source for graded prices".
- Code map: `scan/` (OCR parser `CardTextParser`, slab labels `parseGrade`, picture search), `data/CardRepository.kt`
  (sources, prices, graded lookup), `grade/` (pre-grader), `ui/screens/`.
- Pre-grader model: `scripts/pregrade/` (train_grade_model.py writes grade/GradeWeights.kt; data provenance in DATASETS.md).
- Strings: every user-visible string in `values/strings_*.xml` plus 20 translations (values-*).
- Rules: no API keys or user email in the APK; no bot-protection evasion; never commit secrets.
- Roadmap: ROADMAP.md.
