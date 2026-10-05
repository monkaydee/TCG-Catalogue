# Roadmap

What comes next for CardNavo, in the order we plan to build it. Rough effort:
S = a day or less, M = a few days, L = a week or more.

Two rules apply to everything here:

- **No API key ever goes into the APK.** Anyone can unpack an APK and copy a key, and every user would then share that key's daily limit. Keys live either on a small server (a free Cloudflare Worker) or are the user's own.
- **Free tiers of price APIs are for personal / non-commercial use.** That's fine while the app is free and in testing. Once the Play Store version earns money (pro tier), the shared server needs a paid commercial plan. That cost is small and the pro tier pays for it.

## Audit update — 5 October 2026

See [the audit and feature proposals](docs/RECOGNITION-PRICING-AUDIT.md) for evidence, sources and remaining limits. This update takes precedence over older “Done” claims below: shipped does not mean reliably validated.

### P0 — corrections implemented locally, awaiting device/provider validation
- Match language and printing explicitly; missing prices stay unknown. Slabs must never inherit raw-card prices. Keep BGS Black Label and CGC Pristine/Perfect separate from ordinary 10s.
- Merge graded-provider coverage instead of stopping after the first company has a result; count both eBay searches against the budget.
- Apply the printed-name check to camera scans, preserve language and grade through photo fallback, require consecutive camera matches and review slabs before bulk/automatic add.
- Capture full-resolution pre-grading photos. Reject photo-boundary edges and implausible shapes; withhold estimates for unresolved partial/artwork outlines, guard tiny-image checks, and use PSA's current 55/45 front centering threshold for 10. Do not show the Pokémon model's estimates for other games or incomplete/unusable measurements.
- Use current company label styles for PSA, BGS, CGC and SGC; add screenshot coverage. Historical label versions and remaining companies require references.

### P1 — validation and data coverage
- Build a consented, labeled camera/photo set across games, languages, printings and slab companies. Measure top-1 accuracy, correct candidate recall and incorrect auto-add rate separately. Bundled Chinese/Japanese/Korean recognizers are implemented; measure device runtime and recognition quality.
- Validate real provider responses and pricing access in the deployed Worker. Browse API asking prices are not completed sales; preserve that distinction. Prefer supported licensed sources for exact language/printing/grade data.
- Calibrate pre-grading on phone photos with verified grades; split evaluation by card/certificate and capture source. Add reference-aware border detection and glare checks so printed white regions and artwork do not count as damage. Until calibrated, retain measurements and cautious estimates only for supported usable inputs.

### Authorized and implemented — validate in this release
1. **Real PnL and grading-cost ledger:** per-copy purchase cost and original currency, manually entered grading fee/shipping/tax, current exact-print raw/graded valuation, realized proceeds/fees. Unrealized PnL today = today's value − purchase cost − grading costs; today's movement is a separate number. Missing costs/prices remain unknown, not zero. Quantity, split sales and currency conversions must preserve basis. Implemented with separate cost lots and transactional FIFO sales.
2. **Price evidence panel:** date, asking vs sold, matching language/printing/grade, sample count and range; allow price challenges. Implemented; see docs/COLLECTION-TOOLS.md.
3. **Recognition review queue:** show read number/set/language and reasons for the best candidates; retain unfinished imports for correction. Implemented; see docs/COLLECTION-TOOLS.md.
4. **Grading submission tracker:** status and actual costs per card, linked to the PnL ledger. Do not present expected grading returns until the pre-grader is calibrated. Implemented; see docs/COLLECTION-TOOLS.md.
5. **Insurance/export report:** photos, certificate, basis and dated valuation evidence; work offline where possible. Implemented; see docs/COLLECTION-TOOLS.md.

### Shared recognition learning
Implemented separate default-off consent, reviewed rectified card crops, private D1/R2 reporting,
rate/storage limits, deletion/retention and moderated daily hints. Automated additions do not count
as human confirmations. Three installation IDs flag a candidate for manual review, not automatic
publication. See [Shared learning](docs/SHARED-LEARNING.md) for deployment and moderation setup.
Training new visual or grading weights remains gated on curated labels and held-out evaluation.

