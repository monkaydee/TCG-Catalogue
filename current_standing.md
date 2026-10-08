# CardNavo — current standing and Claude handoff

**Updated: 8 October 2026 (UTC).** Read this file before changing the app. It separates
implemented behaviour, verified checks, remaining data/validation gaps and future proposals.

## Repository and release rules

- Repository: `monkaydee/TCG-Catalogue`.
- Working branch: `claude/tcg-card-scanner-app-e2tlar`. **Do not merge main without instruction.**
- Starting release for this work: **CardNavo 0.1.205**, commit `997dc282e78751178bc3538ef3e87e9843e99f24`.
- This update uses the existing Actions release pipeline. Its published release number is
  `0.1.<Build APK run number>`; APK is `cardnavo-<run number>.apk`. Keep name/version convention.
  Check the successful push run and attached release for this file's commit for the final number;
  PR runs also consume run numbers and do not publish releases.
- Keep the current package `com.monkaydee.tcgcatalogue` and signing certificate SHA-256
  `6826240eb61f6b1a6cb42d74701f062606611509eff53c23080c4aba0506c7b2`.
- Do not copy keys, private photos, private report payloads or credentials into this public repo.
- Shopping is **an idea/plan only**. Do not introduce shopping UI or promise universal cheapest prices.

## What was updated in this work

| Area | Implementation | Files / evidence |
| --- | --- | --- |
| Photo safety | Disable shared-learning photos in app and reject image/approval fields server-side before persistence, even forged clients | `SharedLearning.kt`, `LearningPanel.kt`, `worker/src/feedback.ts`; upload-rejection tests |
| Data minimisation | New client sends normalized OCR fingerprint, not readable OCR; server excludes OCR/suggestion/name/set prose from stored recognition reports | Hash-only request/storage regression; pseudonymous data still needs GDPR treatment |
| Legacy learning evidence | Revision-2 cleanup deletes legacy photos/free-OCR reports and dependent rules; preserves references and fails if R2 deletion cannot run | `ensureLearningPolicy`, `learning_policy` table; deployment verifies completion |
| Withdrawal | Old photo-enabled clients queue deletion on upgrade; pending deletion blocks queued sends; orphan installation tokens expire; missing confirmation is unconfirmed | `SharedLearning.kt`, daily Worker cleanup; explicit selections still require review |
| Automatic OS backups | Disable implicit Android cloud backup/device transfer for private data; explicit chosen-file backup/import/cloud sync remains | Manifest and backup/data-extraction XML rules |
| Malicious request size | Stream-bound prices/sealed/online-identify/moderation requests without trusting Content-Length | `boundedBody`/`boundedJson`, cancellation regression; feedback was already bounded |
| Raw regional pricing | Verify language through eBay's returned Language facet for raw searches too, including titles omitting language; reject conflicting explicit languages | `ebayRaw`, two-call budget accounting; mocked provider regression |
| Asking-price deviations | Isolate disconnected >2x adjacent bands; retain only a strict majority; abstain on tied/unsupported split bands; retain prior median bounds and limited-evidence labels | `comparables.ts`; minority/extreme/tied-band tests |
| Graded diagnostics | Return up to five accepted public listing title/ID/price/currency examples, after outlier removal, for investigation | `comparableExamples`; not proof of artwork authenticity or completed sale |
| Japanese API alias | Accept JP and normalize to JA at Worker boundary for cards and sealed; UI continues displaying JP | Alias/cache-key regression; internal TCGdex ISO language JA remains correct |
| Cache safety/freshness | Validate required containers for each known feed, check atomic replacement success and reload in-memory Pokémon names after file replacement | `CardIndexApi`; schema/freshness regressions |
| Collection price repair | Collection tools show missing-price count, JP identity-repair count and links to affected cards | Existing exact native JP selector used; no silent conversion of an English collector number |
| Sealed images | Retry alternate thumbnail sizes of the same TCGplayer product; preserve exact-language identity and placeholder when missing | Same-product/source regression; cannot generate missing supplier artwork |
| Recognition accuracy gate | Default golden-set exact threshold now 90%; empty data and invalid/zero thresholds fail when run | `GoldenSetTest`, recognition workflow; private photo corpus still not configured |
| Documentation | Updated privacy/learning description, upload review, competitor comparison and this handoff | Linked below; operator/privacy contact remains required before public launch |

