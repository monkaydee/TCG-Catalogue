// TCG API (https://tcgapi.dev, full reference at https://tcgapi.dev/llms-full.txt)
//
// Raw prices:  GET https://api.tcgapi.dev/v1/cards/tcgplayer/{tcgplayerId}
//              → { data: { …card, prices: [{ printing: "Normal"|"Foil", market_price, low_price,
//                  median_price, market_price_as_of, last_updated_at }] } }
// Auth header: X-API-Key
// Free plan:   100 requests/day (resets 00:00 UTC). No batch endpoint on the free plan
//              (/v1/bulk/* needs Pro), so it is one call per card.
//
// Important: per-condition prices (/cards/{id}/prices/conditions) need the Pro plan. The free plan
// only gives one TCGplayer *market price* per printing, so we return it as `market` and leave
// the NM/LP/… conditions empty instead of pretending the market price is a Near Mint price.

import type { BatchResult, CardRequest, RawPrice, RawProvider } from "../types";
import { arr, emptyConditions, fetchJson, obj, price, str } from "../util";

const BASE = "https://api.tcgapi.dev/v1";

/** TCG API only knows "Normal" and "Foil"; holo, reverse holo, etched… count as Foil. */
export function tcgApiPrinting(printing?: string): "Normal" | "Foil" | undefined {
  if (!printing) return undefined;
  return /foil|holo|etched|parallel/i.test(printing) && !/^normal$/i.test(printing) ? "Foil" : "Normal";
}

export function parseTcgApi(json: unknown, card: CardRequest): RawPrice | null {
  const rows = arr(obj(obj(json).data).prices).map(obj);
  const wanted = tcgApiPrinting(card.printing);
  const row = (wanted && rows.find((r) => str(r.printing) === wanted)) || rows.find((r) => price(r.market_price) !== null);
  const market = row ? price(row.market_price) : null;
  return market === null ? null : { conditions: emptyConditions(), market, source: "tcgapi" };
}

export function tcgApi(): RawProvider {
  return {
    name: "tcgapi",
    batchSize: 1,
    supports: (card) => !!card.tcgplayerId,
    async fetch(cards, key): Promise<BatchResult<RawPrice>> {
      const card = cards[0];
      const json = await fetchJson("tcgapi", `${BASE}/cards/tcgplayer/${encodeURIComponent(card.tcgplayerId!)}`, {
        headers: { "X-API-Key": key },
      });
      const p = parseTcgApi(json, card);
      return new Map(p ? [[card.key, p]] : []);
    },
  };
}
