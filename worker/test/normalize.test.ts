import { describe, expect, it } from "vitest";
import { cacheKey, parseCard } from "../src/prices";
import { justTcgIdentifier, parseJustTcgBatch, parseJustTcgGraded } from "../src/providers/justtcg";
import { parsePoketrace } from "../src/providers/poketrace";
import { parsePpt, splitGradeKey } from "../src/providers/ppt";
import { parsePsaCert, parsePsaSpecPop } from "../src/providers/psa";
import { parseRapidPop, parseRapidTcg } from "../src/providers/rapidapi";
import { parseTcgApi, tcgApiPrinting } from "../src/providers/tcgapi";
import { parseXimilar, toBase64, ximilarGame } from "../src/providers/ximilar";
import type { CardRequest } from "../src/types";
import { conditionCode, price } from "../src/util";
import justV1 from "./fixtures/justtcg-v1-batch.json";
import justV2 from "./fixtures/justtcg-v2-graded.json";
import poke from "./fixtures/poketrace-cards.json";
import pptCard from "./fixtures/ppt-card.json";
import psaCert from "./fixtures/psa-cert.json";
import psaPop from "./fixtures/psa-pop.json";
import rapidPop from "./fixtures/rapidapi-pop.json";
import rapidSearch from "./fixtures/rapidapi-search.json";
import tcgapi from "./fixtures/tcgapi-card.json";
import xim from "./fixtures/ximilar-tcg-id.json";

function card(o: Partial<CardRequest>): CardRequest {
  const c = parseCard({ game: "pokemon", id: "x", name: "Charizard", set: "Base Set", number: "4/102", ...o })!;
  return c;
}

describe("helpers", () => {
  it("parses prices tolerantly", () => {
    expect(price("1,234.567")).toBe(1234.57);
    expect(price("$3")).toBe(3);
    expect(price(0)).toBeNull();
    expect(price("N/A")).toBeNull();
    expect(price(null)).toBeNull();
  });
  it("maps condition labels", () => {
    expect(conditionCode("Near Mint")).toBe("NM");
    expect(conditionCode("NEAR_MINT")).toBe("NM");
    expect(conditionCode("Lightly Played Foil")).toBe("LP");
    expect(conditionCode("DAMAGED")).toBe("DMG");
    expect(conditionCode("MINT")).toBeNull();
    expect(conditionCode("PSA_10")).toBeNull();
  });
});

describe("request parsing and cache keys", () => {
  it("separates a targeted grade cache from other grades and the overview",()=>{
    const base={game:"pokemon",id:"base5-4",graded:true,printing:"1st Edition Holofoil"};
    const five=parseCard({...base,grader:"PSA",grade:"5"})!;
    expect(five.key).not.toBe(parseCard({...base,grader:"PSA",grade:"8"})!.key);
    expect(five.key).not.toBe(parseCard(base)!.key);
    expect(parseCard({...base,grader:"anything"})?.grader).toBeUndefined();
  });
  it("prefers the TCGplayer id in the key and includes the printing", () => {
    expect(card({ tcgplayerId: "42382", printing: "Reverse Holofoil" }).key).toBe("v7:pokemon:tcg42382:reverseholofoil:default");
    expect(cacheKey({ game: "magic", id: "abc", name: "", set: "", number: "" })).toBe("v7:magic:idabc:default");
  });
  it("accepts the app's enum names in any case and rejects unknown games", () => {
    expect(parseCard({ game: "ONE_PIECE", id: "OP01-024" })?.game).toBe("one_piece");
    expect(parseCard({ game: "yugioh", id: "1" })).toBeNull();
    expect(parseCard({ game: "pokemon" })).toBeNull();
    expect(parseCard({ game: "pokemon", id: "a", tcgplayerId: "not-a-number" })?.tcgplayerId).toBeUndefined();
  });
});

