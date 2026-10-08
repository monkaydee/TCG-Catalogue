import { describe, expect, it } from "vitest";
import type { CacheEntry } from "../src/cache";
import { getPrices, parseCard, type CacheLike, type ChainProvider, type Gate } from "../src/prices";
import { parseEbay, parseEbayRaw, titleMatches } from "../src/providers/ebay";
import { type CardRequest, type RawPrice } from "../src/types";
import { emptyConditions } from "../src/util";

const flareon: CardRequest = { game: "pokemon", id: "base2-3", name: "Flareon", set: "Jungle", number: "3/64", key: "k" };
const rows = (title: string, values: number[]) => ({ itemSummaries: values.map((v, i) => ({ itemId: `${title}-${i}`, title, price: { value: String(v), currency: "USD" } })) });

describe("multi-copy eBay listings", () => {
  it("never price one raw card or one slab from a listing of several", () => {
    expect(parseEbayRaw(rows("2x Flareon 3/64 Jungle Holo English NM", [100, 100, 100]), flareon)).toBeNull();
    expect(parseEbay(rows("2x Flareon 3/64 Jungle Holo English PSA 9", [300, 300, 300]), flareon)).toEqual([]);
    for (const t of ["Flareon 3/64 Jungle English NM x3", "Flareon 3/64 Jungle English NM - 3 copies", "Set of 2 Flareon 3/64 Jungle English NM", "Flareon 3/64 Jungle English NM Playset"])
      expect(titleMatches(t, flareon), t).toBe(false);
  });
  it("still accepts single cards whose titles contain an x or a grade", () => {
    expect(titleMatches("Flareon 3/64 Jungle English NM 1x", flareon)).toBe(true);
    expect(titleMatches("Flareon 3/64 Jungle Holo English PSA 9", flareon)).toBe(true);
    const ex = { ...flareon, id: "xy12-13", name: "Charizard EX", set: "Evolutions", number: "12/108" };
    expect(titleMatches("Charizard EX 12/108 Evolutions English NM", ex)).toBe(true);
  });
});

describe("requested condition", () => {
  const memory = (): CacheLike => {
    const store = new Map<string, CacheEntry<unknown>>();
    return {
      async getMany<T>(keys: string[]) { return new Map(keys.flatMap((k) => (store.has(k) ? [[k, store.get(k) as CacheEntry<T>]] : []))); },
      async putMany(entries) { for (const e of entries) store.set(e.key, { value: e.value, source: e.source, fetchedAt: e.fetchedAt, expiresAt: e.fetchedAt + e.ttlMs }); },
    } as CacheLike;
  };
  const gate: Gate = { async reserve(_p, n) { return n; }, async refund() {}, async block() {} };
  const provider = (name: "justtcg" | "tcgapi", conditions: Partial<RawPrice["conditions"]>) => {
    const p: ChainProvider<RawPrice> & { calls: number } = { name, batchSize: 1, calls: 0, supports: () => true,
      async fetch(list) { p.calls++; return new Map(list.map((c) => [c.key, { conditions: { ...emptyConditions(), ...conditions }, market: null, source: name }])); } };
    return p;
  };
  const deps = (raw: ChainProvider<RawPrice>[]) => ({ cache: memory(), raw: raw.map((provider) => ({ provider, key: "k" })), graded: [], gate,
    rawTtlMs: 1, gradedTtlMs: 1, notFoundTtlMs: 1, maxCalls: 10, now: 0, wait: async () => {} });

  it("asks the next provider when the requested condition is missing", async () => {
    const nm = provider("justtcg", { NM: 100 }), lp = provider("tcgapi", { LP: 70 });
    const [r] = await getPrices([parseCard({ game: "pokemon", id: "c", condition: "LP" })!], deps([nm, lp]));
    expect(lp.calls).toBe(1);
    expect(r.conditions?.LP).toBe(70);
  });
  it("keeps the earlier table when a later provider also lacks the condition, and NM requests stop early", async () => {
    const nm = provider("justtcg", { NM: 100 }), other = provider("tcgapi", { NM: 90 });
    const [r] = await getPrices([parseCard({ game: "pokemon", id: "c", condition: "MP" })!], deps([nm, other]));
    expect(r.conditions?.NM).toBe(100);
    const nm2 = provider("justtcg", { NM: 100 }), later = provider("tcgapi", { LP: 70 });
    await getPrices([parseCard({ game: "pokemon", id: "c" })!], deps([nm2, later]));
    expect(later.calls).toBe(0);
  });
  it("caches each requested condition separately", () => {
    expect(parseCard({ game: "pokemon", id: "c", condition: "LP" })!.key).not.toBe(parseCard({ game: "pokemon", id: "c" })!.key);
    expect(parseCard({ game: "pokemon", id: "c", condition: "bogus" })!.condition).toBeUndefined();
  });
});
