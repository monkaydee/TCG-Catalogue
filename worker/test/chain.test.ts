import { describe, expect, it } from "vitest";
import type { CacheEntry } from "../src/cache";
import { gradedForSchema, getPrices, parseCard, runChain, type CacheLike, type ChainProvider, type Gate } from "../src/prices";
import { QuotaError, type CardRequest, type GradedPrice, type ProviderName, type RawPrice } from "../src/types";
import { chunk, emptyConditions } from "../src/util";

// ---------- Fakes ----------

/** Budget gate with fixed calls left per provider, recording what happened. */
function fakeGate(left: Partial<Record<ProviderName, number>>) {
  const log: string[] = [];
  const gate: Gate & { left: Partial<Record<ProviderName, number>>; log: string[] } = {
    left,
    log,
    async reserve(p, n) {
      const g = Math.min(n, left[p] ?? 0);
      left[p] = (left[p] ?? 0) - g;
      log.push(`reserve ${p} ${n}->${g}`);
      return g;
    },
    async refund(p, n) {
      left[p] = (left[p] ?? 0) + n;
      log.push(`refund ${p} ${n}`);
    },
    async block(p) {
      left[p] = 0;
      log.push(`block ${p}`);
    },
  };
  return gate;
}

const raw = (source: string, nm: number): RawPrice => ({ conditions: { ...emptyConditions(), NM: nm }, market: null, source });

/** A provider that knows the cards whose key is in `knows`, and records each batch it got. */
function fakeProvider(
  name: ProviderName,
  batchSize: number,
  knows: (c: CardRequest) => boolean,
  opts: { supports?: (c: CardRequest) => boolean; fail?: Error } = {},
) {
  const batches: string[][] = [];
  const p: ChainProvider<RawPrice> & { batches: string[][] } = {
    name,
    batchSize,
    batches,
    supports: opts.supports ?? (() => true),
    async fetch(cards) {
      batches.push(cards.map((c) => c.key));
      if (opts.fail) throw opts.fail;
      return new Map(cards.filter(knows).map((c) => [c.key, raw(name, 1)]));
    },
  };
  return p;
}

const cards = (n: number, extra: Record<string, unknown> = {}) =>
  Array.from({ length: n }, (_, i) => parseCard({ game: "pokemon", id: `c${i}`, tcgplayerId: String(1000 + i), name: `Card ${i}`, ...extra })!);

const noWait = async () => {};

// ---------- Batching ----------

