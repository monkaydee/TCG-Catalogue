# Recognition, pricing and pre-grading audit — 5 October 2026

Base: `claude/tcg-card-scanner-app-e2tlar`, `9462af2`. Work branch: `codex/recognition-pricing-audit`. The supplied ROADMAP-5.md was reviewed alongside the repository roadmap. New features below are proposals; feedback ingestion and grading costs have not been implemented.

## Corrections

| Area | Reproduced problem | Change |
| --- | --- | --- |
| Camera recognition | Camera skipped the printed-name check used by imports; blank frames did not interrupt a recognition streak | Apply the check and require consecutive matching frames |
| Imports | Picture fallback could discard slab grade; small-text retries/thumbnail used the wrong rotation | Preserve grade and best rotation; keep detected language with picture candidates |
| Slabs | Plain BGS Pristine 10 could become Black Label; abbreviated company/grade labels were missed | Require explicit Black Label or four named perfect subgrades; recognize direct labels and separated CGC Pristine/Perfect |
| Language | Japanese Pokémon hits lacked language; ambiguous language scores chose an arbitrary winner; CJK names disappeared during normalization | Carry language, leave ties unresolved, preserve Unicode letters/digits |
| Automatic add | Slabs could be added before the grader/grade was reviewed | Route slab detections through review in camera and photo bulk flows |
| Valuation | Missing language/graded prices fell back to English/raw prices | Keep value unknown; clear incompatible quotes after edits/refresh |
| Graded coverage | First provider with any grade prevented later providers filling PSA/CGC/BGS gaps | Merge distinct grader/grade/qualifier/currency quotes while preferring earlier providers |
| Grade premiums | Premium 10 prices were discarded or mixed with ordinary 10s | Carry a separate qualifier end to end |
| Printing | Several providers silently substituted other printings; first-edition TCGdex price could use unlimited | Require available matching printing; reject premium prints a coarse API cannot distinguish |
| eBay | Marketplace implied card language; grade digits could match collector numbers; reverse/shadowless mismatches | Require non-English evidence, separate grade from collector number, check printing; include full number/set in searches |
| Quotas | Two graded eBay searches counted as one | Reserve/refund actual search count; preserve graded flag for duplicate requests |
| Purchase cost | Changing quote currency changed the interpretation of stored purchase cost | Convert existing basis when quote currency changes; default missing quote currency to the user's currency |
| Pre-grader | Preview capture was low resolution; image boundaries created fake edges; corner closeup counted as a card; tiny sharpness inputs could fail | Use ImageCapture; clamp image sampling, reject boundary/implausible outlines, guard tiny images and withhold grades from unmeasurable inputs |
| Pre-grading estimates | Pokémon model could be applied to other games and missing measurements; outdated PSA front-10 centering cutoff | Require supported game, usable front/back and centering; use current 55/45 front threshold |
| Slab appearance | CGC used old blue style; BGS 9.5 was silver | Current black/silver CGC, gold Pristine; gold BGS 9.5/10, separate Black Label; PSA red frame and SGC black well |

The cost-currency repair preserves the current database design. A proper per-copy cost ledger is still needed: aggregated copies currently cannot retain distinct purchase and grading costs, and repeated currency conversion uses the configured rate rather than historical rates. Existing incorrect prices must be refreshed; existing misidentified cards/grades require review. No automatic migration can recover an unknown original language, purchase currency or physical printing.

## Validation and limits

- Worker: **69 regression tests pass**, with TypeScript type checking. Coverage includes pricing normalization, matching, graded coverage, cache keys, quota behavior and legacy-client compatibility.
- Android: **122 unit tests pass with the external fixtures enabled**. The initial fixes passed `testDebugUnitTest assembleRelease lintRelease`; the expanded release has passed the final unit/migration suite and is built/published by GitHub Actions. Regression tests cover slab parsing, language ambiguity, Unicode names, image quality and rendering. Slab screenshots are generated under `app/build/screenshots/`.
- Release lint completed with **zero errors and 269 warnings** (mostly typography and dependency/version notices). Context-retention warnings were checked: the scanner/import constructors are passed application contexts, and AppStrings stores an application context. The remaining warnings are recorded in `app/build/reports/lint-results-release.html`; they have not been broadly suppressed or cleaned up.
- External photo fixtures: set `PREGRADING_FIXTURE_DIR` for `PreGradeReferenceTest`. Private Drive images remain outside Git; tests skip these inputs when the fixture directory is absent. Results are written under `app/build/pregrade-reference-results/`.
- Drive experiment: full ST30-001 front and red leader back produce complete outlines; the corner closeup is rejected in one rotation but can still yield a false internal outline in the EXIF-correct orientation. It has no usable centering and cannot produce a grade estimate. Both full sides still have **no automatic centering result**. Detection success is not evidence that wear estimates or grades are correct.
- Online experiment: official Pokémon front/back images are too low resolution for physical grading and correctly trigger the size check. An official One Piece front exercises outline detection but printed white art triggers the current glare heuristic. A front/back pair image also reveals a false enclosing outline; absent centering blocks a grade, but the detector still needs a multi-card/artwork rejection stage. Publisher artwork has no known physical defects or grade and cannot validate accuracy.
- No physical Android camera/emulator OCR end-to-end test or real authenticated price-provider smoke test was available in this workspace. Local release builds have no production pricing credentials. Production deployment is checked through the GitHub Worker workflow; private R2 and the moderation key are reported independently by status.

