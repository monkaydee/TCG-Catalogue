# CardNavo reliability audit — 7 October 2026

Scope: pre-grade reveal, binder interaction, price retention, catalogue/image coverage and recognition paths. Base app: `990982b` (0.1.203). This is a source/fixture/public-endpoint audit plus inspection of the latest authenticated deployment artifact, not a claim that every user's collection was tested.

## Fixed in this update

| Priority | Confirmed issue | Correction and evidence |
| --- | --- | --- |
| P1 | Raw server fallback used `getOrNull()`, so an upstream outage became a successful empty lookup | Propagate the failure to refresh handling. Preserve the exact saved quote/date and show an unavailable notice; a confirmed miss remains distinct. The old quote was already retained on many misses; the bug was inaccurate error classification. Mock provider regression checks both cases. |
| P1 | Sealed refresh wrote its original row after waiting for the network, overwriting edits to quantity or purchase cost | Apply quote fields in a transaction to the latest row. Verify row identity and do nothing after removal/language/product changes. Test edits, deleted rows, changed language and quote failure. |
| P1 | HTTP 200 HTML/truncated catalogue downloads could replace valid local indexes, breaking search and recognition until another download | Validate JSON before replacing daily files and validate the card-index schema before replacing the typed index. Regression preserves a usable file and does not create a cache from malformed data. |
| P2 | Grade parser accepted positive nonfinite values from a malformed provider response | Require finite positive graded prices. |
| P2 | Binder settlement could advance outside the new page range after a filter/grid change; interrupted turns needed protection | Clamp completion to current page bounds and invalidate canceled transitions. Exercise short drag, forward/back buttons, jumps and page-count reduction. |
| P2 | Reveal spun 510 degrees in 1.6 seconds, pull hint was remote from paper, and PSA label inherited app styling | One 360-degree turn over 2.8 seconds, synchronized hint directly below the grade cover, and a shared compact label with explicit sans-serif typography. Preserve swipe/accessibility reveal and four-corner capture review. |
| P2 | Binder moved as a stiff flat panel | Curved strip projection, softer release, bounded 780 ms button turns, lighting following the bend. Record the grid once per draw on hardware; software exports/previews have a safe fallback. Hardware uses the supplied page reverse; software uses a plain reverse. Physical-device frame pacing still needs user validation. |

## Production pricing evidence

