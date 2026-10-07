// POST /v1/prices: cache first, then providers in order, batched, within budget.

import type { CacheEntry } from "./cache";
import type { CardRequest, Conditions, Game, GradedPrice, ProviderName, RawPrice } from "./types";
import { QuotaError } from "./types";
import { chunk, hasAnyCondition } from "./util";

export const GAMES: Game[] = ["pokemon", "one_piece", "magic", "dragon_ball_fw", "dragon_ball_super", "union_arena", "weiss_schwarz", "naruto"];
export const MAX_CARDS = 50;

// ---------- Request validation ----------

const text = (v: unknown, max = 200) => (typeof v === "string" ? v.trim().slice(0, max) : typeof v === "number" ? String(v) : "");

/** Cache key: game + best identifier (TCGplayer id when known) + printing. */
export function cacheKey(c: Omit<CardRequest, "key">): string {
  const id = c.tcgplayerId ? `tcg${c.tcgplayerId}` : `id${c.id}`;
  const printing = c.printing ? `:${c.printing.toLowerCase().replace(/[^a-z0-9]/g, "")}` : "";
  const language = c.language && c.language !== "EN" ? `@${c.language}` : "";
  // Version the cache after fixing language, printing and qualified-grade matching.
  const slab = c.graded && c.grader ? `:slab:${c.grader}:${c.grade ?? "all"}` : "";
  return `v7:${c.game}:${id}${printing}${language}:${c.market ?? "default"}${c.printingUnique ? ":single" : ""}${slab}`;
}

/** Checks one card from the request body. Returns null when it is unusable. */
export function parseCard(v: unknown): CardRequest | null {
  if (!v || typeof v !== "object") return null;
  const o = v as Record<string, unknown>;
  const game = text(o.game, 40).toLowerCase() as Game;
  if (!GAMES.includes(game)) return null;
  const id = text(o.id, 100);
  const tcg = text(o.tcgplayerId, 20);
  const language = text(o.language, 10).toUpperCase();
  if (o.language !== undefined && !/^[A-Z]{2}$/.test(language)) return null;
  const card: Omit<CardRequest, "key"> = {
    game,
    id,
    name: text(o.name),
    set: text(o.set),
    number: text(o.number, 40),
    setAliases: Array.isArray(o.setAliases) ? o.setAliases.filter((a): a is string => typeof a === "string" && a.length <= 100).slice(0,8) : [],
    ...( /^\d{4}$/.test(text(o.releaseYear)) ? {releaseYear:text(o.releaseYear)} : {}),
    ...(/^\d+$/.test(tcg) ? { tcgplayerId: tcg } : {}),
    ...(o.printingUnique === true ? {printingUnique:true} : {}),
    ...(text(o.printing, 60) ? { printing: text(o.printing, 60) } : {}),
    ...(text(o.localName, 120) ? { localName: text(o.localName, 120) } : {}),
    ...(language ? { language } : {}),
    ...(o.market === "US" || o.market === "DE" ? {market:o.market} : {}),
    ...(o.graded === true ? { graded: true } : {}),
    ...(o.graded === true && o.gradedOnly === true ? {gradedOnly:true} : {}),
    ...(o.graded === true && /^(PSA|BGS|CGC|SGC|TAG|ACE|AOG|GSG|PI)$/.test(text(o.grader).toUpperCase()) ? {grader:text(o.grader).toUpperCase()} : {}),
    ...(o.graded === true && /^(10|[1-9](?:\.5)?)$/.test(text(o.grade)) ? {grade:text(o.grade)} : {}),
  };
  if (!card.id && !card.tcgplayerId) return null;
  return { ...card, key: cacheKey(card) };
}

// ---------- Provider chain ----------

/** What the chain needs from the budget store (the real one is Budgets in budget.ts). */
export interface Gate {
  reserve(p: ProviderName, calls: number): Promise<number>;
  refund(p: ProviderName, calls: number): Promise<void>;
  block(p: ProviderName): Promise<void>;
}

export interface ChainProvider<T> {
  name: ProviderName;
  batchSize: number;
  /** HTTP search calls made by one batch (OAuth token acquisition has separate headroom). */
  callsPerBatch?: number;
  minIntervalMs?: number;
  supports(card: CardRequest): boolean;
  fetch(cards: CardRequest[], key: string): Promise<Map<string, T>>;
}

