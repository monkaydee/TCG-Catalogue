# TCG Catalogue

An Android app to catalogue **Pokémon** and **One Piece** trading cards: scan a card with the
camera, and the app identifies it, looks up its market price, files it under its set and
tracks the value of your whole collection over time.

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
- **Quick add**: scan a stack of cards quickly. Cards that are clearly recognised are added
  without asking.
- **Manual add**: search Pokémon by name or number, One Piece by code.
- **Collection**: portfolio value, value chart (1M / 3M / 1Y / All), most valuable cards, and
  every set with its value, copy count and completion (`12/165`).
- **Set view**: owned cards sorted by value or by number.
- **Prices**: Pokémon from Cardmarket (EUR, trend) or TCGplayer (USD, market). Choose
  in Settings. One Piece from TCGplayer. Displayed in EUR or USD using ECB rates. Prices
  refresh daily in the background, which also records the portfolio history.
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
| Exchange rates     | [Frankfurter](https://frankfurter.dev) (ECB)        |

## Building locally

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew testDebugUnitTest   # parser unit tests
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Notes and limitations

- APKs are signed with the key in `app/signing/debug.keystore`. It's checked in so that CI builds
  can update each other. Don't publish the app to the Play Store with this key.
- Prices are for raw (ungraded) cards.
- Recognition relies on the printed number. Very old Pokémon cards without numbers, and cards
  that are badly worn or shot at an angle, may need the manual search.
- Free community APIs are used. If one goes down or changes, lookups for that game fail until
  it recovers or the client in `data/remote/` is adapted.
