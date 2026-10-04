// POST /v1/prices: cache first, then providers in order, batched, within budget.

import type { CacheEntry } from "./cache";
import type { CardRequest, Conditions, Game, GradedPrice, ProviderName, RawPrice } from "./types";
import { QuotaError } from "./types";
import { chunk } from "./util";

export const GAMES: Game[] = ["pokemon", "one_piece", "magic", "dragon_ball_fw", "dragon_ball_super", "union_arena", "weiss_schwarz", "naruto"];
export const MAX_CARDS = 50;

// ---------- Request validation ----------

const text = (v: unknown, max = 200) => (typeof v === "string" ? v.trim().slice(0, max) : typeof v === "number" ? String(v) : "");

/** Cache key: game + best identifier (TCGplayer id when known) + printing. */
export function cacheKey(c: Omit<CardRequest, "key">): string {
  const id = c.tcgplayerId ? `tcg${c.tcgplayerId}` : `id${c.id}`;
  const printing = c.printing ? `:${c.printing.toLowerCase().replace(/[^a-z0-9]/g, "")}` : "";
  const language = c.language && c.language !== "EN" ? `@${c.language}` : "";
  return `${c.game}:${id}${printing}${language}`;
}

/** Checks one card from the request body. Returns null when it is unusable. */
export function parseCard(v: unknown): CardRequest | null {
  if (!v || typeof v !== "object") return null;
  const o = v as Record<string, unknown>;
  const game = text(o.game, 40).toLowerCase() as Game;
  if (!GAMES.includes(game)) return null;
  const id = text(o.id, 100);
  const tcg = text(o.tcgplayerId, 20);
  const card: Omit<CardRequest, "key"> = {
    game,
    id,
    name: text(o.name),
    set: text(o.set),
    number: text(o.number, 40),
    ...(/^\d+$/.test(tcg) ? { tcgplayerId: tcg } : {}),
    ...(text(o.printing, 60) ? { printing: text(o.printing, 60) } : {}),
    ...(text(o.localName, 120) ? { localName: text(o.localName, 120) } : {}),
    ...(typeof o.language === "string" && /^[A-Z]{2}$/.test(o.language) ? { language: o.language } : {}),
    ...(o.graded === true ? { graded: true } : {}),
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
): Promise<ChainOutcome<T>> {
  const found = new Map<string, T>();
  const unchecked = new Set<string>(); // a provider that supports the card could not ask about it

  for (const { provider, key } of providers) {
    const pending = cards.filter((c) => !found.has(c.key) && provider.supports(c));
    if (pending.length === 0) continue;
    const batches = chunk(pending, provider.batchSize);
    const wanted = Math.min(batches.length, Math.max(0, calls.left));
    const granted = wanted > 0 ? await gate.reserve(provider.name, wanted) : 0;
    calls.left -= granted;
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
          if (v !== undefined) found.set(c.key, v);
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
    if (granted > used) await gate.refund(provider.name, granted - used);
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

// ---------- The whole /v1/prices flow ----------

export interface CardPrice {
  key: string;
  conditions: Conditions | null;
  market: number | null;
  currency: string;
  graded: GradedPrice[];
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
  const unique = [...new Map(cards.map((c) => [c.key, c])).values()];
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
  const graded = await runChain(gradedMiss, d.graded, d.gate, calls, d.wait);
  const rawMiss = unique.filter((c) => !fresh(rawKey(c)));
  const raw = await runChain(rawMiss, d.raw, d.gate, calls, d.wait);

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
      writes.push({ key: gradedKey(c), value: g ?? [], source: g?.[0]?.source ?? null, fetchedAt: d.now, ttlMs: d.gradedTtlMs });
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
    if (c.graded) {
      const g = graded.found.get(c.key);
      if (g) gradedList = g;
      else if (!graded.notFound.has(c.key)) {
        const e = cached.get(gradedKey(c)) as CacheEntry<GradedPrice[]> | undefined;
        if (e) {
          gradedList = Array.isArray(e.value) ? e.value : [];
          gradedStale = e.expiresAt <= d.now;
        }
      }
    }

    const p = rawEntry?.value.price ?? null;
    let reason: CardPrice["reason"] = null;
    if (!p) {
      if (rawEntry) reason = "not_found";
      else if (raw.unsupported.has(c.key)) reason = "unsupported";
      else reason = "unavailable";
    }
    return {
      key: c.key,
      conditions: p ? p.conditions : null,
      market: p ? p.market : null,
      currency: p?.currency ?? "USD",
      graded: gradedList,
      source: p ? p.source : null,
      fetchedAt: rawEntry ? new Date(rawEntry.fetchedAt).toISOString() : null,
      stale: (rawEntry?.stale ?? false) || gradedStale,
      reason,
    };
  });
}