### Release policy
Every push continues to publish `v0.1.<GitHub build number>` with the signed update-compatible APK.
Publication failures now fail the workflow. Server tests and Android lint run before release.
Production Worker deployment is restricted to the existing app branch and main.

## Done

- **Recognition:**
  - image-only identification for all games (*Find by picture*: on-device, weekly picture index on the `embeddings` branch), with card outline search in photos and automatic fallback when no number is read;
  - Japanese Union Arena codes (UA…) matched to the English prints;
  - Japanese Pokémon cards;
  - stack scan mode.
- **Prices and collection:**
  - price history per card;
  - set checklist;
  - wishlist, trade list and sold cards;
  - price alerts;
  - sealed products;
  - cloud backup and sync without sign-in;
  - home-screen widget;
  - share a binder page.
- **Look:**
  - adaptive icon and splash screen;
  - spacing system;
  - skeleton loading;
  - holo card page with price chart;
  - onboarding;
  - empty states;
  - haptics;
  - predictive back;
  - right-to-left layout.
- **Price server:** free Cloudflare Worker with the provider keys, shared cache and per-provider budgets (`worker/`, docs/CLOUDFLARE.md); graded prices on the card page and in the collection value; PSA cert check; optional online identification (Ximilar).
- **Pre-grader (Standard, first version):** front and back photo (camera with card guide or gallery), card found and straightened on the phone, centering against PSA / BGS / CGC limits, corner and edge wear per zone, a likely PSA range calibrated on PSA scans of graded cards (scripts/pregrade/). Free for now.
- **Since the first test round (October 2026):**
  - eBay prices through the price server: graded for every company (PSA, BGS, CGC, SGC, TAG, ACE, AOG, GSG; Pristine / Black Label kept apart), raw for printings the databases don't price (1st Edition), per printing;
  - card language: read from the card, editable when adding / editing, stored per copy, priced from eBay in that language (ebay.de / .fr / .it / .es / .nl, local card name);
  - PSA slab labels without logo text, Black Star promos (SVP, MEP, SWSH, SM, XY, BW), name search in the card's language with number check, picture search limited to the printed game;
  - pre-grader: card found by its own edges, spirit level, automatic photo when level;
  - set checklist "+1 per tap" with Undo.
- **Engineering:**
  - screenshot tests in CI;
  - crash reports through GitHub issue / share;
  - privacy policy.

---

## How we make recognition dependable

Recognition can't be "guaranteed" by more rules alone: every rule that fixes one card can break another (this happened once with the Ogerpon promo). What makes it dependable is **measuring it on real cards on every build** and **never adding a card silently when the app isn't sure**. Every item below serves one of three goals:

1. **Measure:** a growing set of real photos with the right answer, checked automatically on every build. A release only goes out when the score doesn't drop.
2. **Learn from every miss:** "Wrong card?" in the app turns a miss into a new test case in one tap.
3. **Be honest:** each match gets a confidence; below it the app asks instead of guessing, and says why ("number not readable", "name doesn't match").

---

## P0 status (October 2026)

| # | Item | Status |
|---|---|---|
| 1–2 | Golden set + recognition check in CI | **Done**: shared `PhotoIdentifier`, `GoldenSetTest`, `recognition.yml`; waiting for the photos in a private repo (docs/GOLDEN_SET.md) |
| 3 | "Wrong card?" → test case | **Done**: corrections are kept automatically, confirmed imports optionally; export in Settings |
| 4 | One scoring for all signals | **Partly**: number results are checked against the printed name (all languages), contradictions and picture / new-card finds never auto-add. A single score formula follows once the golden set measures it. |
| 5 | Offline name index in all languages | **Done** for Pokémon (EN/DE/FR/IT/ES/PT, daily) |
| 6 | Newest cards on day one | **Done** for Pokémon (TCGplayer list of promos and the last 180 days) |
| 7 | Small print reading | **Done**: enlarged bottom-strip OCR when no number is read |
| 8 | Debug view | **Done**: long-press an imported photo |
| 9 | Price confidence | **Done** for eBay prices (listings, range, date) |
| 10 | Language price coverage | **Waiting**: Cardmarket API needs a seller account (owner); eBay by language in use |

