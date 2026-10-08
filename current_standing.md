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

## Review fixes (8 October 2026, Claude, after the Codex review of 118714d)

Codex reviewed 0.1.207 and reported 8 defects; Claude verified each in the code and fixed all 8.

| # | Defect | Fix | Test |
| --- | --- | --- | --- |
| 1 | eBay `2x`/`x3`/"3 copies"/"set of 2"/playset listings priced as one card or slab | `MULTI_COPY` rejects them in `titleMatches` (raw and graded) | `worker/test/review-findings.test.ts` |
| 2 | Cloud merge ignored a corrected cost lot with the same id, then saved the stale one | `CostLot.updatedAt` (Room 9, auto-migration); merge takes the newer version first, then new lots (splits) | `LedgerBackupRegressionTest` |
| 3 | Editing a card's purchase price left the ledger unchanged | `applyPurchaseEdit`: writes it into the lots unless they already record different prices (then the lot editor decides); fees and lot currency kept | same |
| 4 | Backup merge dropped incoming per-card price history | history remapped through `rowMapping`; local points for the same day win | same |
| 5 | Replacement import kept the previous portfolio chart | `snapshots().deleteAll()` inside the import transaction | same |
| 6 | NM-only quote stopped the raw chain, so LP/MP/HP/DMG stayed unpriced | app sends `condition` (non-NM); Worker continues until that condition is found, keeps the earlier table otherwise, caches per condition (`:cLP`) | worker test |
| 7 | Deleting/selling the last copy left uneditable grading submissions | `removeRow` deletes the row's submissions and price history (delete, sell-all, quantity 0) | same |
| 8 | Re-selecting the current cloud file released its persisted permission | `choose` releases the old grant only for a different file | not automated (one-line guard) |

Local: Android 219 tests, 0 failures, 3 existing skips; release build and lint OK. Worker 109 passed, typecheck OK.

## Binders and collection list (8 October 2026, Claude)

Requested by the owner before the next Codex review.

- **Several binders.** Home → "Open binder" now opens a shelf: the main binder (every card, unchanged) plus the user's own
  binders and a "New binder" tile. Own binders are Room tables `binders` and `binder_cards` (schema 10, auto-migration);
  a card row may sit in several binders. Cards are chosen in the binder (＋ / menu → "Add cards": search and tick).
- **Closed covers.** Every binder opens on its cover (turner page 0); swiping, tapping the cover or the › button opens
  page 1. Covers: 8 preset designs (`CoverDesign`) or the user's own picture (stored in app files, never uploaded).
  The main binder's cover is in settings (`main_binder_cover*`).
- **4 × 3 layout** (`Binder.CLASSIC`, 4 rows of 3) is the new default; 3 × 3, 6 × 6 and 9 × 9 remain.
- **Open collection.** New button next to "Open binder": a scrollable list with picture, name, set/number/language,
  slab label (or condition), price per copy and quantity; game filter and sort by value, name, set, recently added, grade.
- **Data safety.** Deleting or selling the last copy removes it from binders; merging rows moves membership; backups
  carry binders and memberships (merge matches binders by name). Cover pictures are not in backups: on another phone
  the preset design is shown.
- Tests: `CollectionListTest`, binder case in `LedgerBackupRegressionTest`, screenshots `binder_4x3`, `binder_covers`.
  Local: 225 tests, 0 failures, 3 skips; lint 0 errors, 319 warnings.
- Not done / for review: adding a card to a binder from the card page itself; per-binder layout/sort (shared setting today);
  physical-phone check of the cover tap/swipe feel.

## Binder pockets and card arrival (8 October 2026, Claude)

- **Copies:** option "Each copy its own pocket" (binder ⋯ menu, setting `binder_spread`, default on). Off = one pocket
  per card row with ×n. Slabs, raw copies and different grades/companies/certificates were already separate rows
  (`copyKey`, grade in `condition`), so they always get separate pockets.
