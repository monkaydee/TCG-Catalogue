# Roadmap

What comes next for TCG Catalogue, in the order we plan to build it. Rough effort:
S = a day or less, M = a few days, L = a week or more.

Two rules apply to everything here:

- **No API key ever goes into the APK.** Anyone can unpack an APK and copy a key, and every user would then share that key's daily limit. Keys live either on a small server (a free Cloudflare Worker) or are the user's own.
- **Free tiers of price APIs are for personal / non-commercial use.** That's fine while the app is free and in testing. Once the Play Store version earns money (pro tier), the shared server needs a paid commercial plan. That cost is small and the pro tier pays for it.

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
- **Engineering:**
  - screenshot tests in CI;
  - crash reports through GitHub issue / share;
  - privacy policy.

---

## Phase 1: Prices (raw and graded)

### The sources we looked at

| Source | What it gives | Free tier | Verdict |
|---|---|---|---|
| [PokemonPriceTracker API](https://www.pokemonpricetracker.com/api-reference) | Pokémon raw prices (TCGplayer, eBay) and **graded PSA / CGC / BGS / SGC from eBay sales**, also Japanese cards | 100 credits/day (1 credit per card, +1 for graded, +1 for history), hobby use only; paid $9.99/month for 20,000 credits/day with commercial use (that plan covers **PSA grades only**, which is fine for now) | **Best graded source for Pokémon.** Use it now with the user's own key; later through our Worker on the $9.99 plan. |
| [JustTCG](https://justtcg.com/) | 20 games incl. One Piece, Magic, Pokémon; prices **per condition and per printing** (alt arts as separate rows), from online marketplaces plus 65 game stores; graded PSA / BGS / CGC in v2 beta | 1,000 calls/month, 100/day, 20 cards per call, non-commercial only; commercial from $19/month | **Good second source**, especially for One Piece conditions and graded One Piece once v2 is stable. Same plan: own key now, Worker later. |
| [PokeTrace](https://poketrace.com/developers) | Pokémon TCGplayer, eBay, Cardmarket; graded values | Free tier is US raw prices only; EU and graded need Pro | Not useful for free; skip unless Pro becomes worth it. |
| [pokemonprice.com](https://pokemonprice.com/) | Website with raw and PSA prices, population, "PSA difficulty" | No public API | **Link only** (like the eBay/PriceCharting links today). Scraping the site isn't allowed. |
| [tcgapicompare.com](https://tcgapicompare.com/graded-card-price-api/) | A comparison page (TCGGraph, Scrydex, JustTCG) | – | Useful reading. Its advice matches ours: always show grader, grade, date and source, and never show a raw price as a graded one. |
| [dev.to article](https://dev.to/lulzasaur/building-a-trading-card-price-tracker-with-free-apis-4mkg) | TCGplayer and PSA population through RapidAPI | 50 requests/**month** shared | Far too small for an app. Skip. |
| [Ximilar card grading](https://docs.ximilar.com/collectibles/card-grading) | Cloud AI grading: corners, edges, surface, centering per side, 1–10 | Small test credits; paid per call; photos must go to their server | **Not for the app** (costs per scan, uploads users' photos). **Useful as a free reference** to check our own grader during development (see Phase 2). |
| eBay Browse API | Current eBay listings, including graded | Free with an eBay developer account (5,000 calls/day), commercial use allowed | **Graded prices for every game.** Shows asking prices, not sold prices, so it's labelled "listed at". Needs the Worker. |
| PSA Public API | Cert number → card, grade, population | Free with a PSA account (rate-limited) | **Verify a scanned slab**: the app already reads the cert number; one call confirms card and grade and shows population. |
| TCGdex / Scryfall / optcgapi / TCGCSV / Cardmarket price guide | What the app uses today | Free, no key | Stay the base for raw prices. |

### The plan

| Step | What | Effort |
|---|---|---|
| 1. "Use your own key" | Settings → Price sources: paste a free PokemonPriceTracker and/or JustTCG key (each user signs up in 1 minute, no credit card). The app then fetches graded prices only for **cards you own or open**, cached for a day, so 100 credits/day goes a long way. Fully within the free terms (personal use). | M |
| 2. Graded price display | Card page: per grader and grade (PSA 10, PSA 9, CGC 9.5 …) with source, date and number of sales. Collection value uses the graded price when there is one (otherwise your own value, as today). Never a raw price shown as graded. | M |
| 3. Free Cloudflare Worker | One small Worker (100,000 requests/day free) holding *our* keys: eBay Browse (free), PSA cert lookup (free), later PokemonPriceTracker / JustTCG (paid commercial plans). Results are cached for 24 h, so one card is fetched once per day for **all** users. Without a key of their own, users get graded prices from here. | M |
| 4. Slab verification | Scanned cert number → PSA API: confirms card and grade, shows population ("1 of 312 PSA 10"). | S |
| 5. More links | pokemonprice.com, PSA population and 130point sold search as links on the card page. | S |
| 6. Better raw prices | JustTCG per-condition prices for One Piece and the other games where TCGplayer's condition data is thin, as a second opinion when the two disagree. | S |

---

## Phase 2: Pre-grading Standard (paid): centering, corners, edges, front and back

All on the phone. No photos are uploaded and there is no cost per scan. The measurements are mostly classic image measurement, which is precise and explainable. Machine learning is only used where it clearly helps (corner and edge damage).

| Step | What | Effort |
|---|---|---|
| 1. Guided capture | Full-screen camera for the front, then the back. It checks before taking the photo: card fully inside, sharp (blur measure), no glare spot (overexposed area), card not tilted too much, enough resolution (the long side of the card ≥ 1,500 px). Plain dark background recommended, card out of the sleeve. | M |
| 2. Straighten | Find the card's four edges precisely (sub-pixel line fit on the outline; the card search from the scanner is the start) and warp to a flat, exact rectangle. | M |
| 3. Centering | Find the inner print frame on each side and measure left/right and top/bottom border widths: "Front 54/46 · 52/48, Back 61/39 · 58/42". Compare with the published PSA, BGS and CGC centering limits for each grade. Full-art cards without a visible border: "centering can't be measured". This is pure measurement and can be very exact. | M |
| 4. Corners and edges, measured | Crop the 4 corners and 4 edges of both sides. Measure whitening (light pixels on the coloured border), rounding against an ideal corner, chips and nicks along the edge line. Gives a score per corner and edge plus a zoomed picture with the problem marked. | L |
| 5. Corners and edges, learned | A tiny on-device classifier (LiteRT, ~1–2 MB) for corner and edge crops: clean / light wear / whitening / ding. Trained on our own labelled crops (the pre-grading repo's data, plus volunteers' photos with consent). Combined with step 4's measurements. | L |
| 6. Result | Subgrades for centering, corners and edges, a likely grade **range** ("most likely PSA 8–9"), a confidence level, and a clear note that surface isn't checked in Standard. No promise of a grade. | M |
| 7. Calibration | A benchmark set: cards with known grades (our own slabs, cracked-out cards, volunteers). Measure how often we're within one grade. Use Ximilar's free test credits on the same photos as a second reference. Only release when the numbers are good. | M |
| 8. Paid tier | Google Play Billing: Standard as a one-time purchase or a small subscription, with a few free checks to try it. Needs the $25 Play account. | M |
| 9. "Worth grading?" | Combines pre-grade and graded prices: expected value after grading (chance × price per grade) minus grading fee and shipping, against the raw price. | S |

**Status:** steps 1–4, 6 and a first calibration are in the app. The grade model is fitted on about 13,000 PSA-graded cards (front and back cert scans) (Hugging Face `jyesr/pokemon-tcg-grading`, see scripts/pregrade/DATASETS.md); on held-out cards it is within one grade 87 % of the time (always guessing PSA 10 would be 82 %). It sorts better and worse cards apart, but it can't tell a 9 from a 10 reliably yet, because many 9s miss the 10 on surface flaws Standard doesn't see. Next: a boosted-tree model (tested: clearly better separation), more dataset shards, the Pokémon back template for registration-based centering, a benchmark of real phone photos with known grades, then step 5 and step 8.

**Pre-grading Pro (later):** surface scratches, print lines, dents and stains need a stronger model and very good light (raking light, several photos). It builds on the pre-grading repo once Standard is calibrated.

---

## Phase 3: Machine learning (free, on-device)

| Idea | Why | How | Effort |
|---|---|---|---|
| Our own card embedding model | *Find by picture* is 82 % right on the first match today with a general image model; a model trained on card images should reach 95 %+ | Fine-tune MobileNetV3 / EfficientNet-Lite with contrastive training on the card images we already download (free GPU: Kaggle / Colab), export to LiteRT, same index pipeline | L |
| Picture search for all games | Magic, Dragon Ball, Union Arena, Weiss Schwarz, Naruto | Add their images (Scryfall, TCGCSV) to `build_embeddings.py` | S |
| Card detector | Find several cards in one photo (binder page) more reliably than the current rectangle search | Tiny on-device detector / segmentation model (e.g. YOLO-nano class, LiteRT) | M |
| Language and edition | Tell English / Japanese / German print and 1st Edition from the picture | Small classifier on the card crop | M |
| Japanese text | Read Japanese names and One Piece Japanese cards | ML Kit's free Japanese text model | S |
| Price trend hint | "Rising for 3 weeks" / "unusual spike" from the price history | Simple statistics on the stored history, no cloud | S |
| Opt-in training photos | Better models from real scans | Only with explicit opt-in, only the cropped card plus the confirmed card ID, GDPR consent text, deletable, mentioned in the Play data-safety form | M |

---

## Phase 4: Look and feel

| Idea | What | Effort |
|---|---|---|
| Logo and animated start | A proper logo (card fan / binder ring mark), animated on start: Android 12+ animated splash icon (animated vector, ≤ 1 s), then a short Compose intro (cards fan out, value counts up) on cold start only, skippable | M |
| Home dashboard | Value card with sparkline, "today's movers" (biggest gainers/losers in your collection), recently added, quick actions (scan, binder, search) | M |
| Shared-element transitions | Card flies from list / binder pocket into the card page | M |
| Card page 2.0 | Large card with tilt holo, tabs for prices / history / graded / details, one action bar | M |
| Tablet and foldable layout | Two panes (list + card), binder as a real two-page spread | M |
| Binder upgrades | Choose binder cover and page colour, pocket placeholders for missing set cards, drag cards between pockets | M |
| Accessibility pass | 200 % font size, TalkBack on every screen, contrast check | S |
| Themes gallery | Ready-made themes (Pokéball-red, Grand-Line-blue, dark gold …) without game logos | S |

---

## Phase 5: More features

| Idea | Why | Effort |
|---|---|---|
| Several collections / folders and tags | "Personal", "For sale", "Graded", "Deck" | M |
| Grading submission tracker | Cards sent to PSA/BGS/CGC: date, tier, cost, status, returned grade → updates the card | M |
| CSV import / export | Move from Collectr, TCGplayer, Dragon Shield, Cardmarket stock lists | M |
| Barcode scan for sealed products | UPC/EAN on boxes and ETBs → sealed product | S |
| Market movers | Biggest risers and fallers per game (from the daily index) | M |
| Set release calendar | Upcoming sets per game (free from TCGdex / Scryfall / optcgapi) | S |
| Trade by QR code | Show your trade list as a QR code / link; the other app shows what matches their wishlist | M |
| Insurance / value report | PDF of the collection with photos and values | S |
| Deck lists | One Piece and Pokémon decks from owned cards; what's missing and what it costs | L |

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
