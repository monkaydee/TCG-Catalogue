# CardNavo Pro (paid tier)

Added 11 November 2026. One purchase through Google Play unlocks the collector tools for good.
The price is set in the Play Console, never in the app; the app shows whatever Play returns.

## What is free and what is Pro

| Free | Pro |
| --- | --- |
| Scanning, photo import, find by picture | Collection and insurance reports (CSV, PDF) |
| All prices, graded prices, price history, alerts | Import from other apps and lists |
| Collection, sets, checklists, wishlist, trade list, sold, sealed | Deck builder |
| Main binder and one own binder | More than one own binder |
| Cost ledger, PnL, grading tracker, lot calculator | "Worth grading?" and PSA population |
| Backups, cloud sync, widgets, themes | Cost to complete a set |
| Pre-grading (experimental) | |

Rules behind the split:

- **A user's data is never locked.** Backup, export of the backup file and everything already stored stay free.
- **Pre-grading is not sold.** It is uncalibrated (docs/PREGRADING-VALIDATION.md); selling its estimates would be
  selling a claim the app cannot back. Move it to Pro only after the validation in that document passes.
- The list lives in one place: `ProFeature` in `data/Pro.kt`. Each gate asks `Pro.state.allows(feature)`.

## When the paywall is on

Only in a build made with the environment variable `PRO_PAYWALL=true` (`BuildConfig.PRO_PAYWALL`).
That is the Play Store bundle from `.github/workflows/play.yml`. Every other build, including the APKs
`build.yml` publishes on GitHub, leaves every tool open and shows no Pro page in Settings.

Consequence: while the GitHub APKs stay public, anyone can install the unlocked build. Before the Play
launch, decide whether to make the repository private or stop attaching APKs to public releases.

## Code

- `data/Pro.kt`: the feature list, the state shown to the UI and `Pro.resolve`, the pure rule that turns
  the store's answer into owned / pending / to acknowledge. Tested in `ProTest`.
- `billing/ProBilling.kt`: Play Billing 9 client. Product `cardnavo_pro`, one-time, non-consumable.
  Pending payments (cash, bank transfer) do not unlock. Purchases are acknowledged, otherwise Google refunds
  them after three days. When Play answers, its list is the truth, so a refund locks Pro again; when Play
  cannot be reached, the last known answer stands, so Pro works offline.
- `ui/screens/ProScreen.kt`: the Pro page, `ProLock` (the button shown in place of a locked tool) and
  `LocalOpenPro`. Screenshots `pro_paywall`, `pro_owned` (`ProScreenTest`).
- Strings: `strings_pro.xml` in all 21 languages (machine-assisted like the rest; review before launch).

## What the owner has to do before money can come in

1. **Play Console account** (one-time $25) and identity verification. A new personal account must run a
   closed test before it may publish to production (12 testers for 14 days when this was written; the
   Console shows the current rule).
2. **Upload key.** Create one and keep it out of the repository:
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`.
   Add repository secrets `UPLOAD_KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `UPLOAD_KEYSTORE_PASSWORD`,
   `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`. Enrol in Play App Signing when creating the app.
3. **Create the app** in the Play Console with package `com.monkaydee.tcgcatalogue`, then run the
   "Play Store bundle" workflow and upload the `.aab` to the internal test track.
4. **Create the product**: Monetise → Products → One-time products → ID `cardnavo_pro` (must match exactly),
   set the price (for example €24.99) and activate it. A payments profile and tax details are required.
5. **Licence testers**: add test Google accounts under Settings → Licence testing to buy without being charged.
   Test: buy, restore on a second phone, refund in the Console and confirm Pro locks again, slow payment method.
6. **Store listing**: privacy policy URL (docs/PRIVACY.md as a web page), data-safety form, screenshots,
   and the operator details that docs/UPLOAD-PRIVACY-REVIEW.md lists as open. Game names in plain text only.
7. **Price data licences.** The free tiers of the price APIs are for non-commercial use (ROADMAP.md). Once Pro is
   sold, the shared price server needs commercial plans. Check each provider's terms before launch.

The Play build is signed with a different key than the GitHub APKs. A tester cannot update from a GitHub
APK to the Play version: export a backup, uninstall, install from Play, import the backup.

## Not done

- No server-side receipt check. Ownership is decided on the phone from Play's signed answer, which a rooted
  phone can fake. The price server could verify purchase tokens with the Play Developer API later.
- Not tested against a real Play product: that needs steps 1–5. Logic and screens are covered by tests here.
- No subscription, no trial, no promo codes beyond what the Play Console offers for one-time products.