## P0: Right card, right price (now)

| # | What | Why / how | Effort |
|---|---|---|---|
| 1 | **Recognition test set ("golden set")** | 200+ real photos (our own cards, raw / sleeve / toploader / slab, all languages, glare, angles) with the right card id, printing, language and grade. Stored in a private test-data folder, not in the APK. | M |
| 2 | **Recognition check in CI** | An Android emulator job runs the real pipeline (ML Kit text, number, name, picture) on the golden set and reports exact-match rate per game / language / condition. Fails the build when it drops. Same for the camera scan and photo import, which must share one pipeline. | M |
| 3 | **"Wrong card?" report** | One tap on any result: pick the right card; the cropped photo, the text read and the right answer are saved (and, if the user agrees, sent as a GitHub issue without personal data). Each report becomes a golden-set case. | M |
| 4 | **One scoring for all signals** | Number (with promo / TG / GG formats), printed name in every language, set code, language, picture similarity and HP each add evidence; contradictions subtract (number says Masquerain, name says Ogerpon). The best card needs a clear lead to be added without asking. Replaces today's chain of fallbacks. | M |
| 5 | **Offline name index in all languages** | Daily index of every card's name in EN / DE / FR / IT / ES / PT / JA (TCGdex, Scryfall, optcgapi, TCGCSV) on the data branch, fuzzy-matched on the phone. Faster than live searches, works offline, covers names like "Riesenzahn", "Glurak". | M |
| 6 | **Newest cards on day one** | Newest promos (e.g. MEP 091) are missing in TCGdex for a while. Fall back to TCGplayer's product list (TCGCSV, daily) and pokemontcg.io; mark "new card, details may be incomplete". | S |
| 7 | **Small print reading** | Read the bottom line again from a 2–3× zoomed crop of the card (number, set code, language code); several camera frames combined; glare spots detected and the user asked to tilt. Makes "SVP DE 123" readable behind toploaders. | M |
| 8 | **Debug view** | Long-press a result: the text read, the signals and scores, why this card. For us and for testers. | S |
| 9 | **Price confidence** | Every price shows source, date, number of listings and a low–high range; language and graded prices say "asking prices on eBay". Collection value says how much of it is estimated. | S |
| 10 | **Language price coverage** | Cardmarket offers per language and condition through its official API (needs a seller account); otherwise eBay sold listings where the API allows. Until then: eBay asking prices per language, as today. | M |

## P1: Make it fast and pleasant

| # | What | Why / how | Effort |
|---|---|---|---|
| 11 | **Live camera scan like Collectr** | Card outline drawn live, auto-capture when sharp and steady, result in under 1 s, continuous mode for stacks with a running list and sound / vibration per card. | M |
| 12 | **Performance** | Picture index and name index loaded once in memory (memory-mapped), OCR on a cropped card instead of the full photo, prices fetched in batches of 20, images cached in two sizes. Goal: scan → result < 800 ms on a mid-range phone, app start < 1 s. | M |
| 13 | **Import review screen** ("add all sure ones" and sure / check marks done) | All imported photos in one list with confidence colours; "add all sure ones" button; unsure ones side by side with the photo. | S |
| 14 | **Edit everything in one place** | Card page: language, printing, condition, grade, cert, price override, notes, purchase date, all inline. | S |
| 15 | **Pre-grader reliability** | Golden set of our own phone photos with known grades; outline accuracy test in CI; centering for full-art fronts via the printed frame of the set's template; One Piece / other games calibrated separately. | L |
| 16 | **Offline mode** | Everything except prices works offline; prices refresh when back online, with their age shown. | S |

## P2: More features

