# Pricing correction and remaining evidence limits

The app matches product identity before price. A successful HTTP response does not prove a
valuation. The regression suite covers cross-set reprints, collector-number/grade collisions,
wrong languages, PSA-qualified slabs, premium tens, alternate printings, raw condition mixing,
and sealed box/bundle/Battle/case/contents mismatches.

## Comparable quotes

Card titles need an explicit language (or a verified eBay language facet for graded listings), set or complete One Piece release code, name,
collector number and supported printing evidence. Known Base Set reprints are excluded. Declared
release years and localized set names are supplied from TCGdex. Missing or ambiguous evidence
abstains. This is conservative title matching, not verification of a seller's physical item.

PSA OC/MC/MK/ST/PD/OF and altered/authentic-only listings cannot enter ordinary-grade averages.
BGS Black Label and CGC Pristine/Perfect stay separate. Providers with aggregated BGS/CGC 10
sold tiers that do not distinguish premium labels are skipped for those tiers.

Graded asking references can use one unique matching listing, with limited evidence below five. Explicit raw conditions require
three comparables for EACH condition; mixed/unknown conditions cannot become NM. Sealed asking
quotes require three unique matching listings for the exact format, set, edition and named
contents. Extreme asking prices outside a factor of four around the median are excluded; a
remaining broad spread is labelled limited evidence. These labels are not statistical certainty.
All asking quotes exclude shipping and are labelled separately from completed-sale sources.
PokemonPriceTracker's sold-data adapter remains optional and requires an existing licensed key;
no service was purchased or provider credentials fabricated to increase coverage.

## Language and market

Printed language is separate from marketplace. EUR settings request Japanese/other-language
imports on eBay Germany; USD settings request eBay US. Currency is retained and converted for
portfolio display. These are import asking prices, NOT Japanese domestic JPY sales. Adding a
Japanese domestic sold feed requires an authorized provider with usable product identifiers.

Japanese TCGCSV card names are paired with native set/number identities when an unambiguous
English name exists. Unknown aliases abstain. Explicit catalogue language narrows regional
results; other entries remain unverified templates, visibly labelled. Matching live quotes add
availability evidence without certifying a user's physical product. Catalogue counts do not
establish that every template is published in every language.

One Piece Cardmarket choices are automatic only when exactly one priced English listing exists.
Unverified non-English fallback and choosing a printing/TCGplayer product by closest price are removed.
Cardmarket public guides still represent aggregate references where exact-language/condition
transactions are not provided; the source note discloses that limitation.

## Cache, upgrade and validation

Raw and graded caches carry independent timestamps and stale flags. The app saves the original
retrieval date, displays stale warnings and retains failed-refresh values with a warning. Native
sealed quotes retain the index date instead of assigning the time the item was added.
Negative searches use a shorter TTL; transient/provider-budget failures are not confirmed absence.

The new matching revision invalidates server caches. On first upgraded launch, affected automatic
collection/sealed quotes are cleared and a network-constrained refresh is queued. Manual values,
quantities, purchase costs and currencies are preserved. Some prices will remain unavailable
under the stricter rules; unavailable does not mean a zero-price market.

Deployment smoke checks use native Japanese identities and cover Pokémon/One Piece raw and
graded EN/DE/JA paths, EUR/USD sealed markets, source/count/range constraints and freshness.
The artifact reports available and unavailable quotes separately. Fixtures and smoke samples
cannot guarantee all providers, grades or products have market coverage.

## Missing-price repair and matching revision 5

Unavailable automatic quotes (including legacy automatic zero values) are unknown, not zero-valued cards. Manual zero remains valid. Partial totals show known amounts and missing-copy counts; incomplete valuations do not produce portfolio-loss snapshots. Failed or empty refreshes preserve existing quotes with a warning. This repair queues a refresh without clearing saved values.

A raw provider returning only an aggregate market reference no longer prevents later condition-price providers from being queried. Native card IDs establish which catalogue language a quote belongs to; an English image is not evidence of an English physical card. Localized set aliases improve German search terms.

Holo wording may be absent from a comparable only when the catalogue explicitly reports a single supported printing. Multiple or unknown printings still require positive title evidence. Explicit non-holo, reverse, edition and conflicting-language exclusions remain in force. Revision 5 separates these searches from earlier server caches.

## Raw references and condition-only recovery

Raw saved valuations query TCGplayer condition buckets even when the catalogue has no aggregate market price. An explicit requested-condition bucket can be used without intermediate buckets or a market reference; it is not synthesized from a different condition. Matching remains restricted to the saved language and printing. Missing raw rows refresh on opening their card page, and refreshing the comparison tables also refreshes the saved valuation.

