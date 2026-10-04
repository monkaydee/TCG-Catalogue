// eBay Browse API (https://developer.ebay.com/api-docs/buy/browse/overview.html) — free,
// 5,000 calls/day, commercial use allowed.
//
// Token:  POST https://api.ebay.com/identity/v1/oauth2/token (client credentials, Basic id:secret)
// Search: GET  https://api.ebay.com/buy/browse/v1/item_summary/search?q=…&category_ids=183454
//         → { itemSummaries: [{ title, price: { value, currency }, buyingOptions }] }
//
// These are current *asking* prices of fixed-price listings, not sales, so the source is
// "eBay listings (asking)", never a sales price. A listing only counts when its title names the
// card, its number and exactly one grade; per grader and grade the median is used.
// The key is "clientId:clientSecret" (secrets EBAY_CLIENT_ID and EBAY_CLIENT_SECRET).

import type { BatchResult, CardRequest, GradedPrice } from "../types";
import type { GradedProvider } from "../types";
import { fetchJson, obj, price, str } from "../util";

const API = "https://api.ebay.com";
/** eBay's "CCG Individual Cards" category (Pokémon, One Piece, Magic, …). */
const SINGLES = "183454";
const GRADERS = ["PSA", "BGS", "CGC", "SGC", "TAG", "ACE"];
const NOT_A_SINGLE = /\b(lot|bundle|proxy|custom|reprint|orica|fan ?art|digital|choose|pick|you pick|break|box|pack|empty|label only)\b/i;

let token: { value: string; expires: number } | null = null;

async function accessToken(key: string): Promise<string> {
  if (token && token.expires > Date.now() + 60_000) return token.value;
  const res = await fetch(`${API}/identity/v1/oauth2/token`, {
    method: "POST",
    headers: { Authorization: `Basic ${btoa(key)}`, "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=client_credentials&scope=https%3A%2F%2Fapi.ebay.com%2Foauth%2Fapi_scope",
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new Error(`ebay: token HTTP ${res.status}`);
  const j = obj(await res.json());
  const value = str(j.access_token);
  if (!value) throw new Error("ebay: no token");
  token = { value, expires: Date.now() + (typeof j.expires_in === "number" ? j.expires_in : 7200) * 1000 };
  return value;
}

/** The single grade a listing title names, e.g. "PSA 9" → PSA/9; null when none or several. */
export function gradeInTitle(title: string): { grader: string; grade: string } | null {
  const re = new RegExp(`\\b(${GRADERS.join("|")})\\s*(?:GEM\\s*(?:MINT|MT)\\s*|MINT\\s*|NM-?MT\\s*)?(10|9\\.5|[1-9](?:\\.5)?)(?![\\d.])`, "gi");
  const found = new Set<string>();
  let m: RegExpExecArray | null;
  while ((m = re.exec(title))) found.add(`${m[1].toUpperCase()}|${m[2]}`);
  if (found.size !== 1) return null;
  const [grader, grade] = [...found][0].split("|");
  return { grader, grade };
}

/** The number before any "/", e.g. "3/64" → "3", "OP05-060" → "OP05-060". */
function shortNumber(n: string): string {
  return n.split("/")[0].replace(/^0+(?=\d)/, "");
}

const FIRST_EDITION = /\b(1st|first)\s*(ed\.?|edition)\b/i;
const SHADOWLESS = /\bshadowless\b/i;

export function titleMatches(title: string, card: CardRequest): boolean {
  const t = title.toLowerCase();
  if (NOT_A_SINGLE.test(title)) return false;
  // 1st Edition and Shadowless sell for many times the regular print: only for that printing.
  const first = /first|1st/i.test(card.printing ?? "");
  if (FIRST_EDITION.test(title) !== first) return false;
  if (SHADOWLESS.test(title) && !/shadowless/i.test(card.printing ?? "")) return false;
  // every word of the name (ignoring short ones and punctuation)
  const words = card.name.toLowerCase().split(/[^a-z0-9éè]+/).filter((w) => w.length > 1);
  if (!words.every((w) => t.includes(w))) return false;
  if (!card.number) return true;
  const n = shortNumber(card.number).toLowerCase();
  const esc = n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(^|[^a-z0-9])0*${esc}([^a-z0-9]|$)`).test(t);
}

export function parseEbay(json: unknown, card: CardRequest): GradedPrice[] {
  const by = new Map<string, number[]>();
  for (const it of Array.isArray(obj(json).itemSummaries) ? (obj(json).itemSummaries as unknown[]) : []) {
    const item = obj(it);
    const title = str(item.title) ?? "";
    const p = obj(item.price);
    const value = price(typeof p.value === "string" ? Number(p.value) : p.value);
    if (value === null || str(p.currency) !== "USD" || !titleMatches(title, card)) continue;
    const g = gradeInTitle(title);
    if (!g) continue;
    const k = `${g.grader}|${g.grade}`;
    by.set(k, [...(by.get(k) ?? []), value]);
  }
  const date = new Date().toISOString().slice(0, 10);
  // one listing alone is no price: a single seller can ask anything
  return [...by].filter(([, list]) => list.length >= 2).map(([k, list]) => {
    const [grader, grade] = k.split("|");
    const s = [...list].sort((a, b) => a - b);
    const median = s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
    return { grader, grade, price: Math.round(median * 100) / 100, currency: "USD", source: "eBay listings (asking)", date };
  });
}

export function ebay(): GradedProvider {
  return {
    name: "ebay",
    batchSize: 1,
    supports: (card) => !!card.name,
    async fetch(cards, key): Promise<BatchResult<GradedPrice[]>> {
      const card = cards[0];
      const q = [card.name, card.number ? shortNumber(card.number) : "", card.set, "graded"].filter(Boolean).join(" ");
      const params = new URLSearchParams({ q, category_ids: SINGLES, filter: "buyingOptions:{FIXED_PRICE}", limit: "100" });
      const json = await fetchJson("ebay", `${API}/buy/browse/v1/item_summary/search?${params}`, {
        headers: { Authorization: `Bearer ${await accessToken(key)}`, "X-EBAY-C-MARKETPLACE-ID": "EBAY_US" },
      });
      const graded = parseEbay(json, card);
      return new Map(graded.length ? [[card.key, graded]] : []);
    },
  };
}
