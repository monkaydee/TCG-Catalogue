import { describe, expect, it } from "vitest";
import { gradeInTitle, parseEbay, titleMatches } from "../src/providers/ebay";
import type { CardRequest } from "../src/types";

const flareon: CardRequest = { game: "pokemon", id: "base2-3", name: "Flareon", set: "Jungle", number: "3/64", key: "k" };
const item = (title: string, value: string) => ({ title, price: { value, currency: "USD" } });

describe("ebay", () => {
  it("reads exactly one grade from a title", () => {
    expect(gradeInTitle("1999 Pokemon Jungle Flareon Holo #3 PSA 9 MINT")).toEqual({ grader: "PSA", grade: "9" });
    expect(gradeInTitle("Flareon 3/64 BGS 9.5 Gem Mint")).toEqual({ grader: "BGS", grade: "9.5" });
    expect(gradeInTitle("Flareon PSA NM-MT 8")).toEqual({ grader: "PSA", grade: "8" });
    expect(gradeInTitle("Flareon PSA 9 and PSA 10 lot")).toBeNull();
    expect(gradeInTitle("Flareon holo raw")).toBeNull();
  });

  it("needs name and number in the title", () => {
    expect(titleMatches("Flareon Holo 3/64 Jungle PSA 9", flareon)).toBe(true);
    expect(titleMatches("Flareon Holo #19 Jungle PSA 9", flareon)).toBe(false);
    expect(titleMatches("Vaporeon Holo 12/64 PSA 9", flareon)).toBe(false);
  });

  it("takes the median per grade", () => {
    const json = { itemSummaries: [item("Flareon 3/64 PSA 9", "100"), item("Flareon #3 PSA 9 Jungle", "140"), item("Flareon 3/64 PSA 9", "120"), item("Flareon 3/64 PSA 8", "60"), item("Flareon lot 3/64 PSA 9", "5")] };
    const out = parseEbay(json, flareon);
    expect(out.find((g) => g.grade === "9")).toMatchObject({ grader: "PSA", price: 120, source: "eBay listings (asking)" });
    expect(out.find((g) => g.grade === "8")).toBeUndefined(); // one listing only
  });

  it("keeps 1st Edition apart", () => {
    expect(titleMatches("Flareon 3/64 1st Edition Holo PSA 9", flareon)).toBe(false);
    expect(titleMatches("Flareon 3/64 1st Edition Holo PSA 9", { ...flareon, printing: "firstEdition" })).toBe(true);
    expect(titleMatches("Flareon 3/64 Holo PSA 9", { ...flareon, printing: "firstEdition" })).toBe(false);
  });
});
