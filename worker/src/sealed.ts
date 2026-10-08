import { comparableSummary, phraseIn } from "./comparables";
import { Cache } from "./cache";
import { Budgets } from "./budget";
import { accessToken, languageAspectFilter, languageMatches, marketplace } from "./providers/ebay";
import { fetchJson, obj, str, price, boundedJson, BodyTooLarge } from "./util";
import type { Env } from "./types";

export interface SealedRequest { game: "pokemon" | "one_piece"; productId: string; name: string; language: "EN" | "DE" | "JA"; aliases?: string[]; market?: "US" | "DE"; }
export interface SealedPrice { amount: number; currency: string; source: string; listings: number; low: number; high: number; fetchedAt: string; stale?: boolean; evidence?: string; excluded?: number; }
interface SealedLookup { quote: SealedPrice | null; imageUrl: string | null; }
const OP_CODES: Record<string, string> = {
  "romance dawn": "OP01", "paramount war": "OP02", "pillars of strength": "OP03", "kingdoms of intrigue": "OP04",
  "awakening of the new era": "OP05", "wings of the captain": "OP06", "500 years in the future": "OP07",
  "two legends": "OP08", "emperors in the new world": "OP09", "royal blood": "OP10", "a fist of divine speed": "OP11",
  "legacy of the master": "OP12", "carrying on his will": "OP13", "the best": "PRB01", "memorial collection": "EB01",
  "the azure sea's seven": "OP14", "adventure on kami's island": "OP15", "the time of battle": "OP16",
  "the world's strongest warriors": "OP17", "the dominance of god": "OP18", "egghead crisis": "EB04",
};
export function sealedType(name: string): string {
  if (/build\s*(?:&|and|und)\s*battle.*stadium/i.test(name)) return "battle-stadium";
  if (/build\s*(?:&|and|und)\s*battle/i.test(name)) return "battle-box";
  if (/ultra premium|ultra-premium|\bupc\b/i.test(name)) return "upc";
  if (/\bcase\b|ケース|\bkas(?:e|ten)\b/i.test(name)) return "case";
  if (/elite trainer|\betb\b|top trainer|top-trainer/i.test(name)) return "etb";
  if (/booster bundle|booster[- ]?bundel|ブースターバンドル/i.test(name)) return "bundle";
  if (/booster[- ]?(?:box|display)|display|booster box|ブースターボックス|拡張パック.*box/i.test(name)) return "booster-box";
  if (/deck|デッキ/i.test(name)) return "deck";
  if (/tin|缶/i.test(name)) return "tin";
  if (/blister/i.test(name)) return "blister";
  if (/collection|kollektion|コレクション/i.test(name)) return "collection";
  if (/box|ボックス/i.test(name)) return "unknown-box";
  if (/pack|booster|パック/i.test(name)) return "pack";
  return "unknown";
}
export function sealedContentsMatch(title:string, card:SealedRequest):boolean {
  const units=(s:string)=>[
    ...[...s.matchAll(/\b(\d+)\s*(x|box(?:es|en)?\b|displays?\b)/gi)]
      // "36x boosters" counts packs inside one box, not 36 boxes.
      .filter(m=>m[2].toLowerCase()!=="x" || !/^\s*(?:packs?|boosters?)\b(?![- ]?(?:box|display|bundle))/i.test(s.slice(m.index!+m[0].length)))
      .map(m=>Number(m[1])),
    ...[...s.matchAll(/\b(?:box|display)\s*x\s*(\d+)\b/gi)].map(m=>Number(m[1])),
  ].filter(n=>n>1);
  const expected=units(card.name), actual=units(title);
  if (actual.some(n=>!expected.includes(n)) || expected.some(n=>!actual.includes(n))) return false;
  const contents=(s:string)=>[
    ...[...s.matchAll(/\b(\d+)[- ]*(?:x\s*)?(?:packs?|boosters?)\b(?![- ]?(?:box|display|bundle))/gi)].map(m=>Number(m[1])),
    ...[...s.matchAll(/\b(\d+)er[- ]+(?:booster[- ]?)?display\b/gi)].map(m=>Number(m[1])),
    ...[...s.matchAll(/\bdisplay\s*\(?\s*(\d+)er\b/gi)].map(m=>Number(m[1])),
  ];
  const requested=contents(card.name), observed=contents(title);
  if (requested.length) return observed.length > 0 && requested.every(n=>observed.includes(n)) && observed.every(n=>requested.includes(n));
  // Standard full booster boxes; a half-display or pack bundle must never share its quote.
  if (sealedType(card.name)==="booster-box" && observed.length) {
    const count=card.game==="one_piece" ? 24 : card.language!=="JA" ? 36 : /terastal|shiny treasure|vstar universe|vmax climax|shiny star/i.test(card.name) ? 10 : /(?:\b151\b|SV2a)/i.test(card.name + " " + (card.aliases ?? []).join(" ")) ? 20 : 30;
    if (observed.some(n=>n!==count)) return false;
  }
  return true;
}

function normalized(t: string): string { return t.normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase(); }
/** Require a sealed unit, exact language, same product family and identifiable set. */
export function sealedTitleMatches(title: string, card: SealedRequest, verifiedLanguage?: string): boolean {
  if (!languageMatches(title, card.language, verifiedLanguage)) return false;
  if (/\b(empty|opened|unsealed|lot|half|partial|loose|reseal(?:ed)?|proxy|custom|replica|break|random|mystery|choose|pick|auswahl|w[aä]hlen|loose cards|code card|no packs)\b/i.test(title)) return false;
  if (/shrink(?:wrap)? (?:removed|missing)|no shrink|not sealed|nicht versiegelt|シュリンクなし|開封済|ohne folie|halb(?:es|er|e)? display|ge[oö]ffnet/i.test(title)) return false;
  if (!/sealed|unopened|ovp|versiegelt|unge[oö]ffnet|未開封|シュリンク/i.test(title)) return false;
  const game = card.game === "pokemon" ? /pok[eé]mon|ポケモン|ポケカ/i : /one\s*piece|ワンピース/i;
  if (!game.test(title)) return false;
  const kind=sealedType(card.name);
  if (["unknown","unknown-box"].includes(kind) || sealedType(title) !== kind) return false;
  if (!sealedContentsMatch(title,card)) return false;
  // Special packaging must not fall through a set-only alias to ordinary packs.
  for (const variant of [/pre[- ]?release/i, /sleeved/i, /double[- ]?pack/i])
    if (variant.test(title) !== variant.test(card.name)) return false;
  if (card.game === "one_piece") {
    const code = (card.aliases ?? []).find(a=>/^(?:OP|PRB|EB)\d{2}$/i.test(a)) ?? Object.entries(OP_CODES).find(([name])=>normalized(card.name).includes(name))?.[1];
    const codes = [...title.matchAll(/\b(?:op|prb|eb)[- ]?\d{2}\b/gi)].map(m=>m[0].replace(/[- ]/g,"").toUpperCase());
    if (code && codes.some(c=>c!==code.toUpperCase())) return false;
  }
  const first = /\b(?:1st|first|erste)\s*(?:ed(?:ition)?|auflage)\b/i;
  if (first.test(title) !== first.test(card.name)) return false;
  if (/\bshadowless\b/i.test(title) !== /\bshadowless\b/i.test(card.name)) return false;
  if (/\b(pokemon center|pok[eé]mon center)\b/i.test(title) !== /\b(pokemon center|pok[eé]mon center)\b/i.test(card.name)) return false;
  const name = normalized(card.name).replace(/\([^)]*(?:non-english|asia|japanese|german)[^)]*\)/gi, "")
    .replace(/\b(booster|box|display|pack|case|deck|bundle|collection|tin|elite|trainer|blister|sealed|english|japanese|german|non|cards|card|edition|version|eu)\b/g, "");
  const words = name.split(/[^\p{L}0-9]+/u).filter(w => w.length > 1);
  const t = normalized(title);
  if (words.length && words.every(w => t.includes(w))) return true;
  // Set-only aliases cannot identify character collections, tins or special deck editions.
  if (["booster-box","pack","case","bundle","etb"].includes(kind) && (card.aliases ?? []).some(alias => {
    const words = normalized(alias).split(/[^\p{L}0-9]+/u).filter(w => w.length > 1);
    return words.length > 0 && words.every(w => {
      if (/^(?:op|prb|eb|sv|sm|s|m)\d+[a-z]*$/.test(w)) return new RegExp(`(^|[^a-z0-9])${w}([^a-z0-9]|$)`).test(t.replace(/\b(op|prb|eb)[- ](?=\d)/g,"$1"));
      return phraseIn(t,w);
    });
  })) return true;
  if (card.game === "one_piece") {
    const code = Object.entries(OP_CODES).find(([name]) => normalized(card.name).includes(name))?.[1];
    if (code) {
      const match = /\b(?:op|prb|eb)[- ]?\d{2}\b/gi;
      const codes = [...title.matchAll(match)].map(m => m[0].replace(/[- ]/g, "").toUpperCase());
      return codes.length > 0 && codes.every(c => c === code);
    }
  }
  return false;
}
export function parseSealedListings(json: unknown, card: SealedRequest, verifiedIds: ReadonlySet<string> = new Set()): SealedPrice | null {
  const values: number[] = []; const seen = new Set<string>();
  const { currency } = marketplace(card.language,card.market);
  const rows = obj(json).itemSummaries;
  for (const row of Array.isArray(rows) ? rows : []) {
    const item = obj(row), title = str(item.title) ?? "", id = str(item.itemId) ?? title;
    const p = obj(item.price), value = price(Number(p.value));
    if (seen.has(id) || value === null || p.currency !== currency || !sealedTitleMatches(title, card, verifiedIds.has(id) ? card.language : undefined)) continue;
    seen.add(id); values.push(value);
  }
  // A verified asking reference is useful even for a thin market. Counts and limited evidence
  // remain explicit; it must never be presented as a confirmed sale or a robust market sample.
  const summary=comparableSummary(values,1);
  if (!summary) return null;
  return { ...summary, currency, source:"eBay sealed listings (asking, shipping excluded)", fetchedAt:new Date().toISOString() };
}

