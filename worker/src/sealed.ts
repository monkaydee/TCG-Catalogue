import { comparableSummary, phraseIn } from "./comparables";
import { Cache } from "./cache";
import { Budgets } from "./budget";
import { accessToken, languageMatches, marketplace } from "./providers/ebay";
import { fetchJson, obj, str, price } from "./util";
import type { Env } from "./types";

export interface SealedRequest { game: "pokemon" | "one_piece"; productId: string; name: string; language: "EN" | "DE" | "JA"; aliases?: string[]; market?: "US" | "DE"; }
export interface SealedPrice { amount: number; currency: string; source: string; listings: number; low: number; high: number; fetchedAt: string; stale?: boolean; evidence?: string; excluded?: number; }
const OP_CODES: Record<string, string> = {
  "romance dawn": "OP01", "paramount war": "OP02", "pillars of strength": "OP03", "kingdoms of intrigue": "OP04",
  "awakening of the new era": "OP05", "wings of the captain": "OP06", "500 years in the future": "OP07",
  "two legends": "OP08", "emperors in the new world": "OP09", "royal blood": "OP10", "a fist of divine speed": "OP11",
  "legacy of the master": "OP12", "carrying on his will": "OP13", "the best": "PRB01", "memorial collection": "EB01",
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
  const units=(s:string)=>[...s.matchAll(/\b(\d+)\s*(?:x|box(?:es|en)?|displays?)\b/gi)].map(m=>Number(m[1])).filter(n=>n>1);
  const expected=units(card.name), actual=units(title);
  if (actual.some(n=>!expected.includes(n)) || expected.some(n=>!actual.includes(n))) return false;
  const contents=(s:string)=>[...s.matchAll(/\b(\d+)\s*(?:packs?|boosters?)\b(?![- ]?(?:box|display|bundle))/gi)].map(m=>Number(m[1]));
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
export function sealedTitleMatches(title: string, card: SealedRequest): boolean {
  if (!languageMatches(title, card.language)) return false;
  if (/\b(empty|opened|unsealed|lot|half|partial|loose|reseal(?:ed)?|proxy|custom|replica|break|random|mystery|loose cards|code card|no packs)\b/i.test(title)) return false;
  if (/shrink(?:wrap)? (?:removed|missing)|no shrink|シュリンクなし|開封済|ohne folie|halb(?:es|er|e)? display|ge[oö]ffnet/i.test(title)) return false;
  if (!/sealed|unopened|ovp|versiegelt|unge[oö]ffnet|未開封|シュリンク/i.test(title)) return false;
  const game = card.game === "pokemon" ? /pok[eé]mon|ポケモン|ポケカ/i : /one\s*piece|ワンピース/i;
  if (!game.test(title)) return false;
  const kind=sealedType(card.name);
  if (["unknown","unknown-box"].includes(kind) || sealedType(title) !== kind) return false;
  if (!sealedContentsMatch(title,card)) return false;
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
export function parseSealedListings(json: unknown, card: SealedRequest): SealedPrice | null {
  const values: number[] = []; const seen = new Set<string>();
  const { currency } = marketplace(card.language,card.market);
  const rows = obj(json).itemSummaries;
  for (const row of Array.isArray(rows) ? rows : []) {
    const item = obj(row), title = str(item.title) ?? "", id = str(item.itemId) ?? title;
    const p = obj(item.price), value = price(Number(p.value));
    if (seen.has(id) || value === null || p.currency !== currency || !sealedTitleMatches(title, card)) continue;
    seen.add(id); values.push(value);
  }
  const summary=comparableSummary(values,3);
  if (!summary) return null;
  return { ...summary, currency, source:"eBay sealed listings (asking, shipping excluded)", fetchedAt:new Date().toISOString() };
}
export async function sealedPrice(req: Request, env: Env): Promise<Response> {
  const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json" } });
  if (Number(req.headers.get("content-length") ?? 0) > 4000) return json({error:"body_too_large"},413);
  let o: Record<string, unknown>;
  try { o = obj(await req.json()); } catch { return json({error:"invalid_json"},400); }
  const game = str(o.game)?.toLowerCase(), language = str(o.language)?.toUpperCase();
  const name = str(o.name)?.trim(), productId = str(o.productId);
  if (!["pokemon","one_piece"].includes(game ?? "") || !["EN","DE","JA"].includes(language ?? "") || !name || name.length > 200 || !productId || !/^-?\d{1,16}$/.test(productId)) return json({error:"invalid_product"},400);
  const aliases = Array.isArray(o.aliases) ? o.aliases.filter((v): v is string => typeof v === "string" && v.length <= 100).slice(0, 12) : [];
  const card = { game, name, language, productId, aliases, market:o.market === "DE" || o.market === "US" ? o.market : undefined } as SealedRequest;
  const key = `sealed:v4:${game}:${productId}:${language}:${card.market ?? "default"}:${name}`;
  const cache = new Cache(env.DB), now = Date.now();
  const entry = (await cache.getMany<SealedPrice | null>([key])).get(key);
  if (entry && entry.expiresAt > now) return json({price:entry.value,reason:entry.value ? null : "not_found"});
  const auth = env.EBAY_CLIENT_ID && env.EBAY_CLIENT_SECRET ? `${env.EBAY_CLIENT_ID}:${env.EBAY_CLIENT_SECRET}` : null;
  if (!auth) return json({price:entry?.value ? {...entry.value,stale:true} : null,reason:"provider_not_configured"});
  const budgets = await new Budgets(env.DB,env).load();
  if (!(await budgets.reserve("ebay",1))) return json({price:entry?.value ? {...entry.value,stale:true} : null,reason:"budget_exhausted"});
  try {
    const languageWord = {EN:"English",DE:"Deutsch",JA:"Japanese"}[card.language];
    const localized = card.language === "DE" ? aliases.find(a => !/^[A-Z]+[- ]?\d+[A-Za-z]*$/.test(a) && !/[\u3040-\u30ff\u4e00-\u9fff]/.test(a) && !normalized(name).includes(normalized(a))) : undefined;
    const q = [card.game === "pokemon" ? "Pokemon" : "One Piece", localized ?? name.replace(/\([^)]*\)/g,""), languageWord, card.language === "DE" ? "OVP" : "sealed"].join(" ");
    const params = new URLSearchParams({q,filter:"buyingOptions:{FIXED_PRICE}",limit:"100"});
    const data = await fetchJson("ebay",`https://api.ebay.com/buy/browse/v1/item_summary/search?${params}`,{headers:{Authorization:`Bearer ${await accessToken(auth)}`,"X-EBAY-C-MARKETPLACE-ID":marketplace(card.language,card.market).site}});
    const result = parseSealedListings(data,card);
    await cache.putMany([{key,value:result,source:result?.source ?? null,fetchedAt:now,ttlMs:result ? 86_400_000 : 3_600_000}]);
    return json({price:result,reason:result ? null : "not_found"});
  } catch { return json({price:entry?.value ? {...entry.value,stale:true} : null,reason:"unavailable"}); }
}
