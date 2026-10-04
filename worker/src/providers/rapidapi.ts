// RapidAPI APIs from https://dev.to/lulzasaur/building-a-trading-card-price-tracker-with-free-apis-4mkg
// Free tier of the TCGplayer search: 25 requests/month (hard limit), so it is the last resort.
//
// TCGplayer prices:  GET https://tcgplayer-price-data.p.rapidapi.com/tcgplayer/search?query=…&limit=…
//                    Headers: x-rapidapi-key, x-rapidapi-host. From the API's OpenAPI spec on RapidAPI:
//                    { success, count, results: [{ productName, productLineName, setName, rarityName,
//                    marketPrice, medianPrice, lowestPrice, lowestPriceWithShipping, totalListings,
//                    imageUrl, url, scrapedAt }] }. Only `query` and `limit` (max 50) exist.
// PSA population:    GET https://{RAPIDAPI_POP_HOST}{RAPIDAPI_POP_PATH}?certNumber=…
//                    Returns cardName, year, brand, grade, totalPopulation, psa10Count, psa9Count.
//
// Uncertain:
//  - The RapidAPI host of the PSA population API is not published in the article (it calls the
//    author's own backend). It is a setting (RAPIDAPI_POP_HOST) and is off until the owner fills
//    it in from the RapidAPI page of that API.
//  - Results are a name search: a result is only used when its name and game match and its set
//    or number settles which printing it is (never a guess between several look-alikes).

import type { BatchResult, CardRequest, Game, Population, RawPrice, RawProvider } from "../types";
import { arr, emptyConditions, fetchJson, obj, price, str } from "../util";

/** Words of TCGplayer's product line name ("Pokemon", "One Piece Card Game", "Magic: The Gathering" …). */
const GAME_WORDS: Record<Game, string> = {
  pokemon: "pokemon",
  one_piece: "onepiece",
  magic: "magic",
  dragon_ball_fw: "fusionworld",
  dragon_ball_super: "dragonball",
  union_arena: "unionarena",
  weiss_schwarz: "weiss",
  naruto: "naruto",
};

const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, "");

export function rapidItems(json: unknown): Record<string, unknown>[] {
  if (Array.isArray(json)) return json.map(obj);
  const o = obj(json);
  return arr(o.results ?? o.data ?? o.items).map(obj);
}

export function parseRapidTcg(json: unknown, card: CardRequest): RawPrice | null {
  const strip = (v: string) => norm(v.split("/")[0]).replace(/^0+/, "");
  const nameOf = (it: Record<string, unknown>) => str(it.productName) ?? str(it.name) ?? "";
  const matches = rapidItems(json).filter((it) => {
    const name = nameOf(it);
    if (!name || !norm(name).includes(norm(card.name ?? ""))) return false;
    const line = str(it.productLineName);
    return !line || norm(line).includes(GAME_WORDS[card.game]) ||
      (card.game === "dragon_ball_super" && !norm(line).includes("fusionworld") && norm(line).includes("dragonball"));
  });
  // The number may be its own field or part of the name ("Charizard ex - 199/165").
  const numberOf = (it: Record<string, unknown>) =>
    str(it.number) ?? str(it.cardNumber) ?? /(\b[A-Z]*\d+[a-z]?)\/\w+/i.exec(nameOf(it))?.[1];
  const byNumber = card.number ? matches.filter((it) => { const n = numberOf(it); return !!n && strip(n) === strip(card.number!); }) : [];
  const bySet = card.set ? matches.filter((it) => norm(str(it.setName) ?? str(it.set) ?? "") === norm(card.set!)) : [];
  const both = byNumber.filter((it) => bySet.includes(it));
  const item = both[0] ?? (byNumber.length === 1 ? byNumber[0] : undefined) ?? (bySet.length === 1 ? bySet[0] : undefined) ??
    (matches.length === 1 && !card.set && !card.number ? matches[0] : undefined);
  const market = item ? (price(item.marketPrice) ?? price(item.medianPrice) ?? price(item.market_price) ?? price(item.price)) : null;
  return market === null ? null : { conditions: emptyConditions(), market, source: "rapidapi-tcgplayer" };
}

export function rapidTcg(host: string): RawProvider {
  return {
    name: "rapidapi",
    batchSize: 1,
    supports: (card) => !!card.name,
    async fetch(cards, key): Promise<BatchResult<RawPrice>> {
      const card = cards[0];
      // A plain name search; the most results per request, as every request counts.
      const q = new URLSearchParams({ query: card.name!, limit: "50" });
      const json = await fetchJson("rapidapi", `https://${host}/tcgplayer/search?${q}`, {
        headers: { "x-rapidapi-key": key, "x-rapidapi-host": host },
      });
      const p = parseRapidTcg(json, card);
      return new Map(p ? [[card.key, p]] : []);
    },
  };
}

export function parseRapidPop(json: unknown): Population | null {
  const o = obj(Array.isArray(json) ? json[0] : obj(json).data ?? json);
  const n = (v: unknown) => (typeof v === "number" ? v : typeof v === "string" && v.trim() !== "" && !isNaN(+v) ? +v : null);
  const total = n(o.totalPopulation);
  if (total === null) return null;
  const byGrade: Record<string, number> = {};
  if (n(o.psa10Count) !== null) byGrade["10"] = n(o.psa10Count)!;
  if (n(o.psa9Count) !== null) byGrade["9"] = n(o.psa9Count)!;
  const description = [str(o.year), str(o.brand), str(o.cardName)].filter(Boolean).join(" ") || undefined;
  return { total, higher: null, byGrade, description, source: "rapidapi-psa" };
}

export async function rapidPop(host: string, path: string, key: string, cert: string): Promise<Population | null> {
  const json = await fetchJson("rapidapi", `https://${host}${path}?certNumber=${encodeURIComponent(cert)}`, {
    headers: { "x-rapidapi-key": key, "x-rapidapi-host": host },
  });
  return parseRapidPop(json);
}