/** Only photos belonging to the same verified language, set and sealed unit are usable. */
export function sealedListingImage(json: unknown, card: SealedRequest, verifiedIds: ReadonlySet<string> = new Set()): string | null {
  const rows = obj(json).itemSummaries;
  for (const row of Array.isArray(rows) ? rows : []) {
    const item = obj(row), id = str(item.itemId) ?? "", title = str(item.title) ?? "";
    if (!sealedTitleMatches(title, card, verifiedIds.has(id) ? card.language : undefined)) continue;
    const image = str(obj(item.image).imageUrl);
    if (!image) continue;
    try {
      const url = new URL(image);
      if (url.protocol === "https:" && url.hostname === "i.ebayimg.com") return url.href;
    } catch { /* A missing or malformed photo does not remove usable price evidence. */ }
  }
  return null;
}

/** Broad identity query: language and sealed state are verified on the returned items. */
export function sealedSearchParams(card: SealedRequest, useCode = false): URLSearchParams {
  const name = card.name.replace(/\([^)]*\)/g, "").trim();
  const localized = card.language === "DE" ? card.aliases?.find(a=>!/^\w*\d+\w*$/.test(a) && !/[\u3040-\u30ff\u4e00-\u9fff]/.test(a) && !normalized(name).includes(normalized(a))) : undefined;
  const code = useCode ? card.aliases?.find(a=>/^(?:OP|PRB|EB|SV|SM|S|M)\d+[a-z]*$/i.test(a)) ?? Object.entries(OP_CODES).find(([n])=>normalized(name).includes(n))?.[1] : undefined;
  const identity = code ? (/^(OP|PRB|EB)(\d+)$/i.test(code) ? `(${code},${code.replace(/(\D+)(\d+)/,"$1-$2")})` : code) : localized ?? name;
  const family = localized || code ? ({"booster-box":"(box,display)",pack:"(pack,booster)",case:"case",etb:"(trainer,etb)",bundle:"bundle",deck:"deck",tin:"tin"} as Record<string,string>)[sealedType(name)] ?? "" : "";
  return new URLSearchParams({q:[card.game === "pokemon" ? "Pokemon" : "One Piece",identity,family].filter(Boolean).join(" "),filter:"buyingOptions:{FIXED_PRICE}",limit:"100",fieldgroups:"MATCHING_ITEMS,ASPECT_REFINEMENTS"});
}

