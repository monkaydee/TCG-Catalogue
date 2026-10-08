# Go Shopping — feasibility and implementation proposal

Reviewed 7 October 2026. Planning only: no shopping button, endpoint, paid provider signup, location tracking, checkout or merchant integrations have been implemented.

## Recommendation

Build a regional offer comparison tool, starting with Germany and the United States. Worldwide country selection is realistic; verified domestic offers in every country and an absolute global cheapest-price guarantee are not realistic without a large, maintained network of merchant agreements. Use the claim **Lowest total found among checked shops**, with the country, sources checked, time and incomplete results visible.

The current price service estimates collection values. Its market medians and sold comparables are not shopping offers. A shopping endpoint needs individual available listings, URLs, shipping, stock and regional eligibility. Do not reuse portfolio quotes as purchasable offers.

## Buyer flow

1. Tap **Go Shopping** from Home, a card, sealed product or wishlist. A card/product supplies its identity as an editable starting point.
2. Confirm delivery country and, where necessary, postal code. A device locale can suggest a country; the user must be able to correct it. Save the preference. GPS permission is unnecessary. Country must be independent of display currency and printed product language.
3. Choose game, set, product type (single card, booster pack, booster box, tin, bundle, deck, case), printed language (EN, DE, JP initially), quantity. Singles also require collector number, printing/edition, raw condition or grading company/grade/qualifier. A set alone is a browse category, not an identical purchasable item.
4. Search domestic stock by default: Germany returns items physically located in Germany and deliverable to the German destination; the US does the same for the US. German sellers' English/Japanese cards remain valid when that printed language is requested. An English shop page does not prove a card's language. German One Piece availability must not be invented from German seller location.
5. Show product photo, exact identity, item price, shipping, known taxes/fees, total for requested quantity, seller/dispatch country, delivery estimate where known, stock, merchant and checked time. Keep offers with unknown shipping in a separate **Total not confirmed** group; do not rank them as cheapest delivered.
6. Open the merchant listing for purchase. Refresh the offer before leaving the app. No in-app payment or automatic purchase in the first release. If there are no domestic verified offers, say so and offer external search links. International offers require an explicit filter change later.

## Data access reality

| Source | What it can contribute | Dependency or limitation |
| --- | --- | --- |
| eBay Browse | Current purchasable listings, country/delivery filtering; useful existing integration starting point | Validate our production access for the shopping use case, marketplace/category coverage, quotas and destination shipping fields. Existing valuation searches neither enforce domestic stock nor include shipping. |
| Cardmarket | Strong candidate for European card/sealed offers | Official help currently says new API applications are not accepted. Public price guides are aggregate references, not live seller stock or exact-language offers. Seek an approved arrangement; until then offer outbound search links, not fabricated comparisons. |
| TCGplayer | US offers/catalogue if access is granted | Official developer documentation says new API access is not currently granted. Current TCGCSV/price history integrations do not establish access to buyable seller offers or shipping totals. Do not make this an MVP dependency. |
| CardTrader | Documented marketplace product listing, language filters, seller country and shipping methods | Evaluate approved access, applicable games/product types, display/redistribution terms and destination-specific shipping. Documentation alone does not establish our access. |
| Independent shops | Domestic sealed stock and promotions | Use opt-in APIs or merchant/affiliate feeds with product IDs, language, stock and shipping rules. Each integration needs validation and maintenance; there is no universal endpoint for all online shops. |

Primary sources checked:

- [eBay field filters](https://developer.ebay.com/api-docs/buy/static/ref-buy-browse-filters.html): `itemLocationCountry` restricts item location; `deliveryCountry`/`deliveryPostalCode` restrict deliverability. Combining a local marketplace selection with those filters matters.
- [eBay Buy API requirements](https://developer.ebay.com/api-docs/buy/static/buy-requirements.html): production use/access requirements must be checked for the intended integration; checkout requires separate approval.
- [Cardmarket API access](https://help.cardmarket.com/en/cardmarket-api): no new access applications currently accepted.
- [TCGplayer Getting Started](https://docs.tcgplayer.com/docs/getting-started): no new API access currently granted.
- [CardTrader API reference](https://www.cardtrader.com/en-US/docs/api/full/reference): product offers include price, quantity, properties and seller country; separate shipping methods are documented.

No provider applications or agreements were submitted. Access statements must be rechecked before implementation.

## Proposed architecture

Extend the existing Worker with a separate authenticated `POST /v1/shopping/search`. Keep keys on the server. Android sends an exact product identity, country, optional postal code and quantity. An adapter registry reports supported countries, games, languages and product types. Unsupported routes return an explicit coverage state, rather than silently searching a different country.

Normalize individual offers into: provider/listing ID; exact product ID or verified identity evidence; printed language; printing and condition/slab identity; sealed unit/count; item location; delivery eligibility; available quantity; item price and currency; shipping and mandatory known fees; total and total-confidence state; photo; merchant URL; observed/expiry timestamps. Country/language mappings are provider-specific: display Japanese as **JP**, while mapping to `ja` only for APIs that require an ISO language code. Never reinterpret a language code as a buyer region.

Cache briefly according to each provider's rules. Include country, destination shipping zone/postal code where relevant, exact identity, quantity, currency and filters in the key. Use budgets, debounce/cancel superseded searches, bounded fan-out, per-provider timeouts and partial results with source status. Revalidate availability and cost before purchase-link navigation. Shopping traffic needs a separate budget from background price refresh; current collection budgets cannot fund unlimited worldwide live searches.

Only accept identical variants. Exclude accessories, empty products, breaks, resealed stock, mixed lots, ambiguous language, wrong pack/box/case quantities, unverified grade and conflicting editions. Titles alone are insufficient when seller data cannot distinguish an alternate art or printing. Preserve listing evidence for review without claiming authenticity verification. Do not infer stock from a public product catalogue.

Use native currency for the offer and show a separately labelled conversion. Domestic delivered totals include quoted shipping and mandatory known fees; unknown tax/fee/shipping prevents a confirmed total. Requested quantity and minimum-order restrictions must be applied before ranking. For multiple items, shipping is per seller/order: multiplying one-item shipping by quantity or summing individually cheapest offers can give the wrong cheapest basket.

## Staged delivery and gates

| Phase | Deliverable | Exit criterion |
| --- | --- | --- |
| 0: Access proof | Capability matrix and read-only offer probes for DE/US; one approved independent-shop feed; CardTrader evaluation | Real eligible listing, exact product/language, dispatch country, stock and a destination total can be obtained. Record unavailable partners explicitly. |
| 1: Domestic MVP | Go Shopping, selectors, DE/US eBay plus approved feeds, source statuses, outbound purchase links | Deterministic fixtures reject wrong language/edition/unit/country; real sample links remain purchasable; ranking uses confirmed totals and quantity. |
| 2: Coverage | Approved additional shops/marketplaces, more games/products/languages, alerts | Country/provider coverage dashboard and stale/stock error monitoring; budget/load tests pass. |
| 3: Worldwide rollout | Country registry activated per validated provider/merchant; supported-country delivery rules | Each activated country has documented usable sources, domestic filtering, currency and shipping tests. Countries without coverage still provide honest external-search fallback. |
| 4: Optional advanced features | Cheapest wishlist basket, price-drop alerts, saved filters, shop exclusions | Basket shipping/fees validated; alerts use buyable prices and do not become portfolio valuations. |

Estimated effort, assuming a developer familiar with this repository: access spike 3–5 working days; DE/US eBay MVP 2–3 weeks; each straightforward merchant feed 2–5 days plus testing. These are planning estimates, not delivery promises. Partner negotiations are unbounded; worldwide merchant coverage is continuing work, not a one-time build. Paid APIs, quotas and maintenance are the dominant dependencies.

## Additional feature proposals

Prioritize a **price health** screen (missing identity, provider unavailable, stale, limited asking evidence), **repair Japanese printing** queue, **wishlist shopping** with delivered-total alerts, and **save recognition corrections** with explicit language/printing review. These improve trust and make shopping more useful. None are implemented in this change.