| # | What | Why | Effort |
|---|---|---|---|
| 17 | **Several collections / folders and tags** | "Personal", "For sale", "Graded", "Deck" | M |
| 18 | **CSV import / export** | Move from Collectr, TCGplayer, Dragon Shield, Cardmarket stock lists | M |
| 19 | **Grading submission tracker** | Sent to PSA / BGS / CGC: date, tier, cost, status, returned grade updates the card | M |
| 20 | **"Worth grading?"** | Pre-grade chances × graded prices − fees vs. raw price | S |
| 21 | **Market movers and price trend hints** | Biggest risers / fallers per game and in your collection; "rising for 3 weeks" | M |
| 22 | **Barcode scan for sealed products** | UPC / EAN on boxes and ETBs | S |
| 23 | **Trade by QR code** | Your trade list as a QR; the other app shows what matches their wishlist | M |
| 24 | **Set release calendar** | Upcoming sets per game | S |
| 25 | **Insurance / value report (PDF)** | Collection with photos and values | S |
| 26 | **Deck lists** | Decks from owned cards; what's missing and what it costs | L |

## P3: Look and machine learning (later)

| # | What | Effort |
|---|---|---|
| 27 | Own card embedding model (fine-tuned on card images; picture search from ~80 % to 95 %+ first-match) | L |
| 28 | Card detector for binder pages (several cards per photo) | M |
| 29 | Language / edition classifier from the picture (backs up the text) | M |
| 30 | Logo and animated start, home dashboard with movers, shared-element transitions, card page 2.0 | M |
| 31 | Tablet / foldable layout, binder upgrades, themes gallery, accessibility pass | M |
| 32 | Pre-grading Pro: surface, scratches, dents, print lines (raking light, several photos) | L |
| 33 | Paid tier through Google Play Billing | M |

### Price sources (reference)

| Source | Used for | Status |
|---|---|---|
| TCGdex / Cardmarket price guide, TCGplayer (TCGCSV), Scryfall, optcgapi | raw prices (English / general print) | in use |
| JustTCG, TCG API, PokeTrace, RapidAPI TCGplayer | raw prices by condition, through the price server within free budgets | in use |
| eBay Browse API | graded (all companies) and language prices, asking prices | in use |
| PSA Public API | cert check, population | in use |
| PokemonPriceTracker ($9.99, PSA grades only) | Pokémon graded sold prices | when the paid plan is bought |
| Cardmarket API (seller account) | per-language and per-condition offers | to apply for |

---

## Languages

21 languages ship now. Most were machine-assisted, so native speakers should review them before a store release:

- **Weblate** or **Crowdin**: both are free for open-source projects;
- a "Help translate" link in Settings → Language.

## Publishing

- **GitHub Releases**: works today, with an APK on every push.
- **Google Play**: a one-time $25 fee. Needed for the paid pre-grading tier (Play Billing).
- **Before the store**, these things are needed:
  - a real signing key;
  - the privacy policy as a web page (GitHub Pages);
  - store screenshots;
  - the data-safety form.
- **Other stores**: Amazon Appstore, Samsung Galaxy Store and Huawei AppGallery are free to publish on. F-Droid would need a variant without ML Kit.
- **Trademarks**: game names only in plain text; no game logos in the icon or listing.

### P0 again: price and language bugs (reported 5 Oct 2026, fix first)
- **Graded cards show the raw price:** the card value must use the graded price (grader + grade) whenever one exists; check list, card page and portfolio total.
- **Other languages show the English raw price:** use the language price (eBay DE/FR/IT/ES, Cardmarket) and show "no price in this language yet" instead of silently falling back to English.
- **Language detection is unreliable:** collect wrong cases via "Wrong card?", add more rule words per language, weigh the set code.
- **Raw prices too low for EU (e.g. Zekrom LTR 115 gold ≈ 160 € vs. NM from 300 € on eBay):** TCGplayer US market is not the EU price. Combine sources: Cardmarket trend for EU users, eBay listings as a cross-check; when sources differ by more than 30 %, show the range and the sources.