export interface ChainOutcome<T> {
  found: Map<string, T>;
  /** Every provider that could look the card up did, and none had it. Safe to cache as "not found". */
  notFound: Set<string>;
  /** No configured provider can look this card up (wrong game or no usable identifier). */
  unsupported: Set<string>;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Tries providers in order. Each provider gets only the cards still missing that it supports,
 * grouped into batches of its batch size. Budget is reserved before calling and unused calls are
 * given back. A failing or rate-limited provider just hands its cards to the next one.
 * `calls.left` caps the provider calls of this one request (Cloudflare subrequest limit).
 */
export async function runChain<T>(
  cards: CardRequest[],
  providers: { provider: ChainProvider<T>; key: string }[],
  gate: Gate,
  calls: { left: number },
  wait: (ms: number) => Promise<unknown> = sleep,
  merge?: (previous: T | undefined, next: T) => T,
  continueWhen?: (value: T) => boolean,
): Promise<ChainOutcome<T>> {
  const found = new Map<string, T>();
  const unchecked = new Set<string>(); // a provider that supports the card could not ask about it

  for (const { provider, key } of providers) {
    const pending = cards.filter((c) => (merge || !found.has(c.key) || continueWhen?.(found.get(c.key)!)) && provider.supports(c));
    if (pending.length === 0) continue;
    const batches = chunk(pending, provider.batchSize);
    const cost = provider.callsPerBatch ?? 1;
    const wanted = Math.min(batches.length, Math.floor(Math.max(0, calls.left) / cost)) * cost;
    const reserved = wanted > 0 ? await gate.reserve(provider.name, wanted) : 0;
    const granted = Math.floor(reserved / cost);
    const remainder = reserved - granted * cost;
    if (remainder > 0) await gate.refund(provider.name, remainder);
    calls.left -= granted * cost;
    let used = 0;
    let stop = false;
    for (let i = 0; i < batches.length; i++) {
      const batch = batches[i];
      if (stop || i >= granted) {
        batch.forEach((c) => unchecked.add(c.key));
        continue;
      }
      if (used > 0 && provider.minIntervalMs) await wait(provider.minIntervalMs);
      used++;
      try {
        const result = await provider.fetch(batch, key);
        for (const c of batch) {
          const v = result.get(c.key);
          if (v !== undefined) found.set(c.key, merge ? merge(found.get(c.key), v) : v);
        }
      } catch (e) {
        batch.forEach((c) => unchecked.add(c.key));
        if (e instanceof QuotaError) {
          stop = true;
          if (e.untilTomorrow) await gate.block(provider.name);
        }
        console.log(`provider error: ${e instanceof Error ? e.message : "unknown"}`);
      }
    }
    if (granted > used) await gate.refund(provider.name, (granted - used) * cost);
  }

  const notFound = new Set<string>();
  const unsupported = new Set<string>();
  for (const c of cards) {
    if (found.has(c.key)) continue;
    if (!providers.some(({ provider }) => provider.supports(c))) unsupported.add(c.key);
    else if (!unchecked.has(c.key)) notFound.add(c.key);
  }
  return { found, notFound, unsupported };
}

/** Keep the preferred provider for each grader/grade/qualifier, fill gaps from later ones. */
export function mergeGraded(previous: GradedPrice[] = [], next: GradedPrice[]): GradedPrice[] {
  const byGrade = new Map<string, GradedPrice>();
  for (const g of [...previous, ...next]) {
    const key = `${g.grader.toUpperCase()}|${g.grade}|${g.qualifier ?? ""}|${g.currency}`;
    if (!byGrade.has(key)) byGrade.set(key, g);
  }
  return [...byGrade.values()];
}

// ---------- The whole /v1/prices flow ----------

export interface CardPrice {
  key: string;
  conditions: Conditions | null;
  market: number | null;
  currency: string;
  graded: GradedPrice[];
  gradedFetchedAt?: string | null;
  gradedStale?: boolean;
  gradedReason: "not_found" | "unsupported" | "unavailable" | null;
  source: string | null;
  fetchedAt: string | null;
  stale: boolean;
  reason: "not_found" | "unsupported" | "unavailable" | null;
}

/** What is stored in the cache for raw prices; price null = "no provider has this card". */
interface RawCacheValue {
  price: RawPrice | null;
}

export interface CacheLike {
  getMany<T>(keys: string[]): Promise<Map<string, CacheEntry<T>>>;
  putMany(entries: { key: string; value: unknown; source: string | null; fetchedAt: number; ttlMs: number }[]): Promise<void>;
}

export interface PriceDeps {
  cache: CacheLike;
  gate: Gate;
  raw: { provider: ChainProvider<RawPrice>; key: string }[];
  graded: { provider: ChainProvider<GradedPrice[]>; key: string }[];
  rawTtlMs: number;
  gradedTtlMs: number;
  notFoundTtlMs: number;
  maxCalls: number;
  now: number;
  wait?: (ms: number) => Promise<unknown>;
}

const HOUR = 3_600_000;
export const hours = (h: number) => h * HOUR;

export async function getPrices(cards: CardRequest[], d: PriceDeps): Promise<CardPrice[]> {
  // The same card may be listed twice; only look it up once.
  const byKey = new Map<string, CardRequest>();
  for (const c of cards) {
    const previous = byKey.get(c.key);
    byKey.set(c.key, { ...c, graded: c.graded || previous?.graded,
      gradedOnly: c.gradedOnly === true && (!previous || previous.gradedOnly === true) });
  }
  const unique = [...byKey.values()];
  const rawKey = (c: CardRequest) => `raw|${c.key}`;
  const gradedKey = (c: CardRequest) => `graded|${c.key}`;

  const cached = await d.cache.getMany<unknown>([...unique.map(rawKey), ...unique.filter((c) => c.graded).map(gradedKey)]);
  const fresh = (k: string) => {
    const e = cached.get(k);
    return e && e.expiresAt > d.now ? e : undefined;
  };

  const calls = { left: d.maxCalls };
  // Graded first: those are the cards people care most about, and their providers are scarcer.
  const gradedMiss = unique.filter((c) => c.graded && !fresh(gradedKey(c)));
  const graded = await runChain(gradedMiss, d.graded, d.gate, calls, d.wait, mergeGraded);
  const rawMiss = unique.filter((c) => !c.gradedOnly && !fresh(rawKey(c)));
  const raw = await runChain(rawMiss, d.raw, d.gate, calls, d.wait, undefined, p => !hasAnyCondition(p.conditions));

  // Store what we learned.
  const writes: Parameters<CacheLike["putMany"]>[0] = [];
  for (const c of rawMiss) {
    const p = raw.found.get(c.key);
    if (p) writes.push({ key: rawKey(c), value: { price: p } satisfies RawCacheValue, source: p.source, fetchedAt: d.now, ttlMs: d.rawTtlMs });
    else if (raw.notFound.has(c.key))
      writes.push({ key: rawKey(c), value: { price: null } satisfies RawCacheValue, source: null, fetchedAt: d.now, ttlMs: d.notFoundTtlMs });
  }
  for (const c of gradedMiss) {
    const g = graded.found.get(c.key);
    if (g || graded.notFound.has(c.key))
      writes.push({ key: gradedKey(c), value: g ?? [], source: g?.[0]?.source ?? null, fetchedAt: d.now, ttlMs: g ? d.gradedTtlMs : d.notFoundTtlMs });
  }
  await d.cache.putMany(writes);

  return cards.map((c) => {
    // Raw part: new result, else cached (fresh or, failing that, stale).
    let rawEntry: { value: RawCacheValue; fetchedAt: number; stale: boolean } | undefined;
    const got = raw.found.get(c.key);
    if (got) rawEntry = { value: { price: got }, fetchedAt: d.now, stale: false };
    else if (raw.notFound.has(c.key)) rawEntry = { value: { price: null }, fetchedAt: d.now, stale: false };
    else {
      const e = cached.get(rawKey(c)) as CacheEntry<RawCacheValue> | undefined;
      if (e) rawEntry = { value: e.value, fetchedAt: e.fetchedAt, stale: e.expiresAt <= d.now };
    }

    let gradedList: GradedPrice[] = [];
    let gradedStale = false;
    let gradedFetchedAt: string | null = null;
    if (c.graded) {
      const g = graded.found.get(c.key);
      if (g) { gradedList = g; gradedFetchedAt = new Date(d.now).toISOString(); }
      else if (!graded.notFound.has(c.key)) {
        const e = cached.get(gradedKey(c)) as CacheEntry<GradedPrice[]> | undefined;
        if (e) {
          gradedList = Array.isArray(e.value) ? e.value : [];
          gradedStale = e.expiresAt <= d.now;
          gradedFetchedAt = new Date(e.fetchedAt).toISOString();
        }
      }
    }

    const p = rawEntry?.value.price ?? null;
    let reason: CardPrice["reason"] = null;
    if (!p && !c.gradedOnly) {
      if (rawEntry) reason = "not_found";
      else if (raw.unsupported.has(c.key)) reason = "unsupported";
      else reason = "unavailable";
    }
    return {
      key: c.key,
      conditions: p ? p.conditions : null,
      market: p ? p.market : null,
      ...(p?.conditionEvidence ? {conditionEvidence:p.conditionEvidence} : {}),
      currency: p?.currency ?? "USD",
      ...(p?.listings ? { listings: p.listings, low: p.low, high: p.high } : {}),
      graded: gradedList.map(g=>({...g,stale:gradedStale,fetchedAt:gradedFetchedAt ?? undefined})),
      gradedFetchedAt, gradedStale,
      gradedReason: !c.graded || gradedList.length ? null
        : graded.notFound.has(c.key) || fresh(gradedKey(c)) ? "not_found"
        : graded.unsupported.has(c.key) ? "unsupported" : "unavailable",
      source: p ? p.source : null,
      fetchedAt: rawEntry ? new Date(rawEntry.fetchedAt).toISOString() : null,
      stale: rawEntry?.stale ?? false,
      reason,
    };
  });
}

/** Legacy apps ignore qualifier fields and must never see premium grades as ordinary 10s. */
export function gradedForSchema(grades: GradedPrice[], schemaVersion: unknown): GradedPrice[] {
  return schemaVersion === 2 ? grades : grades.filter((g) => !g.qualifier);
}
