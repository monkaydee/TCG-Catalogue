# TCG Catalogue

An Android app to catalogue trading cards: **Pokémon, One Piece, Magic: The Gathering,
Dragon Ball Super (Masters and Fusion World), Union Arena, Weiss Schwarz and Naruto**. Scan a
card (raw or graded) with the camera or from a photo; the app identifies it, looks up its market
price, files it under its set and tracks the value of your whole collection over time.

## Install

1. Open the [latest release](../../releases) on your phone.
2. Download `tcg-catalogue-N.apk` and open it. Android asks you to allow installing apps from
   your browser the first time.
3. New builds install over the old one and keep your collection.

Every push to this repository builds a new APK with GitHub Actions (`.github/workflows/build.yml`).

## Features

- **Scan**: point the camera at a card. The app reads the collector number with on-device OCR
  (Google ML Kit) and looks the card up:
  - Pokémon: `025/165`, `TG05/TG30`, `199/165` (secret rares). The set is narrowed down
    by its printed size, and the name read from the card picks the right one when several
    sets have the same size. If it's still unclear, you choose from the matches with images.
  - One Piece: `OP05-060`, `ST01-001`, `EB01-012`, `PRB01-001`, `P-001`, including all
    alternate arts / SP / manga printings as separate choices.
  - Magic: the set code and collector number at the bottom left (`DMU • EN`, `0107 M`);
    falls back to the card name.
  - Dragon Ball Fusion World / Super, Union Arena, Weiss Schwarz, Naruto: the card code
    (`FB01-139`, `BT1-031`, `UE01BT/BLC-1-001`, `HOL/W91-E001`), matched against a daily card
    index. English prints only (the index comes from TCGplayer).
- **Graded cards**: the slab label is read too. That covers PSA, BGS/Beckett (including Black Label),
  CGC (including Pristine), SGC, TAG, ACE, AOG, GSG and PI, plus the grade and cert number.
  Graded copies are priced from PriceCharting's sold listings for that company and grade. If
  PriceCharting has no sales for that slab, the app uses the average of the last 5 eBay sales of
  the same card, company and grade (eBay.de for EUR, eBay.com for USD). It skips lots,
  other grades and Japanese copies. You can also set or correct the company, grade and cert by hand.
- **Import photos**: took pictures while you were out? Pick them from the gallery (Scan →
  *From photos*, or the photo icon on the Collection screen), or share them to TCG Catalogue
  from any app. Each photo is read on the phone, tried in all four orientations, and can contain
  several cards (e.g. a binder page). You review the matches or add them all at once.
- **Quick add**: scan a stack of cards quickly. Cards that are clearly recognised are added
  without asking.
- **Manual add**: search Pokémon by name or number, One Piece by code.
- **Collection**: portfolio value, value chart (1M / 3M / 1Y / All), most valuable cards, and
  every set with its value, copy count and completion (`12/165`).
- **Set view**: owned cards sorted by value or by number.
- **Virtual slabs**: graded cards are shown inside a slab with their company's label (PSA, BGS
  incl. gold and Black Label, CGC, SGC, TAG, ACE, …), the grade words (GEM MT 10, MINT 9, …) and
  the cert number, so graded and raw cards are told apart at a glance.
- **All prices**: the card page's "All prices" panel shows every number the sources publish for
  that printing: Cardmarket (trend, lowest offer, 1/7/30-day averages, or the chosen One Piece
  listing), TCGplayer (market, lowest listing …), TCGplayer sales per condition, PriceCharting raw
  and graded prices, and for slabs the eBay sold average. A source that can't be reached says why.
- **Edit cards**: the pencil on a card's page reopens the add sheet with everything filled in:
  printing, Cardmarket listing, condition, raw/graded with company, grade and cert, quantity and
  purchase price (the card page then shows the gain or loss). "Wrong card?" searches for the right
  card and swaps it in, keeping quantity, grade and purchase price.
- **Full screen**: status and navigation bars are hidden (swipe from the edge to show them);
  can be turned off in Settings.
- **Card view**: swipe left/right to go through the cards in the order of the list you opened
  them from (a set, or your whole collection by value from "Most valuable").
- **Prices**: Pokémon and Magic from Cardmarket (EUR, trend) or TCGplayer (USD, market). Choose
  in Settings. All other games use TCGplayer. When Cardmarket and TCGplayer disagree by more than 3×,
  one of them is linked to the wrong card (e.g. TCGdex gives the gold Zekrom 115/113 the price of the
  regular Zekrom). The app then uses the other market and shows a note on the card. Displayed in EUR or USD using ECB rates. Prices
  refresh daily in the background, which also records the portfolio history.
