# Japanese Pokémon printing and pricing

Japanese editions use different set identities and collector numbers from international editions. Changing only the language of `me01-150` (Steelix, Mega Evolution 150/132) does not make it `ja:M1L-073` (ハガネール, Mega Brave 073/063).

When JP is selected for an international Pokémon candidate, Add/Edit loads native Japanese entries through the existing English-name alias index and TCGdex. Matching language-independent metadata ranks the options; the app does not silently choose a set or number from a name or price. International catalogue entries leave printed language to the scan. Quick-add requires a native identity for a Japanese Pokémon scan. The user confirms the printed Japanese identity, which is then saved with its native set, number and photo. Existing purchase costs and manual values follow the existing edit path.

The matching artwork examples reported for Mega Evolution correspond to Exeggutor M1L 066/063 and Steelix M1L 073/063 in the Japanese catalogue. Verify the number printed on the physical card; regular printings and reprints have their own identities.

## Price sources

Native Japanese Cardmarket references come from the Japanese TCGdex product, rather than the international entry. The label explains that condition is not isolated. TCGplayer product IDs and image fallbacks use the Japanese entry’s `thirdParty.tcgplayer` identity. Condition-history requests accept explicit Japanese language evidence and use caches keyed by product and language; English rows cannot populate Japanese condition data.

Graded requests keep the selected native number, product ID, language and set aliases, including the native set code. Provider matching and evidence thresholds remain unchanged. English and product-wide comparison prices remain labelled as references, and no raw price becomes a slab valuation.

## Older saved cards

An older row may have `language=JA` with an international card ID. Price refresh flags it for repair and clears the incompatible market quote, without clearing costs or manual values. The card detail screen now loads native Japanese choices with product references directly. Select the printing matching the number on your card, then Save. Add/Edit also places these photo-and-price choices near the top instead of beneath the language controls. Unknown native identities and missing quotes remain unknown. Background refresh updates are guarded against concurrent changes to printing, language and slab identity, and preserve current user-entered fields.

English-name search supports `JP Steelix`, `JP Exeggutor` and similar names present in the alias index. Printed native codes (for example `M1L 073/063`) and Japanese name searches remain available.

## Verification

Regression fixtures record TCGdex responses for the reported international and native Japanese printings. Tests check native prices and product photos, explicit Japanese condition filtering, cache isolation, correct identity persistence, cost/manual-value preservation, and the Add/Edit selector. A detail-screen repair regression selects the native Steelix product, saves it, and verifies its Japanese price while retaining purchase cost and quantity. Live catalogue checks verify that the native product IDs differ from the English product IDs.