A slab page automatically loads an ungraded market reference, independently of the exact graded quote. The reference is labelled and excluded from the slab's stored value, binder totals and portfolio totals. A response saying graded providers are unavailable is distinct from a network failure: the server answered but upstream data could not be obtained. A missing quote never claims to retain a price that was not saved.

## Verified native Cardmarket guide references

The public Cardmarket guide is a product-wide aggregate, not an English-only transaction feed. For a non-English NM Pokémon row, a native TCGdex catalogue record may establish that the same set, collector number, variant and Cardmarket product have a positive guide value. The app can then save this as a clearly labelled Cardmarket reference. This is not a claim of a language- or condition-specific quote. English TCGplayer data remain excluded from non-English saved valuations.

A missing native record, differing product ID, differing printing, or requested played condition prevents this fallback. English collector identities are never translated into Japanese release identities. Native references do not replace exact graded prices. Repair refreshes preserve manual values, quantities and cost information.

## Graded lookup repair, revisions 6–7

TCGdex's hyphenated `1st-edition-holofoil` and `unlimited-holofoil` keys now retain their product IDs, prices and printing names. This repairs provider eligibility for the reported Dark Charizard and Jungle Flareon examples. The selected slab company and grade are sent to the server and included in its cache key, preventing popular PSA 10 listings from crowding out the requested grade.

eBay graded searches use company names rather than requiring the word "graded". Queries cover English/local names and sets, GSG/AOG/PI, and decimal-comma grades. A bounded follow-up uses the Language aspect returned by eBay's search refinement. Only item IDs returned by that filtered search gain language evidence; an explicit conflicting language in a title still rejects the item. Marketplace alone never proves language. First-edition, collector-number, printing and special-label checks remain in force.

One to four exact asking comparables are disclosed as limited evidence with listing counts and ranges. They are current listing references, not completed sales or a guarantee of realizable value. The app no longer reports "no recent sales" when it merely received no quote. Missing graded rows refresh on opening their page and graded-panel refresh updates the stored quote, preserving saved values if retrieval fails.

Deployment verification includes the reported first-edition Dark Charizard PSA 5, unlimited Flareon PSA 8 and German Psyduck CGC 9 requests. It separately reports JustTCG v2 HTTP access and priced-variant counts without storing credentials. Successful schema checks alone do not establish graded market coverage. The public PSA API supplies certification/population data, not the estimates and sales history displayed by PSA's website. eBay Browse supplies active listings, not completed sales; neither screenshots nor an active asking-price median are converted into verified sale prices.

Revision 7 adds a bounded international fallback for targeted slabs when the selected market returns no quote. The alternate market is queried for the same language, card, printing, company and grade. Its currency and international-reference label are retained rather than merging USD and EUR comparables. Targeted requests still reserve at most four eBay search calls. Graded-only app lookups skip raw-provider calls; independent raw reference panels request raw prices separately. An unpriced JustTCG graded variant remains unpriced.


## Sealed price previews and language repair

Search results now fetch quotes for composed rows with at most two requests in flight. Quotes are reused when a row is revisited or selected within the same language/currency session. Unavailable providers and missing matches have distinct labels. Unknown sealed values display a dash rather than an automatic zero. Saved products whose earlier generic catalogue IDs are now filtered still refresh using their declared name and language, with exact-language matching on the server.

Regional schema 3 carries candidateLanguages separately from languages. A Non-English One Piece template can be a Japanese search candidate, but remains unconfirmed and carries no Japanese price until independently matched listings provide it. English generic rows are no longer cloned into German or Japanese One Piece. Explicit Chinese/Korean/French product names cannot enter Japanese or German results. Known native Japanese Pokémon release templates are separated from international Pokémon templates. The app applies equivalent filters to cached schema-2 files immediately after upgrading. Aggregate Cardmarket guide amounts remain separate references, excluded from saved language-specific valuations.

Bandai lists Japanese, English, French, Simplified Chinese and Korean versions, with no German One Piece card edition: https://en.onepiece-cardgame.com/rules/announcements/lang_card_rule.php . The native Two Legends OP-08 release is documented at https://www.onepiece-cardgame.com/products/boosters/op08.php . Marketplace location does not establish product language. German Pokémon remains supported.

Sealed matching revision 5 queries identity and product format without requiring the English word sealed. Actual titles still need positive sealed-state evidence (including OVP/未開封), correct format, contents, edition and product language. An item-specific Language refinement may establish language missing from a title; conflicting title languages always reject. A maximum of four Browse searches covers the selected market plus a native-code query in the alternate US/German market. International references retain their original currency and are labelled. Each actual search reserves its own budget. Aliases are included in the revised cache key. Asking references require at least three distinct matching listings and never claim completed sales.

Live deployment verification requires positive quotes for the reported Japanese Two Legends booster box and German Surging Sparks booster box, in addition to the wider sealed fixtures.
