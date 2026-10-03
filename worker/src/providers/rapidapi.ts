// RapidAPI APIs from https://dev.to/lulzasaur/building-a-trading-card-price-tracker-with-free-apis-4mkg
// Free tier: 50 requests/month in total, so these are the last resort.
//
// TCGplayer prices:  GET https://tcgplayer-price-data.p.rapidapi.com/tcgplayer/search?query=…&game=…&limit=5
//                    Headers: x-rapidapi-key, x-rapidapi-host. Items have name, set, marketPrice,
//                    lowestPrice, url (field names from the author's articles; no formal schema).
// PSA population:    GET https://{RAPIDAPI_POP_HOST}{RAPIDAPI_POP_PATH}?certNumber=…
//                    Returns cardName, year, brand, grade, totalPopulation, psa10Count, psa9Count.
//
// Uncertain:
//  - The response wrapper is not documented; we accept a bare array, { results }, or { data }.
//  - The `game` values the search accepts are not documented; we send plain names ("pokemon").
//  - The RapidAPI host of the PSA population API is not published in the article (it calls the
//    author's own backend). It is a setting (RAPIDAPI_POP_HOST) and is off until the owner fills
//    it in from the RapidAPI page of that API.
//  - Results are a name search, so we only accept an item whose name matches the card and, when
//    the item has a number, whose number matches too.

import type { BatchResult, CardRequest, Game, Population, RawPrice, RawProvider } from "../types";
import { arr, emptyConditions, fetchJson, obj, price, str } from "../util";

const GAME_NAMES: Record<Game, string> = {
  pokemon: "pokemon",
  one_piece: "one piece",
  magic: "magic",
  dragon_ball_fw: "dragon ball super fusion world",
  dragon_ball_super: "dragon ball super",
  union_arena: "union arena",
  weiss_schwarz: "weiss schwarz",
  naruto: "naruto",
};

const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, "");

export function rapidItems(json: unknown): Record<string, unknown>[] {
  if (Array.isArray(json)) return json.map(obj);
  const o = obj(json);
  return arr(o.results ?? o.data ?? o.items).map(obj);
}

export function parseRapidTcg(json: unknown, card: CardRequest): RawPrice | null {
  const item = rapidItems(json).find((it) => {
    const name = str(it.name) ?? "";
    if (!name || !norm(name).includes(norm(card.name))) return false;
    // The number may be its own field or part of the name ("Charizard ex - 199/165").
    const num = str(it.number) ?? str(it.cardNumber) ?? /(\b[A-Z]*\d+[a-z]?)\/\w+/i.exec(name)?.[1];
    if (!num || !card.number) return true;
    const strip = (s: string) => norm(s.split("/")[0]).replace(/^0+/, "");
    return strip(num) === strip(card.number);
  });
  const market = item ? (price(item.marketPrice) ?? price(item.market_price) ?? price(item.price)) : null;
  return market === null ? null : { conditions: emptyConditions(), market, source: "rapidapi-tcgplayer" };
}

export function rapidTcg(host: string): RawProvider {
  return {
    name: "rapidapi",
    batchSize: 1,
    supports: (card) => !!card.name,
    async fetch(cards, key): Promise<BatchResult<RawPrice>> {
      const card = cards[0];
      const query = [card.name, card.set, card.number].filter(Boolean).join(" ");
      const q = new URLSearchParams({ query, game: GAME_NAMES[card.game], limit: "5" });
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
