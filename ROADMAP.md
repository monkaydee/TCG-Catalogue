# Roadmap

Ideas to take TCG Catalogue further, using only free services and libraries. Rough effort:
S = a day or less, M = a few days, L = a week or more.

## Done

- **Recognition:**
  - image-only identification (*Find by picture*, on-device, weekly picture index on the `embeddings` branch);
  - Japanese Pokémon cards (set code + TCGdex Japanese data);
  - continuous stack scan mode with undo and review.
- **Prices and collection:**
  - price history per card;
  - set checklist;
  - wishlist, trade list and sold cards with realised profit;
  - price alerts (local notifications from the daily job);
  - sealed products;
  - cloud backup and sync through the system file picker (Google Drive, Dropbox, OneDrive …, no sign-in, no server);
  - home-screen widget;
  - share a binder page.
- **Look:**
  - adaptive app icon and splash screen;
  - one spacing and shape system;
  - skeleton placeholders with fade-in;
  - card page with holo tilt and price chart;
  - onboarding;
  - empty-state illustrations;
  - haptics;
  - predictive back;
  - right-to-left layout.
- **Engineering:**
  - screenshot tests (Robolectric) in CI;
  - crash reports through a prefilled GitHub issue or the share sheet;
  - privacy policy (`docs/PRIVACY.md`).

## Later (planned)

| Idea | Why | How | Effort |
|---|---|---|---|
| Pre-grading, two paid tiers | Standard: corners, edges and centering. Pro: also surface and scratches. | On-device models (TFLite/LiteRT) built on the pre-grading repo; the model files are downloaded like the picture index. Training uploads only with explicit opt-in. | L |
| Graded prices from eBay | Today graded cards need your own value | Your own free eBay developer keys behind a free Cloudflare Worker that calls the eBay Browse API. The key stays on the Worker and never goes into the app. It shows current listings, not sold prices. | M |

## Next ideas

| Idea | Why | How (free) | Effort |
|---|---|---|---|
| Picture search for more games | Magic, Dragon Ball, Union Arena … without a readable code | Add them to `scripts/build_embeddings.py` (Scryfall and TCGCSV have images) | S |
| Japanese One Piece cards | Big part of the market | The TCGCSV index has Japanese products; ML Kit has a free Japanese text model | M |
| Shared-element motion | From a list or binder pocket to the card page | Compose `SharedTransitionLayout` | M |
| Accessibility pass | Large fonts, TalkBack | Check every screen at 200 % font size and with TalkBack | S |
| A benchmark set of real card photos | Measure recognition changes | Collect photos from volunteers (with consent), run them in a unit test | M |

## Languages

21 languages ship now. Most were machine-assisted, so native speakers should review them. Free options:

- **Weblate** (hosted, free for open-source projects) or **Crowdin** (free open-source plan). Volunteers translate in the browser; changes come back as pull requests to the `values-xx` folders.
- Add a "Help translate" link in Settings → Language.

## Publishing (free)

- **GitHub Releases**: works today, with an APK on every push.
- **F-Droid**: free, but the app must be open source with no proprietary libraries. ML Kit is proprietary, so it would need a variant without it.
- **Amazon Appstore, Samsung Galaxy Store, Huawei AppGallery**: free developer accounts.
- **Google Play**: has a one-time $25 fee, so it isn't free. Paid tiers (pre-grading) need Play Billing.
- **Before any store**, these things are needed:
  - a real signing key (not the checked-in debug key);
  - the privacy policy published as a web page (free on GitHub Pages);
  - store screenshots.
- **Trademarks**: keep game names in plain text and never use game logos in the icon or store listing.