The preceding 0.1.205 release already slowed the slab reveal to one 360°/2.8-second rotation,
placed the synchronized pull cue beneath the paper, redesigned PSA labels, added curved
binder turns and fixed interrupted/page-count transitions. It also protected quote refresh
classification, sealed edits during refresh and malformed catalogue replacements. Do not
revert those fixes. Four-corner outline confirmation on the original full photo precedes
pre-grade cropping and is available for correction.

## Current features

- Android local collection for Pokémon, One Piece, Magic, Dragon Ball Fusion World/Super,
  Union Arena, Weiss Schwarz and Naruto. Catalogue/provider coverage differs by game.
- On-device ML Kit OCR; photo import/share, multi-card review, stack scan and local visual
  matching. Picture-only/ambiguous printings require review. Optional Identify online uses
  Ximilar through the Worker only after explicit action; Worker does not persist the photo.
- Raw cards with finish/printing, condition NM/LP/MP/HP/DMG, language, quantity and own value.
  Native JP Pokémon set/number selection and same-name candidate repair. Display JP; keep JA
  where required by ISO language APIs.
- Company-specific slab visuals and recorded grader/grade/cert/qualifier. Supported slab labels
  include PSA/BGS/CGC/SGC/TAG/ACE/AOG/GSG/PI/other. Exact grade/language/printing quotations
  only; unavailable slab values never silently become raw or another company's price.
- Price providers, condition references, source/freshness/limited-evidence notes, all-prices
  panel, personal price history, alerts, browser price/sold links and optional PSA cert/population.
- Sealed packs/boxes/tins/bundles/decks/cases catalogue/inventory and language-specific asks
  where available. Aggregate product guides remain labelled references, not exact-language quotes.
  One Piece has no DE printed-language catalogue entries; a German seller is a different concept.
- Portfolio with known-price totals, set views/checklists, wishlist, trade flags/list and sold
  records. Unknown prices are not genuine zero values.
- Curved virtual binder, layouts/sorts/game filters, page-share images and full-screen browsing.
- Experimental local centering pre-grade: front/back photos, editable outlines/centering,
  rotating covered-grade reveal, swipe interaction, explanation, CSV and saved reports.
  **Surface grading is not validated. Current fallback is centering-only, not a full AI grade
  or PSA certification.** Nice! / meeh! actions lead to the result.
- Costs/grading/shipping/tax ledger, grading submission tracker, PnL and CSV/PDF insurance export.
- Manual JSON backup/import and chosen-file cloud backup/sync; no account-based hosted collection.
- Themes, custom colours/backgrounds, 21 UI locales and three widget styles (Value, Dashboard,
  Collector) with per-widget transparent background/font colour.
- Opt-in hash/identifier recognition reports and explicit incorrect-price reports; private
  manual operator review. No automatic neural training or public user-photo database.

## Bugs/errors and limits that still matter

| Priority | Status | Remaining work / blocker |
| --- | --- | --- |
| P1 | **Open data/access gap** | Exact DE/JP raw and graded coverage is incomplete. Previous production snapshot: German Misty's Psyduck Destined Rivals CGC 9 not_found. New facet search improves eligible raw coverage but cannot supply absent grades or sold history. Secure approved sold-comparable provider access. |
| P1 | **Open identity task** | A saved JP row with English `me01-135`/`me01-150` must be repaired to its printed native identity. Exeggutor M1L-066 and Steelix M1L-073 are examples, not an automatic mapping for every card. Repair links are implemented; user confirmation is necessary. |
| P1 | **Open validation dependency** | Real-photo recognition corpus is absent, so CI golden job skips. A green setup/parser check is not measured camera accuracy. Default threshold is fixed, but configure rights-cleared private photos and regional/printing/slab/negative cases. |
| P1 | **Open operational/privacy readiness** | Actual controller/name/address/private contact, processor contracts, transfer safeguards, backup deletion, children/age rules and incident/access processes need owner verification. Technical changes are not DSGVO certification. |
| P2 | **Open image-data gap** | Regional catalogue thumbnails remain sparse; exact-language provider images may fail. Same-product size retry/placeholder helps broken sizes, not absent pictures. Obtain verified rights-cleared product imagery without substituting another language. |
| P2 | **Open matching-validation limit** | Listing titles/language facets plus statistical consensus do not prove listing artwork/physical condition. Thin asks stay limited, even one listing; they are asking references, not sold values. Review new public title examples and add licensed verified evidence. |
| P2 | **Open device verification** | Binder frame pacing, card capture/outline accuracy, widget readability and reveal feel need physical-phone testing. Automated geometry/screenshots/camera smoke checks cover bounded behaviour, not every GPU/launcher. |
| P2 | **Open backlog** | Existing lint warnings remain; no claim of warning-free app. Check final release lint report for count. More advanced filters and large-collection tools performance should be profiled. |