- **One Piece on Cardmarket**: Cardmarket lists every print of a card separately (original,
  reprints in other sets, alt arts, promos), e.g. 16 listings for Brannew OP03-089. With Cardmarket
  as the price source you pick the exact listing ("The Best · V.3") when adding or later on the card
  page. The prices come from Cardmarket's public price guide via the daily index
  (`CARDMARKET_ONE_PIECE.json`).
- **Misread numbers**: after a scan, the app checks that the card the number points to has the
  name printed on the scanned card. If not, it tries look-alike numbers (6/8, 1/7, 3/8 …) and warns
  you instead of adding the wrong card automatically.
- **Prices by condition**: NM, LP, MP, HP and DMG copies are priced separately, from TCGplayer's
  market prices per condition (its sales history):
  - TCGplayer as the source: the real market price for that condition.
  - Cardmarket as the source: Cardmarket has no per-condition prices, so its price is multiplied
    by TCGplayer's ratio for the same card (e.g. LP = 66 % of NM).
  - No sales for a condition: a typical discount (LP 85 %, MP 70 %, HP 50 %, DMG 35 %), marked as
    an estimate.
  - A worse condition is never valued above a better one.
- **Printing, condition, quantity** per card (normal / holo / reverse / 1st edition;
  NM–DMG).
- **Backup**: export / import the collection as JSON. Data lives only on the phone.

## How it works

```
CameraX frame ──▶ ML Kit text recognition (on-device)
                    │
                    ▼
              CardTextParser  ── "OP05-060" / "025/165" + name guess
                    │  (same reading in 2 frames in a row)
                    ▼
              CardRepository.resolve()
                ├─ Pokémon:   TCGdex  /sets (match printed total) → /sets/{set}/{number}
                └─ One Piece: optcgapi /sets|decks|promos/card/{code}
                    │
                    ▼
              Room database (owned cards, sets, daily snapshots)
                    ▲
              WorkManager daily job: refresh prices + FX, write snapshot
```

| Concern            | Library / service                                   |
|--------------------|-----------------------------------------------------|
| UI                 | Jetpack Compose, Material 3, Navigation             |
| Camera / OCR       | CameraX, ML Kit Text Recognition (bundled model)    |
| Storage            | Room, DataStore                                     |
| Networking         | OkHttp, kotlinx.serialization                       |
| Images             | Coil                                                |
| Background refresh | WorkManager                                         |
| Pokémon data       | [TCGdex](https://tcgdex.dev) (Cardmarket + TCGplayer prices) |
| One Piece data     | [OPTCG API](https://optcgapi.com) (TCGplayer prices) |
| Magic data         | [Scryfall](https://scryfall.com) (Cardmarket + TCGplayer prices) |
| Other games        | [TCGCSV](https://tcgcsv.com) (TCGplayer) → daily index on the `data` branch |
| Graded prices      | [PriceCharting](https://www.pricecharting.com) sold listings, eBay sold listings as fallback |
| Exchange rates     | [Frankfurter](https://frankfurter.dev) (ECB)        |

## Card index

`scripts/build_card_index.py` downloads every set of the indexed games from TCGCSV and writes one
compact JSON file per game (`{"groups": …, "cards": [[code, name, set, rarity, productId, prices]]}`).
`.github/workflows/card-index.yml` runs it every day and force-pushes the files to the `data`
branch. The app downloads the files of the enabled games from there, at most once a day.

## Building locally

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew testDebugUnitTest   # parser unit tests
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Notes and limitations

- APKs are signed with the key in `app/signing/debug.keystore`. It's checked in so that CI builds
  can update each other. Don't publish the app to the Play Store with this key.
- eBay and (if it blocks plain requests) PriceCharting are read through an invisible in-app
  browser (WebView), the way a person would open the page. eBay blocks data-centre servers, so this
  only works on the phone.
- Graded prices come from PriceCharting's public card pages. Smaller graders (AOG, GSG, PI, …)
  have no separate 10 prices there, so their 10s are estimated from the general Grade 9.5 price,
  and the card says so.
- Recognition relies on the printed number. Very old Pokémon cards without numbers, and cards
  that are badly worn or shot at an angle, may need the manual search.
- Free community APIs are used. If one goes down or changes, lookups for that game fail until
  it recovers or the client in `data/remote/` is adapted.