async function searchSealedListings(card: SealedRequest, auth: string, budgets: Budgets, useCode: boolean): Promise<SealedLookup> {
  const params = sealedSearchParams(card,useCode);
  const search = async () => {
    if (!(await budgets.reserve("ebay",1))) throw new Error("budget_exhausted");
    return fetchJson("ebay",`https://api.ebay.com/buy/browse/v1/item_summary/search?${params}`,{headers:{Authorization:`Bearer ${await accessToken(auth)}`,"X-EBAY-C-MARKETPLACE-ID":marketplace(card.language,card.market).site}});
  };
  const initial = await search();
  const category = str(obj(obj(initial).refinement).dominantCategoryId);
  const filter = category ? languageAspectFilter(initial,card.language,category) : undefined;
  let filtered: unknown = null;
  if (filter) {
    params.set("aspect_filter",filter);
    // Already title-confirmed matches remain useful if the facet service fails.
    try { filtered = await search(); } catch { if (!parseSealedListings(initial,card)) throw new Error("language_search_unavailable"); }
  }
  const items = (data:unknown) => Array.isArray(obj(data).itemSummaries) ? obj(data).itemSummaries as unknown[] : [];
  const verified = new Set(items(filtered).map(i=>str(obj(i).itemId)).filter((id):id is string=>!!id));
  const data = {itemSummaries:[...items(filtered),...items(initial)]};
  return {quote:parseSealedListings(data,card,verified),imageUrl:sealedListingImage(data,card,verified)};
}
export async function sealedPrice(req: Request, env: Env): Promise<Response> {
  const json = (value: Record<string,unknown>, status = 200) => new Response(JSON.stringify({...value,sealedMatchingRevision:9}), { status, headers: { "content-type": "application/json" } });
  if (Number(req.headers.get("content-length") ?? 0) > 4000) return json({error:"body_too_large"},413);
  let o: Record<string, unknown>;
  try { o = obj(await boundedJson(req,4000)); } catch (error) { return json({error:error instanceof BodyTooLarge ? "body_too_large" : "invalid_json"},error instanceof BodyTooLarge ? 413 : 400); }
  const game = str(o.game)?.toLowerCase(), requestedLanguage = str(o.language)?.toUpperCase();
  const language = requestedLanguage === "JP" ? "JA" : requestedLanguage;
  const name = str(o.name)?.trim(), productId = str(o.productId);
  if (!["pokemon","one_piece"].includes(game ?? "") || !["EN","DE","JA"].includes(language ?? "") || !name || name.length > 200 || !productId || !/^-?\d{1,16}$/.test(productId)) return json({error:"invalid_product"},400);
  const aliases = Array.isArray(o.aliases) ? o.aliases.filter((v): v is string => typeof v === "string" && v.length <= 100).slice(0, 12) : [];
  const card = { game, name, language, productId, aliases, market:o.market === "DE" || o.market === "US" ? o.market : undefined } as SealedRequest;
  const key = `sealed:v9:${game}:${productId}:${language}:${card.market ?? "default"}:${name}:${JSON.stringify([...new Set(aliases)].sort())}`;
  const cache = new Cache(env.DB), now = Date.now();
  const entry = (await cache.getMany<SealedLookup>([key])).get(key);
  if (entry && entry.expiresAt > now) return json({price:entry.value.quote,imageUrl:entry.value.imageUrl,reason:entry.value.quote ? null : "not_found"});
  const auth = env.EBAY_CLIENT_ID && env.EBAY_CLIENT_SECRET ? `${env.EBAY_CLIENT_ID}:${env.EBAY_CLIENT_SECRET}` : null;
  if (!auth) return json({price:entry?.value.quote ? {...entry.value.quote,stale:true} : null,imageUrl:entry?.value.imageUrl,reason:"provider_not_configured"});
  const budgets = await new Budgets(env.DB,env).load();
  if (!budgets.remaining("ebay")) return json({price:entry?.value.quote ? {...entry.value.quote,stale:true} : null,imageUrl:entry?.value.imageUrl,reason:"budget_exhausted"});
  try {
    // Japanese One Piece titles commonly use OP-14/OP14 rather than the English set name.
    // Search that identity in the selected market before trying a foreign-market reference.
    let result = await searchSealedListings(card,auth,budgets,card.game === "one_piece" && card.language === "JA");
    if (!result.quote) {
      // At most four Browse calls. A failed fallback must not cache a false negative.
      const other: SealedRequest = {...card,market:marketplace(card.language,card.market).currency === "EUR" ? "US" : "DE"};
      const fallback = await searchSealedListings(other,auth,budgets,true);
      result = {quote:fallback.quote ? {...fallback.quote,source:fallback.quote.source+` · ${marketplace(other.language,other.market).site} international reference`} : null,
        imageUrl:fallback.imageUrl ?? result.imageUrl};
    }
    await cache.putMany([{key,value:result,source:result.quote?.source ?? null,fetchedAt:now,ttlMs:result.quote ? 86_400_000 : 3_600_000}]);
    return json({price:result.quote,imageUrl:result.imageUrl,reason:result.quote ? null : "not_found"});
  } catch { return json({price:entry?.value.quote ? {...entry.value.quote,stale:true} : null,imageUrl:entry?.value.imageUrl,reason:"unavailable"}); }
}
