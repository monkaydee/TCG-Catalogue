# Roadmap

Ideas to take TCG Catalogue further, using only free services and libraries. Rough effort:
S = a day or less, M = a few days, L = a week or more.

## 1. Recognition

| Idea | Why | How (free) | Effort |
|---|---|---|---|
| Image-only identification | Old cards without numbers, worn or Japanese cards, cards in toploaders at an angle | A small on-device image-embedding model (e.g. a MobileNet/EfficientNet-Lite TFLite model) plus an index of card-image embeddings built by the existing daily GitHub Action and served from the `data` branch, like the card index today. The scan's embedding is compared with the index (nearest neighbour). | L |
| Japanese and other-language cards | Big part of the One Piece and Pokémon market | TCGdex has Japanese, German, French … data; the One Piece index from TCGCSV has Japanese products; ML Kit has a free on-device Japanese text model | M |
| Continuous "stack" scan mode | Scanning 50 cards one after another | A running list at the bottom of the camera screen with undo per card, instead of a sheet per card | S |
| Card corners/edges check | Pre-grading estimate before sending cards to PSA | Guide the user to photograph front and back; measure the border widths (centering) from the found card rectangle — the new card search already finds the card's outline | M |

## 2. Prices and collection

| Idea | Why | How (free) | Effort |
|---|---|---|---|
| Graded prices from eBay | Today graded cards need your own value | Your own free eBay developer keys behind a free Cloudflare Worker (100,000 requests/day free) that calls the eBay Browse API: the key stays on the Worker, never in the app. Shows current listings (not sold prices — the sold-items API isn't free). | M |
| Price history per card | See trends, not just today's price | Store a small daily price row per card (the daily refresh already fetches it); TCGplayer's free history endpoint already gives 3 months | M |
| Set checklist | Collectors complete sets | "Missing cards" view per set with the card images (TCGdex/optcgapi already list every card of a set) | S |
| Wishlist, trade list, sold cards | Plan purchases, track profit | New lists in the database; realised profit when a card is marked as sold | M |
| Price alerts | "Tell me when this card goes above 100 €" | Local notifications from the existing daily background job — no server needed | S |
| Sealed products | Booster boxes and ETBs are part of many collections | TCGCSV lists sealed products with prices in the same categories | M |
| Free cloud backup and sync | Don't lose the collection, use two phones | Google Drive's hidden app folder (free, the user's own Drive; needs a free Google Cloud project for sign-in) | M |
| Home-screen widget | Portfolio value at a glance | Jetpack Glance widget | S |
| Share a binder page | Show off a page on social media | Render the page to an image and use Android's share sheet | S |

## 3. A cleaner, more professional look

Done in this version: light/dark/system theme, colour themes and own colours per area, own background pictures, grouped settings, bolder headings, the value card on the accent colour.

Next steps:

1. **App icon and splash screen.** A real adaptive icon (foreground card shape, background colour) and the Android 12 splash screen API. *S*
2. **One spacing and shape system.** 4/8/12/16/24 dp spacing, one corner radius for panels (16 dp) and one for card images (6 % of width), used everywhere. *S*
3. **Loading states.** Grey "skeleton" placeholders in the card's shape instead of spinners, and a short fade-in for card images. *S*
4. **Card detail polish.** A large card image with a soft shadow and a holo shimmer that follows the phone's tilt (gyroscope), the price as a small chart, and actions in one bottom bar. *M*
5. **Motion.** A shared-element transition from a list or binder pocket to the card page, and predictive back (Android 14+). *M*
6. **Onboarding.** Three short screens: scan, binder, prices. They also explain the camera permission before Android asks. *S*
7. **Empty states and illustrations.** Simple vector illustrations for an empty collection, no search results and offline. *S*
8. **Haptics.** A light tick when a card is recognised or a binder page lands. *S*
9. **Accessibility check.** Large font sizes, TalkBack labels (already in resources) and right-to-left layout for Arabic. *S*

## 4. Languages

21 languages ship now. Most were machine-assisted, so native speakers should review them. Free options:

- **Weblate** (hosted, free for open-source projects) or **Crowdin** (free open-source plan). Volunteers translate in the browser; changes come back as pull requests to the `values-xx` folders.
- Add a "Help translate" link in Settings → Language.

## 5. Publishing (free)

- **GitHub Releases**: works today, with an APK on every push.
- **F-Droid**: free, but the app must be open source with no proprietary libraries. ML Kit is proprietary, so it would need a variant without it.
- **Amazon Appstore, Samsung Galaxy Store, Huawei AppGallery**: free developer accounts.
- **Google Play**: has a one-time $25 fee, so it isn't free.
- **Before any store**, three things are needed:
  - a real signing key (not the checked-in debug key);
  - a privacy policy page (free on GitHub Pages);
  - store screenshots.
- **Trademarks**: keep game names in plain text and never use game logos in the icon or store listing.

## 6. Engineering

- **Screenshot tests** (Roborazzi/Robolectric, free) in CI, so visual changes are reviewed as images.
- **Crash reports** without a paid service: ACRA, sending reports by e-mail or to a free endpoint.
- **A benchmark set of real card photos**, to measure recognition changes the way the alt-art matcher was tuned.