Reference evidence from 7 October is in [QUALITY-AUDIT-2026-10-07.md](docs/QUALITY-AUDIT-2026-10-07.md).
Those prices/image counts are dated snapshots, not fresh market valuations. For **this** update,
inspect the matching Worker run's `price-verification` artifact. It checks new revision/source/
currency isolation, photo rejection, legacy cleanup and sample live providers; it is not exhaustive
coverage and cannot convert active listings into sold history.

## Friend's German message: how it is addressed

“Du brauchst auch ein Filter, dass starke Abweichungen aussortiert werden … falsche Bilder …
Datenschutz … FSK18 oder verbotener Inhalt … DSGVO konform.”

- Price outliers now have a stronger majority-band filter with abstention.
- A card-shaped JPEG/similar embedding is **not** a content classifier. Shared photos are blocked
  server-side, not merely hidden behind an app switch. Local photos remain private on device.
- Reopening shared photos needs validated independent content/card-identity filters, private
  handling, lawful processing/rights, deletion and operational review. Do not implement an
  untested distance threshold and call it NSFW protection or GDPR compliance.
- OCR/install hashes are pseudonymous. The updated notice describes reports, withdrawal and
  retention; it explicitly lists missing operator obligations.
- See [UPLOAD-PRIVACY-REVIEW.md](docs/UPLOAD-PRIVACY-REVIEW.md) and [PRIVACY.md](docs/PRIVACY.md).

## Other apps and next ideas

Research used official Collectr, Dex and TCG Collector pages on 8 October; marketing claims
were not benchmarked. [The comparison](docs/COMPETITOR-COMPARISON-2026-10-08.md) links sources.

1. Finish reliable price coverage/diagnostics, native JP repairs and the real-photo benchmark.
2. Advanced collection filters: language, condition, printing, grader/grade, missing/stale/manual
   price, duplicates, trade status; named custom folders and master-set progress.
3. Local two-sided trade calculator mixing singles/sealed/cash, with explicit unknowns/fees.
   A trade list already exists; a valuation calculator does not.
4. Species/Pokédex collecting and curated broader Japanese/Chinese regional variants.
5. Verified sold comps, longer history and reliable freshness-aware alerts, subject to data rights.
6. **Go Shopping remains planned:** country-selected domestic comparison (DE/US pilot), exact
   product/type/language/condition/grade, known delivered totals, coverage disclosure and merchant
   links. Worldwide growth depends on access/feeds; never promise all shops/absolute cheapest.
   [Existing plan](docs/GO-SHOPPING-PLAN.md). No shopping code was added.
7. Web/iOS/account sync or friends/Trade Finder/social require a separate architecture/privacy/
   moderation decision. Do not add public uploads as a small incidental UI feature.

## Validation and release handoff

Local validation commands:

```sh
cd worker
npm test
npm run typecheck
cd ..
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintRelease
```

This work adds request-limit, deviation, raw-language-facet, JP alias, upload rejection,
hash-only report, legacy-cleanup safety, cache-schema/freshness and same-product image tests.
Local Android validation: **212 tests, 0 failures, 3 existing external-fixture skips**; release
lint **0 errors, 319 warnings**. Python: **14 passed**. Worker: **104 passed** and TypeScript typecheck passed after the final provider changes.
The resulting GitHub CI report is authoritative for the published build.

The final GitHub build runs full Android/Worker/Python checks, release lint, APK publication;
camera smoke and optional golden workflows are separate. The recognition corpus is not supplied
by synthetic catalogue/screenshot tests. Consult the final run result, not this file alone,
for executed totals and publication status.

After publication verify APK package/name/version, SHA-256 against the release asset digest,
and the unchanged signing certificate. Do not change the version convention to fill in this
file or manufacture a successful golden score. Worker matching/cache revisions are independent
of the Android Actions run number: cards v8, sealed v9, learning policy revision 2.

### For Claude's next session

1. Read this file, release notes, last successful build/deploy and current working-tree status.
2. Preserve existing user collection and version/signing/branch conventions; no main merge.
3. Verify external dependencies before promising missing prices, pictures, full surface AI or
   DSGVO certification. A native JP identity repair is an explicit user choice.
4. Keep shared photos disabled until a real moderation pipeline and operator arrangements exist.
5. Prioritise known defects/data quality before adding social/shopping or changing navigation again.
6. Update this file with dated verification, concrete fixes, remaining gaps and decisions on each
   substantive change. Do not silently remove open dependencies from the handoff.