### P1: Learning from every user (text and card pictures) — scope agreed, not built
- Separate default-off text and photo consent, with a clear preview of the card crop. Cover camera scans, imports and manually corrected searches. Manual searches without a photo send no invented image.
- Send OCR text, original suggestion, confirmed card/printing/language, optional grade context and an opt-in crop (maximum 800 px, EXIF removed). Redact non-card text and background. Never include account/email/location. A random install identifier is pseudonymous, not “fully anonymous”; document its purpose and rotation/retention.
- Store text in D1 and crops in private R2 through the Worker; validate payload size/content, rate-limit and provide retention/deletion controls. R2 has a free tier, not an unlimited storage guarantee. An APK app key is extractable and is not sufficient anti-abuse protection.
- At least three independent install reports are an initial review signal, not proof of three independent users. Deduplicate repeated submissions, require game/set/number/language/printing context, detect conflicts and review rules before promotion. Start with candidate-ranking hints; do not let unreviewed votes override a stronger identifier or trigger auto-add.
- Publish signed/versioned rules with rollback and a held-out regression evaluation before rollout. Daily download is possible; publication depends on sufficient reviewed evidence.
- Retrain models only with consented, reviewed, licensed data and a measured improvement. Split by card/certificate/capture source and exclude training photos from the evaluation set. Monthly retraining is an operational option, not an automatic learning guarantee.
- Outline corrections may later improve the pre-grader; grading-model training additionally needs verified grades and standardized captures. Price reports feed a review queue, not direct price changes.
- Implementation phases: schema/consent/crop review → private ingestion and abuse controls → moderation and export → evaluated rule distribution → model training. This is several work sessions with testing, not a dependable one-session estimate.

### More ideas
- **Set completion helpers:** "missing cards from this set" with total cost to complete; buy links to Cardmarket/eBay (affiliate later).
- **Duplicate finder:** cards owned several times → trade list in one tap.
- **Collection insurance export:** PDF with photos, values and totals.
- **Grading tracker:** cards sent to PSA/BGS/CGC with submission number, status and return date.
- **Sealed product tracker:** booster boxes/ETBs with price history (already started) and opening log (what was pulled).
- **Widgets:** portfolio value, card of the day, biggest mover.
- **Search everywhere:** one search across collection, wishlist and all cards, by name in any language.
- **Accessibility:** larger text option, screen reader labels for card images, colour-blind safe gain/loss colours.

## Next ideas (October 2026), by priority

### Now (after P1 live scan)
- **Pre-grader:** fix the "photo is blurry" false alarm on preview frames (threshold per resolution); take 3 frames and keep the sharpest; collect outline corrections ("Adjust corners") as test cases for every game.
- **Startup animation:** use the brand pack's animated logo (animated-vector drawable) in the Android 12+ splash screen with a fade into Home; static logo below Android 12. No delay added: the animation ends as soon as data is ready.
- **Card page:** price history chart per grade (raw, PSA 9, PSA 10), language switch with its own prices, "set complete x/y" link.

### Soon (features)
- **Portfolio:** profit/loss vs. purchase price, top movers of the week, value by game and by set.
- **Price alerts** as notifications (target price reached, +/- 20 % in a week).
- **Binder view:** 3x3 / 4x3 pages like a real binder, drag to reorder, share as an image.
- **Trade helper:** two collections side by side, fair-trade total in EUR.
- **Grading helper:** "worth grading?" = PSA 10 price x chance from the pre-grader minus grading fee and shipping.
- **Export / import** CSV (Collectr, Cardmarket, TCGplayer formats).

### Design
- One consistent CardNavo look: brand teal accents, card-shaped skeletons while loading, larger card images, haptic feedback on add.
- Home: value hero with sparkline, quick actions (scan, import, pre-grade), recent cards row.
- Dark/light/AMOLED themes; tablet layout with two panes.

### Later
- On-device ML model for card recognition (works offline, < 300 ms).
- Surface check in the pre-grader (scratches with angled light, two photos).
- Wishlist sharing link, friends' collections, set-completion badges.
