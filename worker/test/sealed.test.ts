import { describe, it, expect } from "vitest";
import { parseSealedListings, sealedTitleMatches, type SealedRequest } from "../src/sealed";

const product: SealedRequest = {game:"pokemon",productId:"-100",name:"Terastal Festival ex Booster Box",language:"JA"};
const listing = (title: string, value = "80", currency = "USD", id = title) => ({title,itemId:id,price:{value,currency}});
describe("exact-language sealed prices", () => {
  it("rejects wrong language, unit, opened products and multipacks", () => {
    expect(sealedTitleMatches("Pokemon Terastal Festival ex Japanese Booster Box sealed",product)).toBe(true);
    for (const title of ["Pokemon Terastal Festival ex English Booster Box sealed", "Pokemon Terastal Festival ex Japanese Booster Pack sealed",
      "Pokemon Terastal Festival ex Japanese Booster Box opened", "Pokemon Terastal Festival ex Japanese Booster Box sealed 2 boxes",
      "Pokemon Terastal Festival ex Japanese Booster Box case sealed", "Pokemon Terastal Festival ex Japanese Booster Box empty sealed",
      "Pokemon Terastal Festival ex Japanese Booster Box"]) expect(sealedTitleMatches(title,product)).toBe(false);
  });
  it("requires three unique matching listings and keeps currency", () => {
    const title="Pokemon Terastal Festival ex Japanese Booster Box sealed";
    expect(parseSealedListings({itemSummaries:[listing(title,"80","USD","1"),listing(title,"90","USD","2")]},product)).toBeNull();
    const quote=parseSealedListings({itemSummaries:[listing(title,"80","USD","1"),listing(title,"90","USD","2"),listing(title,"100","USD","3"),listing(title,"1","EUR","4"),listing(title,"9000","USD","3")]},product)!;
    expect(quote.amount).toBe(90); expect(quote.listings).toBe(3); expect(quote.currency).toBe("USD"); expect(quote.source).toContain("asking");
  });
  it("uses explicit German listings in EUR", () => {
    const german:SealedRequest={...product,name:"Surging Sparks Booster Box",language:"DE"};
    const title="Pokemon Surging Sparks Booster Box Deutsch OVP";
    expect(sealedTitleMatches(title,german)).toBe(true);
    expect(sealedTitleMatches("Pokémon Stürmische Funken Boosterdisplay deutsches OVP",{...german,aliases:["Stürmische Funken"]})).toBe(true);
    expect(sealedTitleMatches(title.replace("Deutsch","English"),german)).toBe(false);
    const q=parseSealedListings({itemSummaries:[listing(title,"100","EUR","1"),listing(title,"110","EUR","2"),listing(title,"120","EUR","3")]},german)!;
    expect(q.currency).toBe("EUR"); expect(q.amount).toBe(110);
  });
  it("matches One Piece set code without accepting a different set", () => {
    const op:SealedRequest={game:"one_piece",productId:"-200",name:"Romance Dawn Booster Box (Non-English)",language:"JA"};
    expect(sealedTitleMatches("One Piece OP-01 Japanese Booster Box sealed",op)).toBe(true);
    expect(sealedTitleMatches("One Piece OP-02 Japanese Booster Box sealed",op)).toBe(false);
  });
});
