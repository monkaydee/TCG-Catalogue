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
      "Pokemon Terastal Festival ex Japanese Booster Box sealed lot of 2",
      "Pokemon Terastal Festival ex Japanese Booster Box sealed 2 box",
      "Pokemon Terastal Festival ex Japanese half Booster Box sealed",
      "Pokemon Terastal Festival ex Japanese Booster Box"]) expect(sealedTitleMatches(title,product),title).toBe(false);
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
  it("keeps Battle boxes, bundles, editions and pack contents apart", () => {
    const p:SealedRequest={game:"pokemon",productId:"-1",name:"Surging Sparks Booster Box",language:"DE",aliases:["Stürmische Funken"]};
    for (const title of ["Pokemon Stürmische Funken Build & Battle Box Deutsch OVP", "Pokemon Stürmische Funken Booster Bundle Deutsch OVP", "Pokemon Stürmische Funken Booster Box Deutsch OVP 18 Boosters"])
      expect(sealedTitleMatches(title,p)).toBe(false);
    expect(sealedTitleMatches("Pokemon Stürmische Funken Booster Box Deutsch OVP 36 Boosters",p)).toBe(true);
    const jp151:SealedRequest={...product,name:"Pokemon Card 151 Booster Box",aliases:["SV2a"]};
    expect(sealedTitleMatches("Pokemon Card 151 Japanese Booster Box sealed 20 Packs",jp151)).toBe(true);
    expect(sealedTitleMatches("Pokemon Card 151 Japanese Booster Box sealed 10 Packs",jp151)).toBe(false);
    expect(sealedTitleMatches("Pokemon Terastal Festival ex Japanese Booster Box シュリンクなし",product)).toBe(false);
  });

  it("can price Japanese imports in the German market without changing printed language",()=>{
    const title="Pokemon Terastal Festival ex Japanese Booster Box sealed";
    const p={...product,market:"DE" as const};
    const q=parseSealedListings({itemSummaries:[listing(title,"90","EUR","1"),listing(title,"100","EUR","2"),listing(title,"110","EUR","3")]},p)!;
    expect(q.currency).toBe("EUR");expect(q.amount).toBe(100);
    expect(parseSealedListings({itemSummaries:[listing(title,"90","USD","1"),listing(title,"100","USD","2"),listing(title,"110","USD","3")]},p)).toBeNull();
  });

});