Latest authenticated verification inspected: [Worker run 37602261988](https://github.com/monkaydee/TCG-Catalogue/actions/runs/37602261988), artifact `price-verification` created **2026-10-07 09:41 UTC**. These are snapshot results, not prices freshly fetched under a user's account, sold-price guarantees or valuation advice.

- Flareon Jungle English PSA 8 returned an eBay asking quote of USD 199.99 from 15 matching listings, labelled limited evidence.
- Dark Charizard Team Rocket English first-edition PSA 5 returned USD 675 from two matching listings, labelled limited evidence. Despite the DE marketplace request, the source falls back to an international reference; that is not proof of domestic German stock.
- German Misty's Psyduck Destined Rivals CGC 9 still returned `gradedReason=not_found`. English quotes for this product do not cover German CGC 9. A manually found sold listing is not automatically available through eBay Browse, which searches current listings. Matching company, grade, printing and printed language must stay exact.
- The direct JustTCG Flareon PSA 8 probe returned HTTP 200 and two graded variants but **zero priced variants**. Existing API credentials do not imply full price coverage.
- Japanese sealed Two Legends and Azure Sea's Seven boxes/packs returned asking quotes and images. German Pokémon Surging Sparks, Black Bolt bundle and White Flare ETB also returned asking quotes/images. These routes are operational for those fixtures; no conclusion follows for all sets or languages.
- German and Japanese raw-card fixture requests still had `not_found` cases. Do not display zero or substitute an English/raw quote to conceal them.

### P1: More exact-grade and language coverage is still needed

Current eBay access provides active fixed-price asks, not completed-sale history. Secure an approved completed-sale provider and monitor coverage per identity. Preserve source, sample size, spread and dates. In the snapshot, Charizard PSA 10 asking evidence was USD 179.99 from one listing while PSA 9 was USD 4,000 from three listings; the app labelled both limited. This is a review signal, not proof of a parser error or permission to interpolate prices. The report lacks the underlying titles/images needed to explain the discrepancy. Add auditable candidate/rejection evidence and stronger exact-product/artwork checks before treating thin asks as trustworthy values. Do not impose a universal monotonic grade-price rule: differing qualifiers/variants and sparse listings can break it.

### P1: Old Japanese rows may still need identity repair

Fresh public TCGdex reads returned native **M1L-066 Exeggutor** and **M1L-073 Steelix** with their own Cardmarket references (EUR 2.19 and 2.21 trend in this snapshot). Their TCGplayer product IDs are 647175 and 647182. These are blended references with existing condition caveats, not independently verified NM sale quotes. A row with language JP but English `me01-135`/`me01-150` still needs the actual Japanese printing selected. Auto-translating an English collector number would produce the wrong card.

The public alias index contains 112 Japanese sets and the two native M1L aliases. English-name aliases and native-number selectors work in regression fixtures; coverage is not universal. A guided repair queue and missing-price diagnostics would make this much clearer.

## Images and catalogues

Fresh public data-branch probes returned schema 6, dated **2026-10-07 09:45 UTC**:

| Catalogue | Rows | DE candidates | JP candidates | Rows with any image |
| --- | ---: | ---: | ---: | ---: |
| Regional Pokémon | 4,209 | 4,023 | 151 | 681 |
| Regional One Piece | 595 | 0 | 128 | 202 |

Candidate counts are catalogue templates, not confirmed physical availability. Image counts include any language's image; they do not imply German thumbnails. German product photos often depend on successful exact-language eBay lookup. Do not copy the English box artwork into a German/Japanese product as an unlabelled identity image.

**P2: Incomplete image coverage:** TCGdex returns no native image for both M1L examples, but their product CDN image URLs return HTTP 200 image/jpeg. The first three sealed eBay image URLs from the production artifact also returned HTTP 200. Mega Gengar `mep-073` has no TCGdex image or third-party image/product ID; its constructed pokemontcg.io fallback returned HTTP 403 for both HEAD and GET from this workspace. That does not establish behavior on the user's phone. A verified exact-product image source is still needed where all current fallbacks fail; use an explicit placeholder rather than a wrong image.

**P2: Language availability UX:** One Piece's regional catalogue has no DE printed-language candidates. This must be explained instead of looking like a broken German search. A product shipped by a German shop is not a German printing. Country-based shopping is separate from this language filter.

**P2: Cache freshness:** The Pokémon name index remains cached in memory for an app session; a new daily file does not automatically refresh that in-memory list. A long-running session can miss newly indexed cards until restart. Add stamp/version-based invalidation with stale/failure status. Valid but wrong-schema daily payloads also need stronger per-file schema checks; this update handles malformed JSON and typed card-index schemas, not every possible semantically invalid feed.

## Recognition

Parser/language/slab/variant regression suites are exercised in the full Android test run. Picture-only matches and slabs require review; Japanese selection must persist the native set/number. No new recognition auto-add relaxation was made.

**P1 validation gap:** The latest recognition workflow reports success because its setup check succeeds; the photo golden-set job is skipped when the private dataset is not configured. That is not a measured recognition accuracy result. Configure the private dataset, set a meaningful exact-match threshold, and report language/printing/slab false-positive rates separately. Until then, no reliable accuracy percentage or guarantee for real phone photos is available.

**P2:** Catalogue and visual indexes still have release/language/artwork coverage limits. Cropped/glare/foil, multi-card images and low-text Japanese variants require physical-device testing and a labelled corpus. A clean parser test is insufficient evidence for camera recognition quality. Surface pre-grading remains a centering-only fallback when validated surface assessment is unavailable; this update changes its presentation, not the underlying grading capability.

## Recommended order for further work

1. Exact-price coverage diagnostics and approved sold-comparable access; investigate thin/suspicious asks with listing evidence.
2. Japanese identity repair queue and missing-price reason display; avoid cross-language substitutions.
3. Real-photo recognition dataset, meaningful accuracy gates and physical-device latency/frame tests.
4. Verified sealed/product image feeds, honest unsupported-language states, and cache-version refresh.
5. Country-specific Go Shopping pilot with delivered totals. See [the plan](GO-SHOPPING-PLAN.md); no shopping feature was implemented here.

## Verification boundaries

The new regression tests and full Android/Worker/Python checks cover the changed behavior. Screenshot inspection checks label/hint placement and a bent page frame. Physical-device animation smoothness and all possible provider/identity combinations remain unverified. Live price access was inspected through the authenticated deployment artifact; no credentials were copied out of the APK or repository secrets.
