// PokeTrace (https://poketrace.com/docs, OpenAPI at https://api.poketrace.com/v1/openapi.json)
//
// Raw prices:  GET https://api.poketrace.com/v1/cards?tcgplayer_ids=1,2,3&market=US&limit=20
//              Up to 20 TCGplayer ids per call. Pokémon only.
// Auth header: X-API-Key
// Free plan:   250 requests/day, 1 request per 2 seconds, US market raw conditions only.
// Response:    { data: [{ variant, refs: { tcgplayerId }, currency, prices: { tcgplayer: {
//              NEAR_MINT: { avg, low, high, saleCount }, LIGHTLY_PLAYED: …}, ebay: {…} },
//              lastUpdated }] }
//
// Graded tiers (PSA_10 …) are a paid feature, so they are not used here.
// Uncertain: one TCGplayer product can come back as several PokeTrace cards (one per variant);
// we pick the one whose `variant` matches the requested printing, else the first.

import type { BatchResult, CardRequest, Conditions, RawPrice, RawProvider } from "../types";
import { arr, conditionCode, emptyConditions, fetchJson, hasAnyCondition, obj, price, samePrinting, str } from "../util";

const BASE = "https://api.poketrace.com/v1";

/** Reads raw condition prices, preferring TCGplayer and filling gaps from eBay sold prices. */
export function poketraceConditions(card: Record<string, unknown>): Conditions {
  const c = emptyConditions();
  const prices = obj(card.prices);
  for (const source of ["tcgplayer", "ebay"]) {
    for (const [tier, value] of Object.entries(obj(prices[source]))) {
      const code = conditionCode(tier); // graded tiers like PSA_10 map to null and are skipped
      const p = price(obj(value).avg);
      if (code && p !== null && c[code] === null) c[code] = p;
    }
  }
  return c;
}

export function parsePoketrace(json: unknown, cards: CardRequest[]): BatchResult<RawPrice> {
  const out: BatchResult<RawPrice> = new Map();
  const data = arr(obj(json).data).map(obj);
  for (const card of cards) {
    const matches = data.filter((d) => str(obj(d.refs).tcgplayerId) === card.tcgplayerId);
    if (matches.length === 0) continue;
    const best = matches.find((d) => card.printing && samePrinting(str(d.variant), card.printing)) ?? matches[0];
    if ((str(best.currency) ?? "USD") !== "USD") continue;
    const conditions = poketraceConditions(best);
    if (hasAnyCondition(conditions)) out.set(card.key, { conditions, market: null, source: "poketrace" });
  }
  return out;
}

export function poketrace(): RawProvider {
  return {
    name: "poketrace",
    batchSize: 20,
    minIntervalMs: 2100, // free plan burst limit: 1 request per 2 seconds
    supports: (card) => card.game === "pokemon" && !!card.tcgplayerId,
    async fetch(cards, key) {
      const q = new URLSearchParams({
        tcgplayer_ids: cards.map((c) => c.tcgplayerId).join(","),
        market: "US",
        limit: "20",
      });
      const json = await fetchJson("poketrace", `${BASE}/cards?${q}`, { headers: { "X-API-Key": key } });
      return parsePoketrace(json, cards);
    },
  };
}