## Remaining reliability work

1. Build a labeled corpus for exact identity, language, variant and slab grade. Report correct candidate recall and incorrect auto-add rate separately from top-1 accuracy. Bundled Chinese/Japanese/Korean OCR is now included; physical device latency and accuracy still need measurement.
2. Verify licensed provider access and real response coverage. Strict matching intentionally produces more unknown prices. Some provider schemas only offer coarse printings; eBay titles can omit identity/language information. Asking prices are not sold comparables, and an unsold outlier is not a reliable value. Number/name/title filters also cannot prove every alternate-art image; those cases need exact product or image evidence.
3. Separate temporary provider outage from confirmed absence, retaining a dated stale quote for the exact same identity when appropriate. The Worker now exposes graded availability separately and Android propagates graded/language server errors so those refreshes retain prior quotes. Broader raw-source helpers still need a structured found/not-found/unavailable result.
4. Pre-grading needs reliable whole-card/artwork rejection, reference-aware borders/artwork masks, better glare detection, multi-angle surface photographs and calibration on verified grades captured with phones. Existing model training on graded scans does not establish performance on phone captures. Other games should retain measurement-only behavior until validated.
5. Historical CGC labels, subgrade layouts and other companies need distinct reference-backed templates. Current rendering uses plain company wordmarks and approximate holder styling, not exact reproductions of every label generation. Figma installation was suggested through plugin management; it has not been installed or connected in this session. No downloadable design skill was available to install automatically.

## Features authorized and implemented

| Priority | Feature | Benefit and implementation scope |
| --- | --- | --- |
| 1 | Real PnL / grading costs | Per-copy purchase basis and original currency; manually entered grading fees, shipping/tax; current matched raw/graded value. Unrealized PnL today = today's value minus purchase cost minus grading costs. Daily movement is separate. Missing cost/value is unknown; sales need actual proceeds and selling fees. Quantity splits and migrations need tests. |
| 2 | Price evidence | Explain source, date, language/printing/grade, asking vs sold, sample count/range and stale status. Add a price challenge action rather than silently averaging incompatible markets. |
| 3 | Recognition review queue | Show read number/set/language and candidate evidence; keep unfinished imports and corrections. This supplies reviewed feedback rather than assuming every addition is a correct label. |
| 4 | Grading submissions | Per-card status, submission number, costs and return date linked to PnL. A grading-return calculator should wait for calibrated probabilities. |
| 5 | Insurance/export | Dated pictures, certificates, purchase basis and valuation evidence in CSV/PDF. Useful even when prices are unavailable. |

The user authorized all proposals. Collection tools and shared recognition reporting are implemented; see docs/COLLECTION-TOOLS.md and docs/SHARED-LEARNING.md for behavior and setup. Use separate consent, private D1/R2, crop review/redaction, rate limits, deletion/retention controls and reviewed versioned rules. Three install IDs are a review threshold, not reliable independent-user consensus. Keep training and evaluation images separate; require measured improvement and rollback before distributing learned rules. Uploading reports does not automatically retrain an on-device model.

## Research sources

- [PSA grading standards](https://www.psacard.com/gradingstandards): current front centering allowance for a 10 is approximately 55/45; back 75/25. Centering alone does not determine a grade.
- [CGC new grading scale and labels](https://www.cgccomics.com/news/article/11754/cgc-cards-merger/): modern black/silver label and gold Pristine label. Legacy labels need separate handling.
- [eBay Browse API](https://developer.ebay.com/develop/api/buy/browse_api) and [buying application access](https://developer.ebay.com/develop/get-started/get-started-on-a-buying-application): listings vs restricted Marketplace Insights sold-data access.
- [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/): current free-tier allowances; retention and operational costs still matter.
- [Collectr](https://getcollectr.com/): existing portfolio/cost-basis and trade workflows support prioritizing transparent valuations and a complete cost ledger.
- [Official Pokémon artwork](https://tcg.pokemon.com/en-us/galleries/scarlet-violet/) and [One Piece card list](https://en.onepiece-cardgame.com/cardlist/): reference fixtures, not physical-grade ground truth.
- [One Piece back colors](https://cardgamer.com/beginners/one-piece-card-back-colors/): red leader / blue ordinary / white DON context for game-specific back handling.

## Release handling

The existing Worker workflow deploys on worker changes pushed to **any branch**. These changes are local and reviewable. Before pushing an audit branch, restrict automatic production deployment to the intended release branch or explicitly approve a deployment. Provider keys and the production app key have not been printed, embedded or changed.

Release validation adds cost allocation, original-currency basis, unknown/zero distinctions, CSV injection handling, version-6 database migration, certificate separation and local D1/R2 feedback integration. Verified-grade phone calibration and provider data coverage remain external acceptance work.

Manual centering now supports user-placed printed-frame guides, live ratios and magnified fine adjustment after whole-card confirmation. Manual measurements do not become calibrated overall-grade inputs. The supplied Instagram screenshots are implemented as independent outer/inner guide pairs, preserved outer cuts, four border gaps, and draggable zoom detail up to 20×.

The v0.1.139 feedback revealed a discovery/flow failure: users entered the four-corner outline
tool instead of the buried centering panel. The follow-up uses a dedicated editor, keeps guide
selectors visible and provides result-level recovery. A manual One Piece centering result now
supports the published PSA 10 centering criterion without enabling an unvalidated overall model.
Commercial overall pre-grading readiness remains open (docs/PREGRADING-VALIDATION.md).
