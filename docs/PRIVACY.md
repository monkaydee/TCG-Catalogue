# Privacy policy — TCG Catalogue

_Last updated: 3 October 2026_

TCG Catalogue is an app to scan, catalogue and price trading cards. It is built so that your
collection stays yours.

## What the app stores

- **Your collection** (cards, sealed products, wishlist, sold cards, price history, settings) is
  stored **only on your phone**. There is no account and no server of ours.
- If you turn on **cloud backup**, the app writes a backup file to the place *you* pick with
  Android's file picker (for example your own Google Drive, Dropbox or OneDrive). Only you have
  access to that file; we never see it.
- Pictures you choose as **background pictures** are copied into the app's private storage.

## Camera and photos

- The camera and the photos you import are used **on the phone only**, to read the card number
  (Google ML Kit text recognition runs on the device) and to compare the card with card images.
- Camera pictures and photos are **not uploaded** anywhere.

## Network requests

To look up cards and prices the app contacts these free public services. They receive the card
number, set or name being looked up (never your collection or photos) and, like any web request,
your IP address:

- TCGdex (api.tcgdex.net, assets.tcgdex.net) — Pokémon cards and prices
- OPTCG API (optcgapi.com) — One Piece cards and prices
- Scryfall (api.scryfall.com) — Magic cards and prices
- TCGplayer (tcgplayer-cdn.tcgplayer.com, infinite-api.tcgplayer.com) — card images, prices by condition
- GitHub (raw.githubusercontent.com) — the daily card and sealed-product lists
- Frankfurter (api.frankfurter.dev) — exchange rates

When you tap a price link, your browser opens eBay, PriceCharting, Cardmarket or TCGplayer; their
own privacy policies apply there.

## Crash reports

If the app crashes, it keeps the technical error on the phone and asks you at the next start
whether to report it. A report contains the app version, Android version, phone model and the
error's stack trace — no collection data. It is only sent if you choose to (as a GitHub issue
you can read before submitting, or through the share sheet).

## Analytics and ads

None. The app has no analytics, no tracking and no advertising.

## Children

The app does not knowingly collect any personal data from anyone, including children.

## Contact

Questions: open an issue at https://github.com/monkaydee/TCG-Catalogue/issues
