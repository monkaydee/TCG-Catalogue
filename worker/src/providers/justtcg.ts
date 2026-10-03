// JustTCG (https://justtcg.com/docs, OpenAPI at https://justtcg.com/docs/swagger.json)
//
// Raw prices:   POST https://api.justtcg.com/v1/cards   body: [{ tcgplayerId } | { scryfallId }, ...]
//               Free plan: up to 20 items per batch, 100 calls/day, 1,000 calls/month, 10/min.
//               Response: { data: [{ tcgplayerId, scryfallId, variants: [{ condition, printing,
//               language, price, lastUpdated }] }] }   prices are USD.
// Graded:       GET https://api.justtcg.com/v2/cards?tcgplayer_id=…&graded=only   (beta)
//               Variants with type "graded" carry grading { company, grade, grade_label, qualifier,
//               canonical } and markets[{ region, currency, price, updated_at }].
//               v2 has no batch endpoint yet ("in development"), so it is one call per card.
// Auth header:  x-api-key
//
// Uncertain: whether the free plan returns graded variants at all (the docs do not say). If it
// returns none, the card simply falls through to the next graded provider.

import type { BatchResult, CardRequest, Conditions, GradedPrice, GradedProvider, RawPrice, RawProvider } from "../types";
import { arr, conditionCode, emptyConditions, fetchJson, hasAnyCondition, obj, price, samePrinting, str } from "../util";

const BASE = "https://api.justtcg.com";

/** The identifier JustTCG understands for this card, or null. Magic ids in the app are Scryfall ids. */
export function justTcgIdentifier(card: CardRequest): { tcgplayerId: string } | { scryfallId: string } | null {
  if (card.tcgplayerId) return { tcgplayerId: card.tcgplayerId };
  if (card.game === "magic" && /^[0-9a-f-]{36}$/i.test(card.id)) return { scryfallId: card.id };
  return null;
}

/**
 * Picks one printing's conditions from a JustTCG card's variants. Uses the requested printing
 * when the card has it, otherwise the printing with the most priced conditions. English only.
 */
export function pickConditions(variants: unknown[], printing?: string): Conditions | null {
  const byPrinting = new Map<string, Conditions>();
  for (const v of variants.map(obj)) {
    const lang = str(v.language);
    if (lang && lang.toLowerCase() !== "english") continue;
    const code = conditionCode(str(v.condition) ?? "");
    const p = price(v.price);
    if (!code || p === null) continue;
    // v1 printings may carry a " - Language" suffix; drop it before comparing.
    const pr = (str(v.printing) ?? "Normal").replace(/\s+-\s+\w+$/, "");
    const c = byPrinting.get(pr) ?? emptyConditions();
    c[code] = p;
    byPrinting.set(pr, c);
  }
  if (byPrinting.size === 0) return null;
  if (printing) for (const [pr, c] of byPrinting) if (samePrinting(pr, printing)) return c;
  const count = (c: Conditions) => Object.values(c).filter((x) => x !== null).length;
  return [...byPrinting.values()].sort((a, b) => count(b) - count(a))[0];
}

/** Turns a v1 batch response into prices per requested card. */
export function parseJustTcgBatch(json: unknown, cards: CardRequest[]): BatchResult<RawPrice> {
  const out: BatchResult<RawPrice> = new Map();
  const data = arr(obj(json).data).map(obj);
  for (const card of cards) {
    const id = justTcgIdentifier(card);
    if (!id) continue;
    const match = data.find((d) =>
      "tcgplayerId" in id ? str(d.tcgplayerId) === id.tcgplayerId : str(d.scryfallId) === id.scryfallId,
    );
    if (!match) continue;
    const conditions = pickConditions(arr(match.variants), card.printing);
    if (conditions && hasAnyCondition(conditions)) out.set(card.key, { conditions, market: null, source: "justtcg" });
  }
  return out;
}

/** Turns a v2 graded response into graded prices. Raw variants are ignored on purpose. */
export function parseJustTcgGraded(json: unknown, card: CardRequest): GradedPrice[] {
  const out: GradedPrice[] = [];
  for (const c of arr(obj(json).data).map(obj)) {
    for (const v of arr(c.variants).map(obj)) {
      const g = obj(v.grading);
      if (v.type !== "graded" || !str(g.company)) continue;
      if (str(g.qualifier)) continue; // qualified grades (e.g. "OC") are a different, cheaper market
      if (card.printing && str(v.printing) && !samePrinting(str(v.printing), card.printing)) continue;
      if (str(v.language)) continue; // non-English printing
      const m = obj(arr(v.markets)[0]);
      const p = price(m.price);
      if (p === null) continue;
      const grade = str(g.grade) ?? str(g.grade_label) ?? "Authentic";
      const label = str(g.grade_label);
      const updated = typeof m.updated_at === "number" ? new Date(m.updated_at * 1000).toISOString().slice(0, 10) : undefined;
      out.push({
        grader: str(g.company)!,
        grade: label && str(g.grade) ? `${grade} ${label}` : grade,
        price: p,
        currency: str(m.currency) ?? "USD",
        source: "justtcg",
        ...(updated ? { date: updated } : {}),
      });
    }
  }
  return out;
}

export function justTcgRaw(): RawProvider {
  return {
    name: "justtcg",
    batchSize: 20, // free plan maximum
    supports: (card) => justTcgIdentifier(card) !== null,
    async fetch(cards, key) {
      const body = cards.map((c) => justTcgIdentifier(c)!);
      const json = await fetchJson("justtcg", `${BASE}/v1/cards`, {
        method: "POST",
        headers: { "x-api-key": key, "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      return parseJustTcgBatch(json, cards);
    },
  };
}

export function justTcgGraded(): GradedProvider {
  return {
    name: "justtcg",
    batchSize: 1, // no v2 batch endpoint yet
    supports: (card) => !!card.tcgplayerId,
    async fetch(cards, key) {
      const card = cards[0];
      const q = new URLSearchParams({ tcgplayer_id: card.tcgplayerId!, graded: "only", include: "periods.30d" });
      const json = await fetchJson("justtcg", `${BASE}/v2/cards?${q}`, { headers: { "x-api-key": key } });
      const graded = parseJustTcgGraded(json, card);
      return new Map(graded.length ? [[card.key, graded]] : []);
    },
  };
}
