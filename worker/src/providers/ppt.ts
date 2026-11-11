// PokemonPriceTracker (https://www.pokemonpricetracker.com/llms-full.txt) — optional, paid key.
//
// Graded:      GET https://www.pokemonpricetracker.com/api/v2/cards?tcgPlayerId=…&includeEbay=true
//              → { data: { tcgPlayerId, ebay: { salesByGrade: { psa10: { count, averagePrice,
//                  medianPrice, smartMarketPrice: { price, confidence } }, cgc9_5: … } } } }
//              With tcgPlayerId, `data` is a single object. Cost: 1 credit + 1 for eBay = 2 credits.
// Auth header: Authorization: Bearer <key>
// Plans:       Pro 20,000 credits/day (the default budget assumes 2 credits per call).
//
// Only used for Pokémon, and only when the PPT_KEY secret exists.

import type { BatchResult, GradedPrice, GradedProvider } from "../types";
import { fetchJson, obj, price, str } from "../util";

const BASE = "https://www.pokemonpricetracker.com/api/v2";

/** "psa10" → PSA 10, "cgc9_5" → CGC 9.5, "bgs10" → BGS 10. */
export function splitGradeKey(k: string): { grader: string; grade: string } | null {
  const m = /^([a-z]+)(\d+(?:_\d+)?)$/i.exec(k);
  return m ? { grader: m[1].toUpperCase(), grade: m[2].replace("_", ".") } : null;
}

export function parsePpt(json: unknown): GradedPrice[] {
  const data = obj(obj(json).data);
  const ebay = obj(data.ebay);
  const date = str(ebay.dateRangeEnd)?.slice(0, 10);
  const out: GradedPrice[] = [];
  for (const [k, v] of Object.entries(obj(ebay.salesByGrade))) {
    const g = splitGradeKey(k);
    const s = obj(v);
    const p = price(obj(s.smartMarketPrice).price) ?? price(s.medianPrice) ?? price(s.averagePrice);
    if (!g || p === null) continue;
    if (["BGS","CGC"].includes(g.grader) && g.grade === "10") continue; // Aggregated tier cannot distinguish premium labels.
    const sales = typeof s.count === "number" ? s.count : undefined;
    if (sales !== undefined && sales < 3) continue;
    out.push({ ...g, price: p, currency: "USD", source: "pokemonpricetracker", ...(date ? { date } : {}), ...(sales !== undefined ? { sales } : {}) });
  }
  return out;
}

export function ppt(): GradedProvider {
  return {
    name: "ppt",
    batchSize: 1,
    supports: (card) => card.game === "pokemon" && !!card.tcgplayerId,
    async fetch(cards, key): Promise<BatchResult<GradedPrice[]>> {
      const card = cards[0];
      const q = new URLSearchParams({ tcgPlayerId: card.tcgplayerId!, includeEbay: "true" });
      const json = await fetchJson("ppt", `${BASE}/cards?${q}`, { headers: { Authorization: `Bearer ${key}` } });
      const graded = parsePpt(json);
      return new Map(graded.length ? [[card.key, graded]] : []);
    },
  };
}