- **Own binders have fixed pockets:** table `binder_slots(binderId, slot, cardRowId)` replaces `binder_cards`
  (schema 11, hand-written `MIGRATION_10_11` keeps each binder's cards in pockets 0, 1, 2 …; `BinderSlotMigrationTest`).
  Tap an empty pocket (＋) → choose the card for exactly that pocket; long-press a card → tap another pocket to move
  it there (swaps with a card already there) or "Remove from binder". ＋ in the toolbar still adds many cards; they fill
  pockets after the last used one. Choosing a sort order in an own binder lays its pockets out again in that order.
  A row never fills more pockets than it has copies (selling/editing trims its last pockets). Old backups without
  pocket numbers get numbered on restore; merge appends rows a binder lacks after its last pocket.
- **Animation:** cards placed or moved fall into their pocket from above the page (750 ms, ease in/out, slight turn
  and shadow; several cards 170 ms apart). Screenshots `binder_arrival_mid/done`.
- Main binder unchanged otherwise (auto-sorted by the sort setting, no manual pockets).

Competitor gap research (8 Oct): [docs/COMPETITOR-GAPS-2026-10-08.md](docs/COMPETITOR-GAPS-2026-10-08.md). Recommended next: "Worth grading?",
cost to complete a set, CSV import from other apps, slab cert barcode scan, more currencies + bulk multi-select.

## Collector tools, batch A (8 October 2026, Claude) — from docs/COMPETITOR-GAPS-2026-10-08.md

- **Show prices in** 27 more currencies (Settings → Prices): `Money.format` converts at ECB reference rates (Frankfurter,
  refreshed with prices) for display only; stored amounts, inputs, ledger and backups stay EUR/USD. No rate → shown as stored.
- **Worth grading?** (card page → Graded prices, raw cards): graded quote − raw value − user's grading cost (setting,
  default 30), per quoted grade, and the lowest paying grade per company. Exact quotes only; no grade prediction.
- **Cost to complete** (set checklist): on request, cheapest market printing of each missing card, summed; cards without
  a price counted apart (`SetCompletion`).
- **Notes** per card (card page; `OwnedCard.notes`, schema 12 auto-migration; in backups).
- **Selecting many cards** in Open collection (long-press): add to an own binder (after its last pocket), mark for trade,
  lot value, delete (with confirmation).
- **Lot calculator** (collection selection or Collection tools): known market value, items without price counted, offer
  at 30–120 % of market (`LotValue`).
- Tests: `CollectorToolsTest`, notes/add-to-binder case in `LedgerBackupRegressionTest`. Local: 234 tests, 0 failures.

## Collector tools, batch B (8 October 2026, Claude)

- **Import from other apps** (Collection tools): CSV with header (ManaBox, TCGplayer, Dragon Shield, Collectr, own export;
  `,`/`;`/tab, quoted fields, EU decimals) or plain lists ("4 Lightning Bolt (M10) 146"). `CsvImport` maps columns,
  conditions and languages; `matchImport` finds the card (Magic: set code + number exact; else name, One Piece by code)
  and narrows by number and set. Sure = exactly one fits number (+ set); sure lines are ticked, others offer up to 5
  options. Japanese Pokémon rows matched to an international ID are never "sure". Max 2000 lines per file.
  Collectr's column names are assumed from public material (unverified).
- **Slab barcode/QR scan** (add sheet → certificate field icon): Google code scanner (`play-services-code-scanner`,
  Play services UI, no camera permission of ours). `SlabCode` reads the cert and company from grader links; PSA certs fill
  the grade through the existing cert check when the price server is set up.
- **Market movers**: home "Movers this week" and collection sorts "Rising/Falling this week" from our own daily
  `price_history` (latest vs last point ≥7 days earlier; cards under 1 unit ignored). Not market-wide data.
- **Achievements** (home ⋯): 15 local badges computed from the collection; nothing stored or uploaded.
- Tests: `ImportAndMoversTest`. Local: 241 tests, 0 failures; lint 0 errors, 319 warnings.

## Collector tools, batch C (8 October 2026, Claude)

- **PSA population + gem rate** (card page → Graded prices → "Show population"): through an owned PSA slab of the same
  card and printing with a certificate (`/v1/pop?cert=`, existing Worker route and cache), shown on raw copies too.
  Without such a slab there is no free way to find PSA's spec ID, so nothing is shown. Other graders: no free API.
- **Decks** (home ⋯ → Decks): `decks`/`deck_cards` (schema 13, auto-migration, in backups; merge by name + game).
  Paste a deck list (matched like the CSV import) or add owned cards; adjust copies; value from prices noted when added;
  "still missing" vs owned copies with cost; share as text. Checks: Pokémon 60 / max 4 per name (basic energy free),
  Magic ≥60 / max 4 (basic lands free), One Piece 51 incl. Leader / max 4 per number. No banned-card/format legality.
- **Share as page** (Open collection, a selection, or an own binder's menu): one self-contained HTML file through the
  share sheet, with or without prices. **Decision:** no hosted public link — that needs hosting, privacy and moderation
  decisions (see shared-photo rules above); this keeps everything on the phone until the owner decides otherwise.
- Tests: `DeckAndShareTest`, deck case in `LedgerBackupRegressionTest`. Local: 246 tests, 0 failures; lint 0 errors.
- Not built from the gap list: in-app marketplace listings/watchlist (Go Shopping stays a plan), eBay listing from the app,
  live translation, UPC sealed scan (no verified free UPC source).

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
