# CardNavo UI design

CardNavo keeps its mint accent, dark and light themes, custom palettes, collection data and market matching. Shared controls now use a readable bundled font, outlined Material icons and consistent spacing.

## Shared controls

- Inter is bundled in regular, medium, semibold and bold weights. System fallback supplies glyphs for other writing systems. Typography no longer follows a phone’s decorative font.
- Actions use 12 dp corners and a minimum 48 dp height. Primary actions are filled, secondary actions outlined and supporting actions tonal. Long translated card actions stack on narrow phones or with larger text.
- Game, language, layout and sorting use bounded selectors. Selected labels can truncate; opening the menu reveals the full option.
- Detailed evidence uses expandable panels. Price status and retained-quote warnings remain visible in the summary.
- Outlined icons retain text labels or accessibility descriptions. Gains and losses use lighter colours in dark themes.

## Screens

Card details show printed language, the exact slab grade, price scope and market value together. Ungraded references remain separate from slab values. Edit and Sell stay prominent; trade, alerts and removal are in More actions, with the existing removal confirmation.

Collection navigation prioritises scanning. Binder controls fit within the screen and expose layout and sorting without horizontal scrolling. Pocket overlays and backgrounds are quieter.

Sealed search uses game and language selectors, product thumbnails, compact price rows and clearly labelled listing evidence. Japanese is displayed as JP; internal provider language identifiers remain unchanged. Unsupported German One Piece is disabled, and switching from German Pokémon selects English. Product photos are retained when supplied; absent photos have a neutral placeholder.

Pre-grading shows capture, alignment, inspection and result stages. Photo actions are prominent; tips, measurements and surface evidence can expand. The existing evidence limits, non-professional disclaimer and grade eligibility rules remain in force.

## Font provenance

Inter source: https://github.com/google/fonts/tree/main/ofl/inter

Static resources in `app/src/main/res/font/` were instanced at optical size 14 and weights 400, 500, 600 and 700 from the official variable font. The SIL Open Font License is bundled at `app/src/main/assets/licenses/Inter-OFL.txt`.

## Validation

Robolectric renders actual Compose screens in English dark mode and German light mode on a 320 dp phone with 130% text size. Interaction checks cover card actions, price evidence, sealed language switching and capture actions. Existing pre-grading, pricing and catalogue regressions remain part of the full test suite. Release lint and the signed GitHub build run before distribution.
