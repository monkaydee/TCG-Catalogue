import { afterEach, describe, expect, it, vi } from "vitest";
import { ebay, gradeInTitle, languageAspectFilter, parseEbay, parseEbayRaw, searchParams, titleMatches } from "../src/providers/ebay";
import type { CardRequest } from "../src/types";
const flareon:CardRequest={game:"pokemon",id:"base2-3",name:"Flareon",set:"Jungle",number:"3/64",key:"k"};
const item=(title:string,value:string,id=title)=>({itemId:id,title,price:{value,currency:"USD"}});
const copies=(title:string,values:number[])=>values.map((v,i)=>item(title,String(v),`${title}-${i}`));
afterEach(()=>vi.unstubAllGlobals());
describe("the reported missing slabs",()=>{
 it("falls back from an empty EUR market to an exact USD reference without currency mixing",async()=>{
  const requested={...flareon,market:"DE" as const,language:"EN",grader:"PSA",grade:"8",printing:"Holofoil",printingUnique:true};
  const sites:string[]=[];
  vi.stubGlobal("fetch",vi.fn(async(input:string,init?:RequestInit)=>{
    if(input.includes("oauth2/token")) return new Response(JSON.stringify({access_token:"fixture",expires_in:7200}));
    const site=new Headers(init?.headers).get("X-EBAY-C-MARKETPLACE-ID")!;sites.push(site);
    return new Response(JSON.stringify({itemSummaries:site==="EBAY_US" ? [item("Flareon 3/64 Jungle English PSA 8","130","us-8")] : []}));
  }));
  const quote=(await ebay().fetch([requested],"test:secret")).get(requested.key)![0];
  expect(sites).toEqual(["EBAY_DE","EBAY_US"]);
  expect(quote).toMatchObject({price:130,currency:"USD",grader:"PSA",grade:"8",listings:1,evidence:"limited",source:expect.stringContaining("international reference")});
 });
 const dark={...flareon,id:"base5-4",name:"Dark Charizard",set:"Team Rocket",number:"4/82",printing:"1st Edition Holofoil",grader:"PSA",grade:"5"};
 it("keeps first-edition PSA 5 separate from unlimited, other grades and raw",()=>{
  const titles=["Dark Charizard 4/82 Team Rocket Holo 1st Edition PSA 5", "Dark Charizard 4/82 Team Rocket Holo PSA 5", "Dark Charizard 4/82 Team Rocket Holo 1st Edition PSA 6", "Dark Charizard 4/82 Team Rocket Holo 1st Edition"];
  const items=titles.map((t,i)=>item(t,String(400+i),String(i)));
  expect(parseEbay({itemSummaries:items},dark)).toEqual([]);
  expect(parseEbay({itemSummaries:items},dark,new Set(items.map(i=>i.itemId)))).toEqual([expect.objectContaining({grader:"PSA",grade:"5",price:400,listings:1,evidence:"limited"})]);
  expect(titleMatches(titles[0]+" Japanese",dark,"EN")).toBe(false);
  expect(titleMatches("Dark Charizard 4/82 Team Rocket Non-holo 1st Edition English PSA 5",{...dark,printingUnique:true})).toBe(false);
  expect(titleMatches("Flareon 3/64 Jungle PSA 8",{...flareon,printing:"Holofoil",printingUnique:true},"EN")).toBe(true);
  expect(titleMatches("Flareon 3/64 Jungle 1st Edition PSA 8",{...flareon,printing:"Holofoil",printingUnique:true},"EN")).toBe(false);
  const params=searchParams(dark,"PSA 5",true);
  expect(params.get("q")).toContain("1st edition");expect(params.get("q")).not.toMatch(/graded|English/);
 });
 it("accepts German CGC 9 only with German language evidence",()=>{
  const psyduck={...flareon,name:"Misty's Psyduck",set:"Destined Rivals",number:"193/182",printing:"Holofoil",printingUnique:true,language:"DE",market:"DE" as const,grader:"CGC",grade:"9"};
  const listing={...item("Misty's Psyduck 193/182 Destined Rivals CGC 9","59.50","de-1"),price:{value:"59.50",currency:"EUR"}};
  expect(parseEbay({itemSummaries:[listing]},psyduck)).toEqual([]);
  expect(parseEbay({itemSummaries:[listing]},psyduck,new Set(["de-1"]))[0]).toMatchObject({price:59.5,currency:"EUR",grader:"CGC",grade:"9"});
  expect(parseEbay({itemSummaries:[{...listing,title:listing.title+" English"}]},psyduck,new Set(["de-1"]))).toEqual([]);
 });
 it("normalizes decimal-comma GSG grades and recognizes PI",()=>{
  expect(gradeInTitle("Zekrom GSG 8,5")).toEqual({grader:"GSG",grade:"8.5"});
  expect(gradeInTitle("Flareon PI 9")).toEqual({grader:"PI",grade:"9"});
 });
 it("builds a filter from eBay's language facet, not from marketplace",()=>{
  const response={refinement:{dominantCategoryId:"183454",aspectDistributions:[{localizedAspectName:"Sprache",aspectValueDistributions:[{localizedAspectValue:"Englisch"},{localizedAspectValue:"Deutsch"}]}]}};
  expect(languageAspectFilter(response,"EN")).toBe("categoryId:183454,Sprache:{Englisch}");
  expect(languageAspectFilter(response,"DE")).toBe("categoryId:183454,Sprache:{Deutsch}");
  expect(languageAspectFilter({...response,refinement:{...response.refinement,dominantCategoryId:"123"}},"DE")).toBeUndefined();
  expect(languageAspectFilter(response,"JA")).toBeUndefined();
 });
 it("performs the targeted filtered API search and deduplicates its results",async()=>{
  const urls:string[]=[];
  const listing=item("Dark Charizard 4/82 Team Rocket Holo 1st Edition PSA 5","400","a");
  vi.stubGlobal("fetch",vi.fn(async(input:string)=>{
   urls.push(input);
   if(input.includes("oauth2/token")) return new Response(JSON.stringify({access_token:"fixture",expires_in:7200}));
   if(new URL(input).searchParams.has("aspect_filter")) return new Response(JSON.stringify({itemSummaries:[listing]}));
   return new Response(JSON.stringify({itemSummaries:[listing],refinement:{dominantCategoryId:"183454",aspectDistributions:[{localizedAspectName:"Language",aspectValueDistributions:[{localizedAspectValue:"English"}]}]}}));
  }));
  const result=await ebay().fetch([dark],"test:secret");
  expect(result.get(dark.key)?.[0]).toMatchObject({price:400,listings:1,grade:"5"});
  const searches=urls.filter(u=>u.includes("item_summary/search"));
  expect(searches).toHaveLength(2);
  expect(new URL(searches[1]).searchParams.get("aspect_filter")).toBe("categoryId:183454,Language:{English}");
 });
});
describe("exact comparable eBay prices",()=>{
 it("reads one grade and excludes qualified or altered PSA slabs",()=>{
  expect(gradeInTitle("Jungle Flareon English PSA NM-MT 8")).toEqual({grader:"PSA",grade:"8"});
  expect(gradeInTitle("Flareon BGS 9.5")).toEqual({grader:"BGS",grade:"9.5"});
  for(const q of ["OC","MC","MK","ST","PD","OF","off-center","altered","authentic","trimmed"])
   expect(gradeInTitle(`Flareon PSA 9 ${q}`)).toBeNull();
  expect(gradeInTitle("Flareon PSA 9 PSA 10")).toBeNull();
 });
 it("requires exact set, collector number and language evidence",()=>{
  expect(titleMatches("Flareon Holo 3/64 Jungle English PSA 9",flareon)).toBe(true);
  for(const title of ["Flareon Holo #19 Jungle English PSA 9","Flareon Holo 3/64 Fossil English PSA 9","Flareon Holo 3/64 Jungle PSA 9","Flareon Jungle English PSA 3","Flareon 3/165 Jungle English PSA 9"])
   expect(titleMatches(title,flareon)).toBe(false);
  const charizard={...flareon,name:"Charizard",set:"Base Set",number:"4/102"};
  expect(titleMatches("Charizard Celebrations Base Set 4/102 English PSA 9",charizard)).toBe(false);
  expect(titleMatches("Charizard Base Set 2 4/102 English PSA 9",charizard)).toBe(false);
  expect(titleMatches("Charizard Base Set 4/102 English PSA 9",charizard)).toBe(true);
 });
 it("uses verified localized set aliases, not localized names alone as language proof",()=>{
  const german={...flareon,language:"DE",localName:"Flamara"};
  expect(titleMatches("Flamara 3/64 Dschungel Deutsch PSA 9",german)).toBe(true);
  expect(titleMatches("Flamara 3/64 Dschungel PSA 9",german)).toBe(false);
  expect(titleMatches("Flamara 3/64 Dschungel German English PSA 9",german)).toBe(false);
 });
 it("separates first editions, reverse, foil, alt-art and release years",()=>{
  expect(titleMatches("Flareon 3/64 Jungle English 1st Edition PSA 9",flareon)).toBe(false);
  expect(titleMatches("Flareon 3/64 Jungle English 1st Edition PSA 9",{...flareon,printing:"firstEdition"})).toBe(true);
  expect(titleMatches("Flareon 3/64 Jungle English Reverse Holo PSA 9",flareon)).toBe(false);
  expect(titleMatches("Flareon 3/64 Jungle English Reverse Holo PSA 9",{...flareon,printing:"reverse"})).toBe(true);
  expect(titleMatches("Flareon 3/64 Jungle English Holo",{...flareon,printing:"Normal"})).toBe(false);
  expect(titleMatches("Flareon 3/64 Jungle English 2021 PSA 9",{...flareon,releaseYear:"1999"})).toBe(false);
 });
 it("uses explicit catalogue uniqueness without accepting wrong printings or languages",()=>{
  const card={...flareon,printing:"Holofoil",printingUnique:true};
  expect(titleMatches("Flareon 3/64 Jungle English PSA 9",card)).toBe(true);
  expect(titleMatches("Flareon 3/64 Jungle English PSA 9",{...card,printingUnique:false})).toBe(false);
  for (const title of ["Flareon 3/64 Jungle English Non-holo PSA 9","Flareon 3/64 Jungle English Reverse Holo PSA 9","Flareon 3/64 Jungle Japanese PSA 9","Flareon 3/64 Fossil English PSA 9"])
    expect(titleMatches(title,card),title).toBe(false);
 });
 it("labels sparse graded asking references, removes extreme asks and reports spread",()=>{
  const title="Flareon 3/64 Jungle English PSA 9";
  expect(parseEbay({itemSummaries:copies(title,[100,120])},flareon)[0]).toMatchObject({price:110,listings:2,evidence:"limited"});
  expect(parseEbay({itemSummaries:copies(title,[100])},flareon)[0]).toMatchObject({price:100,listings:1,evidence:"limited",source:expect.stringContaining("not sold")});
  const out=parseEbay({itemSummaries:copies(title,[100,110,120,130,140,9999])},flareon);
  expect(out[0]).toMatchObject({price:120,listings:5,excluded:1,evidence:"consistent"});
 });
 it("never blends damage with NM and excludes unknown or conflicting conditions",()=>{
  const title="Flareon 3/64 Jungle Holo English";
  const out=parseEbayRaw({itemSummaries:[...copies(title+" NM",[50,60,70]),...copies(title+" damaged",[5,6,7]),...copies(title,[500,600,700]),...copies(title+" NM damaged",[1,2,3])]},flareon)!;
  expect(out.market).toBeNull();expect(out.conditions.NM).toBe(60);expect(out.conditions.DMG).toBe(6);expect(out.conditions.LP).toBeNull();
  expect(parseEbayRaw({itemSummaries:copies(title+" NM",[50,60])},flareon)).toBeNull();
 });
 it("keeps premium grades separate",()=>{
  expect(gradeInTitle("BGS 10 Black Label")).toEqual({grader:"BGS",grade:"10",qualifier:"Black Label"});
  expect(gradeInTitle("CGC Pristine 10")).toEqual({grader:"CGC",grade:"10",qualifier:"Pristine"});
  const t="Flareon Jungle 3/64 English";
  const out=parseEbay({itemSummaries:[...copies(t+" CGC 10",[80,90,100,110,120]),...copies(t+" CGC Pristine 10",[200,220,240,260,280])]},flareon);
  expect(out.find(g=>!g.qualifier)).toMatchObject({price:100});
  expect(out).toContainEqual(expect.objectContaining({price:240,qualifier:"Pristine"}));
 });
});