describe("JustTCG", () => {
  it("never substitutes another printing when the requested printing has no price", () => {
    const requested = card({ tcgplayerId: "42382", printing: "Reverse Holofoil" });
    expect(parseJustTcgBatch(justV1, [requested]).has(requested.key)).toBe(false);
  });
  it("keeps numeric grades and special qualifiers as separate fields", () => {
    const variants = [
      { type: "graded", language: "English", grading: { company: "CGC", grade: 10, grade_label: "Pristine" }, markets: [{ currency: "USD", price: 200 }] },
      { type: "graded", grading: { company: "BGS", grade: 10, grade_label: "Pristine" }, markets: [{ currency: "USD", price: 100 }] },
      { type: "graded", grading: { company: "BGS", grade: 10, grade_label: "Black Label" }, markets: [{ currency: "USD", price: 500 }] },
    ];
    const result = parseJustTcgGraded({ data: [{ variants }] }, card({}));
    expect(result[0]).toMatchObject({ grade: "10", qualifier: "Pristine" });
    expect(result[1].grade).toBe("10");
    expect(result[1].qualifier).toBeUndefined();
    expect(result[2]).toMatchObject({ grade: "10", qualifier: "Black Label" });
  });
  it("normalizes a v1 batch, matching cards by TCGplayer id and printing", () => {
    const holo = card({ tcgplayerId: "42382", printing: "Holofoil" });
    const first = card({ tcgplayerId: "42382", printing: "1st Edition Holofoil" });
    const missing = card({ tcgplayerId: "999" });
    const r = parseJustTcgBatch(justV1, [holo, first, missing]);
    expect(r.get(holo.key)).toEqual({ conditions: { NM: 420.5, LP: 310, MP: null, HP: null, DMG: 95.25 }, market: null, source: "justtcg" });
    expect(r.get(first.key)?.conditions.NM).toBe(9000);
    expect(r.has(missing.key)).toBe(false);
  });
  it("matches Magic cards by Scryfall id and skips non-English variants", () => {
    const lotus = parseCard({ game: "magic", id: "bd8fa327-dd41-4737-8f19-2cf5eb1f7cdd", name: "Black Lotus" })!;
    expect(justTcgIdentifier(lotus)).toEqual({ scryfallId: "bd8fa327-dd41-4737-8f19-2cf5eb1f7cdd" });
    const r = parseJustTcgBatch(justV1, [lotus]);
    expect(r.get(lotus.key)?.conditions).toEqual({ NM: null, LP: null, MP: null, HP: 15000, DMG: null });
  });
  it("reads only graded variants from v2, skipping qualified grades and missing prices", () => {
    const g = parseJustTcgGraded(justV2, card({ tcgplayerId: "42382", graded: true }));
    expect(g).toEqual([
      { grader: "PSA", grade: "10", price: 12500, currency: "USD", source: "justtcg", date: "2026-06-08" },
      { grader: "BGS", grade: "9.5", price: 4100.4, currency: "USD", source: "justtcg", date: "2026-06-08" },
    ]);
  });
});

describe("PokeTrace", () => {
  it("picks the requested variant and prefers TCGplayer over eBay, never graded tiers", () => {
    const rev = card({ tcgplayerId: "502000", printing: "Reverse Holofoil", name: "Pikachu" });
    const r = parsePoketrace(poke, [rev]).get(rev.key)!;
    expect(r.conditions).toEqual({ NM: 1.5, LP: 1.2, MP: null, HP: null, DMG: null });
    const normal = card({ tcgplayerId: "502000", printing: "Normal", name: "Pikachu" });
    expect(parsePoketrace(poke, [normal]).get(normal.key)!.conditions.DMG).toBe(0.05);
  });
});

describe("TCG API", () => {
  it("returns the market price by printing and leaves conditions empty", () => {
    const foil = parseCard({ game: "one_piece", id: "OP01-024", tcgplayerId: "453000", printing: "Parallel Foil" })!;
    expect(tcgApiPrinting("Holofoil")).toBe("Foil");
    expect(tcgApiPrinting("Normal")).toBe("Normal");
    expect(parseTcgApi(tcgapi, foil)).toEqual({
      conditions: { NM: null, LP: null, MP: null, HP: null, DMG: null },
      market: 31.2,
      source: "tcgapi",
    });
    const plain = parseCard({ game: "one_piece", id: "OP01-024", tcgplayerId: "453000" })!;
    expect(parseTcgApi(tcgapi, plain)?.market).toBe(2.47);
    expect(parseTcgApi(null, plain)).toBeNull();
  });
});

describe("RapidAPI", () => {
  it("uses a search result only when name, game and set or number settle the printing", () => {
    // Base Set Charizard: the Pokémon one in "Base Set", not Base Set 2 nor a Magic card.
    expect(parseRapidTcg(rapidSearch, card({}))?.market).toBe(430);
    // The number in the product name decides.
    expect(parseRapidTcg(rapidSearch, card({ name: "Charizard ex", set: "151", number: "199/165" }))?.market).toBe(128.4);
    expect(parseRapidTcg(rapidSearch, card({ name: "Blastoise" }))).toBeNull();
    // Several Charizards and nothing to tell them apart: no guess.
    expect(parseRapidTcg(rapidSearch, card({ set: "Some other set", number: "" }))).toBeNull();
    expect(parseRapidTcg({ results: [{ productName: "Charizard", productLineName: "Pokemon", setName: "Base Set", marketPrice: 5 }] }, card({}))?.market).toBe(5);
  });
  it("reads the PSA population response", () => {
    expect(parseRapidPop(rapidPop)).toEqual({
      total: 3800,
      higher: null,
      byGrade: { "10": 121, "9": 1534 },
      description: "1999 Pokemon Game Charizard-Holo",
      source: "rapidapi-psa",
    });
    expect(parseRapidPop({ message: "error" })).toBeNull();
  });
});

