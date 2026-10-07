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

import type { BatchResult, CardRequest, GradedPrice, GradedProvider, RawPrice, RawProvider } from "../types";
import { comparableSummary, phraseIn, normalize } from "../comparables";
import { emptyConditions } from "../util";
import { fetchJson, obj, price, str } from "../util";

const API = "https://api.ebay.com";
/** eBay's "CCG Individual Cards" category (Pokémon, One Piece, Magic, …). */
const SINGLES = "183454";
const GRADERS = ["PSA", "BGS", "CGC", "SGC", "TAG", "ACE", "AOG", "GSG", "PI"];
/** Special 10s that sell far above a plain 10: never counted as one. */
const NOT_A_SINGLE = /\b(lot|bundle|proxy|custom|reprint|orica|fan ?art|digital|choose|pick|you pick|break|box|pack|empty|label only)\b/i;

let token: { value: string; expires: number } | null = null;

export async function accessToken(key: string): Promise<string> {
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
export function gradeInTitle(title: string): { grader: string; grade: string; qualifier?: string } | null {
  // PSA qualifiers and altered/authentic-only slabs have separate markets. Abstain for now.
  if (/\b(?:OC|MC|MK|ST|PD|OF|off[- ]?cent(?:er|re)|miscut|marked|stained|altered|authentic|qualifier|qualified|recolou?red|trimmed)\b/i.test(title)) return null;
  const re = new RegExp(`\\b(${GRADERS.join("|")}|BECKETT)\\s*(?:(?:GEM\\s*(?:MINT|MT)|MINT|NM-?MT|PRISTINE|PERFECT|BLACK\\s*LABEL)\\s*)?(10|9[.,]5|[1-9](?:[.,]5)?)(?![\\d.,])`, "gi");
  const found = new Set<string>();
  let m: RegExpExecArray | null;
  while ((m = re.exec(title))) found.add(`${m[1].toUpperCase() === "BECKETT" ? "BGS" : m[1].toUpperCase()}|${m[2].replace(",", ".")}`);
  if (found.size !== 1) return null;
  const [grader, grade] = [...found][0].split("|");
  const qualifier = grade === "10" ? (
    grader === "BGS" && /\bblack\s*label\b/i.test(title) ? "Black Label" :
    grader === "CGC" && /\bperfect\b/i.test(title) ? "Perfect" :
    grader === "CGC" && /\bpristine\b/i.test(title) ? "Pristine" : undefined
  ) : undefined;
  return { grader, grade, ...(qualifier ? { qualifier } : {}) };
}

/** The number before any "/", e.g. "3/64" → "3", "OP05-060" → "OP05-060". */
function shortNumber(n: string): string {
  return n.split("/")[0].replace(/^0+(?=\d)/, "");
}

const FIRST_EDITION = /\b(1st|first)\s*(ed\.?|edition)\b|\b1\.?\s*(edition|auflage)\b|\berste\s*(edition|auflage)\b/i;
const SHADOWLESS = /\bshadowless\b/i;

/** Words sellers use for a card's language (English and the local eBay site's language). */
const LANGUAGE_WORDS: Record<string, RegExp> = {
  EN: /\b(english|englisch|anglais|inglese|ingl[eé]s|eng)\b/i,
  DE: /\b(german|deutsch(?:e[nmrs]?)?|ger)\b/i,
  FR: /\b(french|fran[cç]ais|fran[cç]aise|franz[oö]sisch|fr)\b/i,
  IT: /\b(italian|italiano|italiana|italienisch|ita)\b/i,
  ES: /\b(spanish|espa[nñ]ol|espa[nñ]ola|spanisch|esp)\b/i,
  PT: /\b(portuguese|portugu[eê]s|portugiesisch)\b/i,
  NL: /\b(dutch|nederlands|niederl[aä]ndisch)\b/i,
  PL: /\b(polish|polski|polnisch)\b/i,
  JA: /\b(japanese|japan|jpn|jp|japanisch|japonais)\b|日本語|日本版/i,
  KO: /\b(korean|kor|koreanisch)\b/i,
  ZH: /\b(chinese|chn|s-chinese|t-chinese|chinesisch)\b/i,
};

/** The eBay site where cards of a language are mostly sold, and its currency. */
export function marketplace(language?: string, market?: string): { site: string; currency: string } {
  if (market === "DE") return {site:"EBAY_DE",currency:"EUR"};
  if (market === "US") return {site:"EBAY_US",currency:"USD"};
  switch (language) {
    case "DE": case "PL": case "PT": return { site: "EBAY_DE", currency: "EUR" };
    case "FR": return { site: "EBAY_FR", currency: "EUR" };
    case "IT": return { site: "EBAY_IT", currency: "EUR" };
    case "ES": return { site: "EBAY_ES", currency: "EUR" };
    case "NL": return { site: "EBAY_NL", currency: "EUR" };
    default: return { site: "EBAY_US", currency: "USD" };
  }
}

/** Used to choose search terms, never to infer card language from the marketplace. */
const SITE_LANGUAGE: Record<string, string> = { EBAY_DE: "DE", EBAY_FR: "FR", EBAY_IT: "IT", EBAY_ES: "ES", EBAY_NL: "NL", EBAY_US: "EN" };

/**
 * Require positive language evidence for every card; reject conflicting explicit languages.
 */
export function languageMatches(title: string, language?: string, verifiedLanguage?: string): boolean {
  const lang = language ?? "EN";
  const named = Object.entries(LANGUAGE_WORDS).filter(([, re]) => re.test(title)).map(([code]) => code);
  if (named.length > 0) return named.length === 1 && named[0] === lang;
  // Marketplace is the seller's market, not proof of the language printed on a card.
  return verifiedLanguage === lang;
}

const SET_ALIASES: Record<string,string[]> = {
  "base set":["Basis","Grundset"], "jungle":["Dschungel"], "fossil":["Fossil"],
  "surging sparks":["Stürmische Funken"], "evolving skies":["Drachenwandel"],
};
export function setMatches(title: string, card: CardRequest): boolean {
  if (!card.set) return false;
  const set=normalize(card.set).replace(/^(?:sv|swsh|sm|xy):\s*/,"");
  const aliases=[set,...(SET_ALIASES[set] ?? []),...(card.setAliases ?? [])];
  if (!aliases.some(a=>phraseIn(title,a))) {
    // A complete One Piece collector code identifies its release; printing still needs evidence.
    const code=/^(?:OP|EB|PRB|ST)\d{2}-\d{3}$/i.test(card.number);
    if (!(card.game === "one_piece" && code && phraseIn(title,card.number))) return false;
  }
  if (set === "base set" && /celebrations|evolutions|legendary collection|base set 2|trading card game classic|classic collection/i.test(title)) return false;
  if (set !== "celebrations" && /celebrations|25th anniversary/i.test(title)) return false;
  if (card.releaseYear && /\b(?:19|20)\d{2}\b/.test(title) && !phraseIn(title,card.releaseYear)) return false;
  return true;
}

export function titleMatches(title: string, card: CardRequest, verifiedLanguage?: string): boolean {
  if (!languageMatches(title, card.language, verifiedLanguage)) return false;
  if (!setMatches(title, card)) return false;
  const t = title.toLowerCase();
  if (NOT_A_SINGLE.test(title)) return false;
  // 1st Edition and Shadowless sell for many times the regular print: only for that printing.
  const first = /first|1st/i.test(card.printing ?? "");
  if (FIRST_EDITION.test(title) !== first) return false;
  if (SHADOWLESS.test(title) && !/shadowless/i.test(card.printing ?? "")) return false;
  if (/shadowless/i.test(card.printing ?? "") && !SHADOWLESS.test(title)) return false;
  for (const special of ["master ball", "poke ball"]) {
    if (phraseIn(title,special) !== phraseIn(card.printing ?? "",special)) return false;
  }
  const reverse = /\breverse\b|umgekehrtes holo/i.test(title);
  const holo = /\bholo(?:foil)?\b|\bfoil\b|holographic/i.test(title);
  if (/^(normal|nonholo|nonfoil)$/i.test(card.printing ?? "") && holo) return false;
  if (/holo|foil/i.test(card.printing ?? "") && !/reverse|non[- ]?(?:holo|foil)/i.test(card.printing ?? "") && (!holo && !card.printingUnique || /non[- ]?(?:holo|foil)/i.test(title))) return false;
  if (/alt|parallel|manga|special art|full art/i.test(card.printing ?? "") && !phraseIn(title,card.printing!)) return false;
  if (/\bmanga\b|\balt(?:ernate)? art\b|\bparallel\b/i.test(title) && !/manga|alt|parallel/i.test(card.printing ?? "")) return false;
  if (/reverse/i.test(card.printing ?? "") !== reverse) return false;
  // every word of the name, English or in the card's language
  const named = (name?: string) => {
    const words = (name ?? "").toLowerCase().split(/[^\p{L}0-9]+/u).filter((w) => w.length > 1);
    return words.length > 0 && words.every((w) => t.includes(w));
  };
  if (!named(card.name) && !named(card.localName)) return false;
  if (!card.number) return false;
  // The grade itself (PSA 9) is not collector number #9.
  const numberTitle = t.replace(new RegExp(`\\b(${GRADERS.join("|")}|beckett)\\s*(?:(?:gem\\s*(?:mint|mt)|mint|nm-?mt|pristine|perfect|black\\s*label)\\s*)?(10|[1-9](?:[.,]5)?)(?![\\d.,])`, "gi"), "");
  const requestedTotal = card.number.split("/")[1];
  const titleTotal = /\b\d+\s*\/\s*(\d+)\b/.exec(numberTitle)?.[1];
  if (requestedTotal && titleTotal && Number(requestedTotal) !== Number(titleTotal)) return false;
  const n = shortNumber(card.number).toLowerCase();
  const esc = n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(^|[^a-z0-9])0*${esc}([^a-z0-9]|$)`).test(numberTitle);
}

export function parseEbay(json: unknown, card: CardRequest, verifiedLanguageIds: ReadonlySet<string> = new Set()): GradedPrice[] {
  const by = new Map<string, number[]>(); const seen = new Set<string>();
  for (const it of Array.isArray(obj(json).itemSummaries) ? (obj(json).itemSummaries as unknown[]) : []) {
    const item = obj(it);
    const title = str(item.title) ?? "";
    const id = str(item.itemId) ?? title;
    if (seen.has(id)) continue;
    const p = obj(item.price);
    const value = price(typeof p.value === "string" ? Number(p.value) : p.value);
    if (value === null || str(p.currency) !== marketplace(card.language,card.market).currency || !titleMatches(title, card, verifiedLanguageIds.has(id) ? card.language ?? "EN" : undefined)) continue;
    const g = gradeInTitle(title);
    if (!g || card.grader && g.grader !== card.grader || card.grade && g.grade !== card.grade) continue;
    seen.add(id);
    const k = `${g.grader}|${g.grade}|${g.qualifier ?? ""}`;
    by.set(k, [...(by.get(k) ?? []), value]);
  }
  const date = new Date().toISOString().slice(0, 10);
  return [...by].flatMap(([k, list]) => {
    const summary = comparableSummary(list,1);
    if (!summary) return [];
    const [grader, grade, qualifier] = k.split("|");
    return [{ grader, grade, ...(qualifier ? { qualifier } : {}), price:summary.amount,
      currency:marketplace(card.language,card.market).currency, source:"eBay listings (asking, not sold; shipping excluded)", date,
      listings:summary.listings,low:summary.low,high:summary.high,evidence:summary.evidence,excluded:summary.excluded }];
  });
}

const SLAB = /\b(psa|bgs|beckett|cgc|sgc|tag|ace|aog|gsg|pi|graded|slab)\b/i;

/** Conditions must be explicit. Unknown and conflicting titles never become NM. */
export function conditionInTitle(title: string): keyof RawPrice["conditions"] | null {
  const labels: [keyof RawPrice["conditions"],RegExp][] = [
    ["NM", /\bnear[- ]?mint\b|\bNM\b/i], ["LP", /\blightly[- ]played\b|\bLP\b/i],
    ["MP", /\bmoderately[- ]played\b|\bMP\b/i], ["HP", /\bheavily[- ]played\b|\bHP\b/i],
    ["DMG", /\bdamaged\b|\bDMG\b|besch[aä]digt|crease|dent|knick/i],
  ];
  const found=labels.filter(([,r])=>r.test(title));
  return found.length === 1 ? found[0][0] : null;
}
export function parseEbayRaw(json: unknown, card: CardRequest): RawPrice | null {
  const groups = new Map<keyof RawPrice["conditions"],number[]>(); const seen=new Set<string>();
  for (const it of Array.isArray(obj(json).itemSummaries) ? obj(json).itemSummaries as unknown[] : []) {
    const item=obj(it), title=str(item.title) ?? "", id=str(item.itemId) ?? title;
    const p=obj(item.price), value=price(Number(p.value)), condition=conditionInTitle(title);
    if (seen.has(id) || value===null || !condition || str(p.currency)!==marketplace(card.language,card.market).currency || SLAB.test(title) || !titleMatches(title,card)) continue;
    seen.add(id);groups.set(condition,[...(groups.get(condition) ?? []),value]);
  }
  const conditions=emptyConditions(); const conditionEvidence:NonNullable<RawPrice["conditionEvidence"]>={}; let count=0;
  for (const [condition,values] of groups) {
    const summary=comparableSummary(values,3);
    if (summary) {conditions[condition]=summary.amount;conditionEvidence[condition]=summary;count+=summary.listings;}
  }
  if (!count) return null;
  return {conditions,conditionEvidence,market:null,source:"eBay condition listings (asking, shipping excluded)",currency:marketplace(card.language,card.market).currency,listings:count};
}

/** Query titles broadly, then verify language through eBay's actual item-specific facet. */
export function searchParams(card: CardRequest, extra: string, graded = false, aspectFilter?: string): URLSearchParams {
  const first = /first|1st/i.test(card.printing ?? "") ? "1st edition" : "";
  const { site } = marketplace(card.language,card.market);
  const languageWord = { EN: "English", DE: "Deutsch", FR: "French", IT: "Italian", ES: "Spanish", NL: "Dutch", PT: "portuguese", PL: "polish", JA: "japanese", KO: "korean", ZH: "chinese" }[card.language ?? ""] ?? "";
  const localizedSet = card.language === "JA" ? card.setAliases?.find(a=>/^[\x00-\x7F]+$/.test(a)) : card.language && card.language !== "EN" ? card.setAliases?.find(a=>normalize(a)!==normalize(card.set) && !/^(?:SV|SWSH|SM|XY)\d/i.test(a)) : undefined;
  const alternatives = (values: (string | undefined)[]) => {
    const unique = [...new Set(values.filter((v):v is string=>!!v))];
    const quoted = unique.map(v=>'"'+v.replace(/"/g, "")+'"');
    return quoted.length > 1 ? '('+quoted.join(',')+')' : unique[0] ?? "";
  };
  const name = graded ? alternatives([card.name,card.localName]) : card.localName ?? card.name;
  const set = graded ? alternatives([card.set,localizedSet]) : localizedSet ?? card.set;
  const q = [name, graded ? shortNumber(card.number) : card.number, set, first, graded || SITE_LANGUAGE[site] === card.language ? "" : languageWord, extra].filter(Boolean).join(" ");
  const params = new URLSearchParams({ q, category_ids: SINGLES, filter: "buyingOptions:{FIXED_PRICE}", limit: "100" });
  if (graded) params.set("fieldgroups", "MATCHING_ITEMS,ASPECT_REFINEMENTS");
  if (aspectFilter) params.set("aspect_filter", aspectFilter);
  return params;
}

async function search(card: CardRequest, key: string, extra: string, graded = false, aspectFilter?: string): Promise<unknown> {
  const params = searchParams(card,extra,graded,aspectFilter);
  return fetchJson("ebay", `${API}/buy/browse/v1/item_summary/search?${params}`, {
    headers: { Authorization: `Bearer ${await accessToken(key)}`, "X-EBAY-C-MARKETPLACE-ID": marketplace(card.language,card.market).site },
  });
}

/** Only a server-returned Language facet can establish language missing from a title. */
export function languageAspectFilter(json: unknown, language = "EN"): string | undefined {
  const refinement = obj(obj(json).refinement);
  if (str(refinement.dominantCategoryId) !== SINGLES) return;
  for (const aspect of Array.isArray(refinement.aspectDistributions) ? refinement.aspectDistributions : []) {
    const a = obj(aspect), name = str(a.localizedAspectName);
    if (!name || !/^(language|sprache|langue|lingua|idioma|taal)$/i.test(name)) continue;
    for (const value of Array.isArray(a.aspectValueDistributions) ? a.aspectValueDistributions : []) {
      const v = obj(value), label = str(v.localizedAspectValue);
      if (label && languageMatches(label,language) && !/[{}:,|]/.test(label))
        return `categoryId:${SINGLES},${name}:{${label}}`;
    }
  }
}

async function languageSearch(card: CardRequest, key: string, extra: string) {
  const initial = await search(card,key,extra,true);
  const filter = languageAspectFilter(initial,card.language);
  let filtered: unknown = null;
  if (filter) {
    try { filtered = await search(card,key,extra,true,filter); }
    catch (error) {
      // A failed facet query must not hide already verified title matches.
      if (!parseEbay(initial,card).length) throw error;
    }
  }
  const items = (json: unknown) => Array.isArray(obj(json).itemSummaries) ? obj(json).itemSummaries as unknown[] : [];
  const verified = new Set(items(filtered).map(it=>str(obj(it).itemId)).filter((id):id is string=>!!id));
  // Prefer the filtered copy when duplicates occur; retain title-confirmed unfiltered items too.
  return {items:[...items(filtered),...items(initial)],verified};
}

export function ebayRaw(): RawProvider {
  return {
    name: "ebay",
    batchSize: 1,
    supports: (card) => !!card.name,
    async fetch(cards, key): Promise<BatchResult<RawPrice>> {
      const card = cards[0];
      const p = parseEbayRaw(await search(card, key, ""), card);
      return new Map(p ? [[card.key, p]] : []);
    },
  };
}

export function ebay(): GradedProvider {
  return {
    name: "ebay",
    batchSize: 1,
    // Two query groups, each with at most one language-facet follow-up.
    callsPerBatch: 4,
    supports: (card) => !!card.name,
    async fetch(cards, key): Promise<BatchResult<GradedPrice[]>> {
      const card = cards[0];
      const groups = card.grader ? [card.grader + (card.grade ? " " + card.grade : "")] : [
        "(psa,bgs,cgc,sgc,tag,ace,aog,gsg,pi,beckett)",
        "-psa (bgs,cgc,sgc,tag,ace,aog,gsg,pi,beckett)",
      ];
      const outcomes = await Promise.allSettled(groups.map(extra=>languageSearch(card,key,extra)));
      const results = outcomes.flatMap(r=>r.status === "fulfilled" ? [r.value] : []);
      const verified = new Set(results.flatMap(r=>[...r.verified]));
      const graded = parseEbay({itemSummaries:results.flatMap(r=>r.items)},card,verified);
      const failed = outcomes.find(r=>r.status === "rejected");
      if (!graded.length && failed?.status === "rejected") throw failed.reason;
      return new Map(graded.length ? [[card.key, graded]] : []);
    },
  };
}
