# Pricing correction and remaining evidence limits

The app matches product identity before price. A successful HTTP response does not prove a
valuation. The regression suite covers cross-set reprints, collector-number/grade collisions,
wrong languages, PSA-qualified slabs, premium tens, alternate printings, raw condition mixing,
and sealed box/bundle/Battle/case/contents mismatches.

## Comparable quotes

Card titles need an explicit printed language, set or complete One Piece release code, name,
collector number and supported printing evidence. Known Base Set reprints are excluded. Declared
release years and localized set names are supplied from TCGdex. Missing or ambiguous evidence
abstains. This is conservative title matching, not verification of a seller's physical item.

PSA OC/MC/MK/ST/PD/OF and altered/authentic-only listings cannot enter ordinary-grade averages.
BGS Black Label and CGC Pristine/Perfect stay separate. Providers with aggregated BGS/CGC 10
sold tiers that do not distinguish premium labels are skipped for those tiers.

Graded asking quotes require five unique matching listings. Explicit raw conditions require
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
Non-English fallback and choosing a printing/TCGplayer product by closest price are removed.
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
