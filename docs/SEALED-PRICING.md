# Regional sealed catalogue and prices

The existing positive product ids refer to TCGplayer's catalogue. Pokémon Japan (TCGCSV category 85) is now included with an explicit JA language; those quotes use identified Japanese products and USD prices. Mid/low asking fallbacks are labelled separately from market prices. Negative ids refer to
Cardmarket product ids (negated), preventing collisions. Product identity includes game, product
id and printed language. The database upgrade preserves all existing items as EN and retains
purchase basis separately from the quote currency; cloud restore/merge uses the same identity.

The regional daily index includes Cardmarket's sealed-product catalogue for Pokémon and One
Piece, excluding coins, accessories and lots. German Pokémon set-name aliases come from TCGdex;
Japanese Pokémon and One Piece name/code aliases improve search. The initial source fetch had
4,209 Pokémon and 578 One Piece entries. Counts depend on the upstream catalogue. Alias coverage
is not a claim that every set has every language variant.

Cardmarket public product/price-guide exports do not expose a price for each printed language.
These entries are catalogue templates, not proof that a German or Japanese variant exists. The
app requires the user to confirm the exact product's printed language. Seller location is not
product language. In particular, a One Piece product sold in Germany is not automatically a
German-language One Piece product.

Cardmarket guide prices are shown as **aggregate references**, in EUR, excluded from portfolio
valuation. They are never relabelled as German/Japanese market prices. Exact-language pricing uses
`POST /v1/sealed/price` on the existing Cloudflare Worker, with eBay credentials held server-side.
Explicit catalogue languages narrow templates; unknown variants are labelled unverified. The route shares the existing authentication, IP limit, provider budget and D1 cache.

Matching requires explicit language, game, identifiable set/name, unit type, unopened/sealed
wording and matching named quantity. Opened, empty, resealed, breaks, proxies, wrong-language
products, mismatched box/pack/case units and duplicate listing ids are excluded. A quote requires
at least one distinct matching listing. Samples below five listings and broad price spreads are labelled limited evidence. The result is the median **asking price**, shipping excluded;
it is not a completed-sale price. The selected display currency chooses the import marketplace independently of printed language:
EUR uses EBAY_DE, USD uses EBAY_US. Japanese quotes are import asking prices, not domestic
Japanese JPY sales. Prices expose listing count, source and date.

Positive quotes cache for 24 hours; misses for one hour. Transient failures/budget exhaustion may
return explicitly stale cached prices. Unconfigured providers or insufficient matches leave the
language-specific price unavailable. English TCGplayer prices never fill that gap. All-language
reference guides remain visible separately. Lack of a quote is not a zero-price market valuation.

The deploy workflow runs live schema/currency/language-separation checks and publishes a
`price-verification` artifact. Provider credentials and quotas determine live coverage; fixtures
cannot establish that a source currently has every requested sealed product. The daily index
workflow publishes the regional files alongside the existing card/name indexes.

Regional schema 6 separates shared Black Bolt / White Flare expansion names from language evidence. International products keep German set aliases; explicit JP or native-code products remain Japanese. Japanese code aliases are excluded from international price requests. Combined international collections retain both German set names. The V6 filename bypasses the incorrectly classified cached catalogue.