describe("PokemonPriceTracker", () => {
  it("splits grade keys", () => {
    expect(splitGradeKey("psa10")).toEqual({ grader: "PSA", grade: "10" });
    expect(splitGradeKey("cgc9_5")).toEqual({ grader: "CGC", grade: "9.5" });
    expect(splitGradeKey("unknownKey")).toBeNull();
  });
  it("reads graded eBay sales and skips grades without a price", () => {
    expect(parsePpt(pptCard)).toEqual([
      { grader: "PSA", grade: "10", price: 12650.55, currency: "USD", source: "pokemonpricetracker", date: "2026-10-01", sales: 12 },
      { grader: "PSA", grade: "9", price: 2050, currency: "USD", source: "pokemonpricetracker", date: "2026-10-01", sales: 40 },
      { grader: "CGC", grade: "9.5", price: 3000, currency: "USD", source: "pokemonpricetracker", date: "2026-10-01", sales: 3 },
    ]);
  });
});

describe("PSA", () => {
  it("normalizes a cert", () => {
    const c = parsePsaCert(psaCert)!;
    expect(c).toMatchObject({
      certNumber: "48658983",
      grade: "MINT 9",
      year: "2000",
      set: "POKEMON ROCKET",
      cardNumber: "8",
      specId: "1234567",
      population: { total: 1520, higher: 210 },
    });
    expect(c.description).toBe("2000 POKEMON ROCKET DARK GYARADOS-HOLO #8");
    expect(parsePsaCert({ PSACert: null })).toBeNull();
    expect(parsePsaCert(null)).toBeNull();
  });
  it("normalizes a spec population", () => {
    expect(parsePsaSpecPop(psaPop)).toEqual({
      total: 4100,
      higher: null,
      byGrade: { "1": 3, "8": 900, "9": 1520, "9 (Q)": 12, "10": 210, Authentic: 2 },
      description: "2000 POKEMON ROCKET 8 DARK GYARADOS-HOLO",
      specId: "1234567",
      source: "psa",
    });
  });
});

describe("Ximilar", () => {
  it("returns the best match first with a confidence derived from the distance", () => {
    const m = parseXimilar(xim);
    expect(m[0]).toMatchObject({ name: "Dark Gyarados", set: "Team Rocket", number: "8", game: "pokemon", tcgplayerId: "84606", confidence: 0.69 });
    expect(m[1]).toMatchObject({ number: "25", tcgplayerId: "84607", confidence: 0.62 });
    expect(m[2]).toMatchObject({ game: "magic", confidence: 0 });
  });
  it("puts matches of the hinted game first", () => {
    expect(parseXimilar(xim, "magic")[0].name).toBe("Gyarados");
  });
  it("maps game labels and encodes images", () => {
    expect(ximilarGame("One Piece")).toBe("one_piece");
    expect(ximilarGame("Yu-Gi-Oh!")).toBe("yu-gi-oh!");
    expect(toBase64(new Uint8Array([0xff, 0xd8, 0xff]))).toBe("/9j/");
  });
});


describe("printing availability", () => {
  it("does not replace unavailable PokeTrace first editions with another printing", () => {
    const first = card({ tcgplayerId: "502000", printing: "1st Edition Holofoil" });
    expect(parsePoketrace(poke, [first]).has(first.key)).toBe(false);
  });
  it("does not replace missing foil rows or premium prints with normal TCG API prices", () => {
    const foil = card({ printing: "Holofoil" });
    const onlyNormal = { data: { prices: [{ printing: "Normal", market_price: 1 }] } };
    expect(parseTcgApi(onlyNormal, foil)).toBeNull();
    for (const printing of ["1st Edition Holofoil", "Shadowless", "Reverse Holofoil"]) {
      expect(parseTcgApi(tcgapi, card({ printing }))).toBeNull();
    }
  });
});


describe("market is independent of language",()=>{
 it("keeps Japanese EUR imports apart from USD imports",()=>{
   const eur=parseCard({game:"POKEMON",id:"ja:SV2a-025",language:"JA",market:"DE"})!;
   const usd=parseCard({game:"POKEMON",id:"ja:SV2a-025",language:"JA",market:"US"})!;
   expect(eur.key).not.toBe(usd.key);
 });
 it("does not merge undifferentiated premium sold-price tiers",()=>{
   expect(parsePpt({data:{ebay:{salesByGrade:{bgs10:{count:20,medianPrice:500},cgc10:{count:20,medianPrice:100}}}}})).toEqual([]);
 });
});
