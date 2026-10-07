import { afterEach, describe, it, expect, vi } from "vitest";
import { parseSealedListings, sealedPrice, sealedSearchParams, sealedTitleMatches, type SealedRequest } from "../src/sealed";
import { languageAspectFilter } from "../src/providers/ebay";
import type { Env } from "../src/types";

const product: SealedRequest = {game:"pokemon",productId:"-100",name:"Terastal Festival ex Booster Box",language:"JA"};
const listing = (title: string, value = "80", currency = "USD", id = title) => ({title,itemId:id,price:{value,currency}});
afterEach(()=>vi.unstubAllGlobals());

it("falls back across markets while retaining Japanese identity and charging each search", async()=>{
  const searches:{url:URL;market:string|null}[]=[];
  let reserved=0;
  const db={prepare:(sql:string)=>({bind(){return this;},async all(){return {results:[]};},async run(){if(sql.includes("INSERT INTO usage")) reserved++;return {meta:{changes:1}};}}),async batch(){return [];}} as unknown as D1Database;
  vi.stubGlobal("fetch",vi.fn(async(input:string,init?:RequestInit)=>{
    if(input.includes("oauth2/token")) return new Response(JSON.stringify({access_token:"fixture",expires_in:7200}));
    const market=new Headers(init?.headers).get("X-EBAY-C-MARKETPLACE-ID");
    searches.push({url:new URL(input),market});
    const title="One Piece OP-08 Japanese Booster Box sealed";
    return new Response(JSON.stringify({itemSummaries:market==="EBAY_DE" ? [] : [listing(title,"50","USD","1"),listing(title,"60","USD","2"),listing(title,"70","USD","3")]}));
  }));
  const request={game:"one_piece",productId:"-766868",name:"Two Legends Booster Box (Non-English)",language:"JA",aliases:["OP08"],market:"DE"};
  const response=await sealedPrice(new Request("https://fixture/v1/sealed/price",{method:"POST",body:JSON.stringify(request)}),{DB:db,EBAY_CLIENT_ID:"fixture",EBAY_CLIENT_SECRET:"fixture"} as Env);
  const result=await response.json() as {price:{amount:number;currency:string;source:string};sealedMatchingRevision:number};
  expect(result.price.amount).toBe(60);
  expect(result.price.currency).toBe("USD");
  expect(result.price.source).toContain("EBAY_US international reference");
  expect(result.sealedMatchingRevision).toBe(6);
  expect(searches.map(s=>s.market)).toEqual(["EBAY_DE","EBAY_US"]);
  expect(searches[1].url.searchParams.get("q")).toContain("OP-08");
  expect(reserved).toBe(2);
});

describe("exact-language sealed prices", () => {
  it("uses the sealed item's Language facet without inferring it from a German seller", () => {
    const title="One Piece Two Legends Booster Box OVP";
    const p:SealedRequest={game:"one_piece",productId:"-766868",name:"Two Legends Booster Box (Non-English)",language:"JA",market:"DE",aliases:["OP08"]};
    const rows={itemSummaries:[listing(title,"50","EUR","1"),listing(title,"60","EUR","2"),listing(title,"70","EUR","3")]};
    expect(parseSealedListings(rows,p)).toBeNull();
    expect(parseSealedListings(rows,p,new Set(["1","2","3"]))?.amount).toBe(60);
    expect(sealedTitleMatches(title+" English",p,"JA")).toBe(false);
    const facet={refinement:{dominantCategoryId:"183456",aspectDistributions:[{localizedAspectName:"Sprache",aspectValueDistributions:[{localizedAspectValue:"Japanisch"}]}]}};
    expect(languageAspectFilter(facet,"JA","183456")).toBe("categoryId:183456,Sprache:{Japanisch}");
    expect(languageAspectFilter(facet,"JA")).toBeUndefined();
  });
  it("queries native codes and format without requiring English sealed wording", () => {
    const p:SealedRequest={game:"one_piece",productId:"-766868",name:"Two Legends Booster Box (Non-English)",language:"JA",market:"DE",aliases:["OP08"]};
    expect(sealedSearchParams(p,true).get("q")).toBe("One Piece (OP08,OP-08) (box,display)");
    expect(sealedSearchParams(p).get("q")).not.toContain("sealed");
    expect(sealedSearchParams(p).get("fieldgroups")).toContain("ASPECT_REFINEMENTS");
    expect(sealedTitleMatches("One Piece Two Legends OP-07 Japanese Booster Box OVP",p)).toBe(false);
    expect(sealedTitleMatches("One Piece OP-08 Japanese Booster Box OVP",p)).toBe(true);
    expect(sealedTitleMatches("One Piece OP-08 Japanese Pre-Release Pack sealed",{...p,name:"Two Legends Booster"})).toBe(false);
    expect(sealedTitleMatches("One Piece OP-08 Japanese Booster Pack sealed",{...p,name:"Two Legends: Pre-Release Pack"})).toBe(false);
  });
  it("rejects wrong language, unit, opened products and multipacks", () => {
    expect(sealedTitleMatches("Pokemon Terastal Festival ex Japanese Booster Box sealed",product)).toBe(true);
    for (const title of ["Pokemon Terastal Festival ex English Booster Box sealed", "Pokemon Terastal Festival ex Japanese Booster Pack sealed",
      "Pokemon Terastal Festival ex Japanese Booster Box opened", "Pokemon Terastal Festival ex Japanese Booster Box sealed 2 boxes",
      "Pokemon Terastal Festival ex Japanese Booster Box case sealed", "Pokemon Terastal Festival ex Japanese Booster Box empty sealed",
      "Pokemon Terastal Festival ex Japanese Booster Box sealed lot of 2",
      "Pokemon Terastal Festival ex Japanese Booster Box sealed 2 box",
      "Pokemon Terastal Festival ex Japanese half Booster Box sealed",
      "Pokemon Terastal Festival ex Japanese Booster Box not sealed",
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
    expect(sealedTitleMatches("Pokemon Stürmische Funken Booster Box Deutsch OVP 36x Boosters",p)).toBe(true);
    expect(sealedTitleMatches("Pokemon Stürmische Funken Booster Box Deutsch OVP 36xBoosters",p)).toBe(true);
    expect(sealedTitleMatches("Pokemon Stürmische Funken 2xBoosterbox Deutsch OVP",p)).toBe(false);
    expect(sealedTitleMatches("Pokemon Stürmische Funken 36er Display Deutsch OVP",p)).toBe(true);
    expect(sealedTitleMatches("Pokemon Stürmische Funken 18er Display Deutsch OVP",p)).toBe(false);
    expect(sealedTitleMatches("Pokemon Stürmische Funken Display (18er) Deutsch OVP",p)).toBe(false);
    expect(sealedTitleMatches("Pokemon Stürmische Funken Booster Box Deutsch OVP Auswahl",p)).toBe(false);
    expect(sealedTitleMatches("Pokemon Stürmische Funken Booster Box Deutsch OVP 18x Boosters",p)).toBe(false);
    expect(sealedTitleMatches("Pokemon Stürmische Funken Booster Box x 2 Deutsch OVP",p)).toBe(false);
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