describe("batching", () => {
  it("chunks lists", () => {
    expect(chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
    expect(chunk([], 20)).toEqual([]);
  });

  it("asks JustTCG for 45 cards in 3 calls of 20, 20 and 5", async () => {
    const jt = fakeProvider("justtcg", 20, () => true);
    const gate = fakeGate({ justtcg: 90 });
    const out = await runChain(cards(45), [{ provider: jt, key: "k" }], gate, { left: 20 }, noWait);
    expect(jt.batches.map((b) => b.length)).toEqual([20, 20, 5]);
    expect(out.found.size).toBe(45);
    expect(gate.left.justtcg).toBe(87);
  });
});

// ---------- Provider selection and fallback ----------

describe("provider chain", () => {
  it("sends only the cards the first provider did not find to the next one", async () => {
    const list = cards(4);
    const jt = fakeProvider("justtcg", 20, (c) => c.id === "c0" || c.id === "c1");
    const ta = fakeProvider("tcgapi", 1, (c) => c.id === "c2");
    const gate = fakeGate({ justtcg: 10, tcgapi: 10 });
    const out = await runChain(list, [{ provider: jt, key: "a" }, { provider: ta, key: "b" }], gate, { left: 20 }, noWait);
    expect(jt.batches).toEqual([list.map((c) => c.key)]);
    expect(ta.batches).toEqual([[list[2].key], [list[3].key]]);
    expect(out.found.get(list[0].key)?.source).toBe("justtcg");
    expect(out.found.get(list[2].key)?.source).toBe("tcgapi");
    expect([...out.notFound]).toEqual([list[3].key]); // both providers answered "don't know it"
  });

  it("moves on to the next provider when a budget is used up", async () => {
    const list = cards(3);
    const jt = fakeProvider("justtcg", 20, () => true);
    const ta = fakeProvider("tcgapi", 1, () => true);
    const gate = fakeGate({ justtcg: 0, tcgapi: 2 });
    const out = await runChain(list, [{ provider: jt, key: "a" }, { provider: ta, key: "b" }], gate, { left: 20 }, noWait);
    expect(jt.batches).toEqual([]);
    expect(ta.batches.length).toBe(2);
    expect(out.found.size).toBe(2);
    // The third card was never checked: it must not be cached as "not found".
    expect(out.notFound.size).toBe(0);
  });

  it("skips providers that do not support a card and reports unsupported cards", async () => {
    const poke = cards(1)[0];
    const naruto = parseCard({ game: "naruto", id: "N-1", name: "Naruto" })!; // no TCGplayer id
    const pt = fakeProvider("poketrace", 20, () => true, { supports: (c) => c.game === "pokemon" });
    const out = await runChain([poke, naruto], [{ provider: pt, key: "k" }], fakeGate({ poketrace: 5 }), { left: 20 }, noWait);
    expect(pt.batches).toEqual([[poke.key]]);
    expect([...out.unsupported]).toEqual([naruto.key]);
  });

  it("on a daily quota error blocks the provider, refunds unused calls and falls back", async () => {
    const list = cards(25);
    const jt = fakeProvider("justtcg", 20, () => true, { fail: new QuotaError("justtcg", "HTTP 429", true) });
    const pt = fakeProvider("poketrace", 20, () => true);
    const gate = fakeGate({ justtcg: 10, poketrace: 10 });
    const out = await runChain(list, [{ provider: jt, key: "a" }, { provider: pt, key: "b" }], gate, { left: 20 }, noWait);
    expect(jt.batches.length).toBe(1); // stopped after the first failure
    expect(gate.log).toContain("block justtcg");
    expect(gate.log).toContain("refund justtcg 1");
    expect(out.found.size).toBe(25);
    expect(out.found.get(list[0].key)?.source).toBe("poketrace");
  });

  it("a burst-limit 429 stops the provider for this request only", async () => {
    const jt = fakeProvider("justtcg", 20, () => true, { fail: new QuotaError("justtcg", "HTTP 429", false) });
    const gate = fakeGate({ justtcg: 10 });
    const out = await runChain(cards(2), [{ provider: jt, key: "a" }], gate, { left: 20 }, noWait);
    expect(gate.log).not.toContain("block justtcg");
    expect(out.notFound.size).toBe(0);
  });

  it("a network error is not 'not found'", async () => {
    const jt = fakeProvider("justtcg", 20, () => true, { fail: new Error("boom") });
    const out = await runChain(cards(2), [{ provider: jt, key: "a" }], fakeGate({ justtcg: 10 }), { left: 20 }, noWait);
    expect(out.found.size).toBe(0);
    expect(out.notFound.size).toBe(0);
  });

  it("caps provider calls per request", async () => {
    const ta = fakeProvider("tcgapi", 1, () => true);
    const calls = { left: 3 };
    await runChain(cards(10), [{ provider: ta, key: "a" }], fakeGate({ tcgapi: 90 }), calls, noWait);
    expect(ta.batches.length).toBe(3);
    expect(calls.left).toBe(0);
  });

  it("waits between calls for providers with a burst limit", async () => {
    const pt = { ...fakeProvider("poketrace", 20, () => true), minIntervalMs: 2100 };
    const waits: number[] = [];
    await runChain(cards(45), [{ provider: pt, key: "a" }], fakeGate({ poketrace: 10 }), { left: 20 }, async (ms) => waits.push(ms));
    expect(waits).toEqual([2100, 2100]);
  });
});

// ---------- Whole flow with cache ----------

function memoryCache(initial: Record<string, CacheEntry<unknown>> = {}) {
  const store = new Map(Object.entries(initial));
  const c: CacheLike & { store: typeof store } = {
    store,
    async getMany<T>(keys: string[]) {
      return new Map(keys.filter((k) => store.has(k)).map((k) => [k, store.get(k) as CacheEntry<T>]));
    },
    async putMany(entries) {
      for (const e of entries) store.set(e.key, { value: e.value, source: e.source, fetchedAt: e.fetchedAt, expiresAt: e.fetchedAt + e.ttlMs });
    },
  };
  return c;
}

const NOW = Date.parse("2026-10-03T12:00:00Z");
const H = 3_600_000;

function deps(cache: CacheLike, rawProviders: ChainProvider<RawPrice>[], gate: Gate, gradedProviders: ChainProvider<GradedPrice[]>[] = []) {
  return {
    cache,
    gate,
    raw: rawProviders.map((provider) => ({ provider, key: "k" })),
    graded: gradedProviders.map((provider) => ({ provider, key: "k" })),
    rawTtlMs: 24 * H,
    gradedTtlMs: 72 * H,
    notFoundTtlMs: 72 * H,
    maxCalls: 20,
    now: NOW,
    wait: noWait,
  };
}

describe("getPrices", () => {
  it("fills CGC/BGS gaps after a PSA-only provider without replacing preferred PSA prices", async () => {
    const [c] = cards(1, { graded: true });
    const grade = (grader: string, amount: number): GradedPrice => ({ grader, grade: "10", price: amount, currency: "USD", source: "test" });
    const provider = (name: ProviderName, values: GradedPrice[]): ChainProvider<GradedPrice[]> => ({
      name, batchSize: 1, supports: () => true, async fetch(list) { return new Map(list.map((c) => [c.key, values])); },
    });
    const [r] = await getPrices([c], deps(memoryCache(), [], fakeGate({ ppt: 2, ebay: 2 }), [
      provider("ppt", [grade("PSA", 100)]),
      provider("ebay", [grade("PSA", 200), grade("CGC", 150), grade("BGS", 180)]),
    ]));
    expect(r.graded.map((g) => [g.grader, g.price])).toEqual([["PSA", 100], ["CGC", 150], ["BGS", 180]]);
  });

  it("retains a graded lookup when a duplicate raw request comes last", async () => {
    const [c] = cards(1, { graded: true });
    let calls = 0;
    const provider: ChainProvider<GradedPrice[]> = { name: "ppt", batchSize: 1, supports: () => true,
      async fetch(list) { calls++; return new Map(list.map((c) => [c.key, [{ grader: "PSA", grade: "9", price: 80, currency: "USD", source: "test" }]])); } };
    const out = await getPrices([c, { ...c, graded: false }], deps(memoryCache(), [], fakeGate({ ppt: 2 }), [provider]));
    expect(calls).toBe(1);
    expect(out[0].graded).toHaveLength(1);
    expect(out[1].graded).toHaveLength(0);
  });

  it("normalizes language codes and rejects malformed languages rather than pricing English", () => {
    expect(parseCard({ game: "pokemon", id: "c", language: "de" })?.language).toBe("DE");
    expect(parseCard({ game: "pokemon", id: "c", language: "German" })).toBeNull();
  });

  it("serves fresh cache without calling any provider", async () => {
    const [c] = cards(1);
    const cache = memoryCache({ [`raw|${c.key}`]: { value: { price: raw("justtcg", 7) }, source: "justtcg", fetchedAt: NOW - H, expiresAt: NOW + H } });
    const jt = fakeProvider("justtcg", 20, () => true);
    const [r] = await getPrices([c], deps(cache, [jt], fakeGate({ justtcg: 10 })));
    expect(jt.batches).toEqual([]);
    expect(r).toMatchObject({ key: c.key, conditions: { NM: 7 }, source: "justtcg", stale: false, reason: null });
  });

  it("fetches misses, stores them, and a second user gets them from cache", async () => {
    const list = cards(2);
    const cache = memoryCache();
    const jt = fakeProvider("justtcg", 20, () => true);
    const gate = fakeGate({ justtcg: 10 });
    await getPrices(list, deps(cache, [jt], gate));
    await getPrices(list, deps(cache, [jt], gate)); // another user, same cards
    expect(jt.batches.length).toBe(1);
    expect(cache.store.get(`raw|${list[0].key}`)?.expiresAt).toBe(NOW + 24 * H);
  });

  it("serves stale prices with stale:true when every budget is used up", async () => {
    const [c] = cards(1);
    const cache = memoryCache({ [`raw|${c.key}`]: { value: { price: raw("justtcg", 7) }, source: "justtcg", fetchedAt: NOW - 50 * H, expiresAt: NOW - 26 * H } });
    const [r] = await getPrices([c], deps(cache, [fakeProvider("justtcg", 20, () => true)], fakeGate({ justtcg: 0 })));
    expect(r).toMatchObject({ conditions: { NM: 7 }, stale: true, reason: null, fetchedAt: new Date(NOW - 50 * H).toISOString() });
  });

  it("returns null with a reason when there is nothing at all", async () => {
    const [c] = cards(1);
    const naruto = parseCard({ game: "naruto", id: "N-1" })!;
    const jt = fakeProvider("justtcg", 20, () => true, { supports: (x) => !!x.tcgplayerId });
    const res = await getPrices([c, naruto], deps(memoryCache(), [jt], fakeGate({ justtcg: 0 })));
    expect(res[0]).toMatchObject({ conditions: null, graded: [], reason: "unavailable", stale: false });
    expect(res[1]).toMatchObject({ conditions: null, reason: "unsupported" });
  });

  it("caches 'not found' so the card is not asked for again tomorrow", async () => {
    const [c] = cards(1);
    const cache = memoryCache();
    const jt = fakeProvider("justtcg", 20, () => false);
    const [r] = await getPrices([c], deps(cache, [jt], fakeGate({ justtcg: 10 })));
    expect(r.reason).toBe("not_found");
    expect(cache.store.get(`raw|${c.key}`)?.value).toEqual({ price: null });
  });

  it("keeps graded prices separate and never fills them from raw prices", async () => {
    const [c] = cards(1, { graded: true });
    const jt = fakeProvider("justtcg", 20, () => true);
    const gradedProvider: ChainProvider<GradedPrice[]> = {
      name: "ppt",
      batchSize: 1,
      supports: () => true,
      async fetch(list) {
        return new Map(list.map((x) => [x.key, [{ grader: "PSA", grade: "10", price: 99, currency: "USD", source: "pokemonpricetracker" }]]));
      },
    };
    const cache = memoryCache();
    const [r] = await getPrices([c], deps(cache, [jt], fakeGate({ justtcg: 5, ppt: 5 }), [gradedProvider]));
    expect(r.graded).toEqual([{ grader: "PSA", grade: "10", price: 99, currency: "USD", source: "pokemonpricetracker", stale:false, fetchedAt:new Date(NOW).toISOString() }]);
    expect(r.conditions?.NM).toBe(1);
    expect(cache.store.get(`graded|${c.key}`)?.expiresAt).toBe(NOW + 72 * H);

    // No graded provider has data: graded stays empty even though a raw price exists.
    const [d] = cards(1, { graded: true, id: "other", tcgplayerId: "77" });
    const [r2] = await getPrices([d], deps(memoryCache(), [jt], fakeGate({ justtcg: 5 })));
    expect(r2.graded).toEqual([]);
    expect(r2.conditions?.NM).toBe(1);
  });

  it("looks up a card listed twice only once", async () => {
    const [c] = cards(1);
    const jt = fakeProvider("justtcg", 20, () => true);
    const res = await getPrices([c, c], deps(memoryCache(), [jt], fakeGate({ justtcg: 10 })));
    expect(jt.batches).toEqual([[c.key]]);
    expect(res.length).toBe(2);
  });
});


describe("multi-search budgets", () => {
  it("reserves both searches and refunds an unusable partial batch", async () => {
    const provider = { ...fakeProvider("ebay", 1, () => true), callsPerBatch: 2 };
    const gate = fakeGate({ ebay: 3 });
    const calls = { left: 10 };
    const out = await runChain(cards(3), [{ provider, key: "k" }], gate, calls, noWait);
    expect(provider.batches).toHaveLength(1);
    expect(out.found.size).toBe(1);
    expect(gate.left.ebay).toBe(1);
    expect(calls.left).toBe(8);
  });
  it("does not launch two searches when the request only has one call left", async () => {
    const provider = { ...fakeProvider("ebay", 1, () => true), callsPerBatch: 2 };
    const gate = fakeGate({ ebay: 10 });
    const out = await runChain(cards(1), [{ provider, key: "k" }], gate, { left: 1 }, noWait);
    expect(provider.batches).toHaveLength(0);
    expect(out.notFound.size).toBe(0);
  });
});


describe("graded availability", () => {
  it("distinguishes exhausted budgets, confirmed absence and unsupported cards", async () => {
    const [c] = cards(1, { graded: true });
    const provider: ChainProvider<GradedPrice[]> = { name: "ppt", batchSize: 1, supports: () => true,
      async fetch() { return new Map(); } };
    const [unavailable] = await getPrices([c], deps(memoryCache(), [], fakeGate({ ppt: 0 }), [provider]));
    expect(unavailable.gradedReason).toBe("unavailable");
    const cache = memoryCache();
    const [missing] = await getPrices([c], deps(cache, [], fakeGate({ ppt: 1 }), [provider]));
    expect(missing.gradedReason).toBe("not_found");
    const [cached] = await getPrices([c], deps(cache, [], fakeGate({ ppt: 0 }), [provider]));
    expect(cached.gradedReason).toBe("not_found");
    const [unsupported] = await getPrices([c], deps(memoryCache(), [], fakeGate({})));
    expect(unsupported.gradedReason).toBe("unsupported");
  });
});


it("hides premium grades from legacy clients that ignore qualifiers", () => {
  const ordinary: GradedPrice = { grader: "BGS", grade: "10", price: 100, currency: "USD", source: "test" };
  const premium = { ...ordinary, qualifier: "Black Label", price: 1000 };
  expect(gradedForSchema([premium, ordinary], undefined)).toEqual([ordinary]);
  expect(gradedForSchema([premium, ordinary], 2)).toEqual([premium, ordinary]);
});
