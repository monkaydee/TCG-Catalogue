Add CardNavo Pro, the paid tier, for the Play Store build. This GitHub build is unchanged for users.

- New one-time "CardNavo Pro" purchase through Google Play Billing (product `cardnavo_pro`; the price comes from the Play Console). Pro page with buy and restore, in 21 languages.
- Pro tools in the Play build: CSV/PDF reports, import from other apps, decks, more than one own binder, "Worth grading?" with PSA population, cost to complete a set. Scanning, prices, the collection, backups and pre-grading stay free.
- The paywall is only active in builds made with `PRO_PAYWALL=true` (new hand-started "Play Store bundle" workflow). APKs from GitHub Releases keep every tool open and show no Pro page.
- Pending payments do not unlock, purchases are acknowledged, refunds lock again, and Pro keeps working offline.
- Settings no longer shows the price server address and key in builds that ship with the server. Builds without one still offer the fields.
- Launch steps for the owner and what is not yet verified: docs/PRO-TIER.md. A real purchase has not been tested; that needs the Play Console setup.

Keep CardNavo, the existing 0.1.<build-number> convention, package and signing key for GitHub builds. The Play build uses its own upload key.
