import { describe, expect, it } from "vitest";
import { gradeInTitle, parseEbay, parseEbayRaw, titleMatches } from "../src/providers/ebay";
import type { CardRequest } from "../src/types";
const flareon:CardRequest={game:"pokemon",id:"base2-3",name:"Flareon",set:"Jungle",number:"3/64",key:"k"};
const item=(title:string,value:string,id=title)=>({itemId:id,title,price:{value,currency:"USD"}});
const copies=(title:string,values:number[])=>values.map((v,i)=>item(title,String(v),`${title}-${i}`));
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
 it("requires five distinct graded comparables, removes extreme asks and reports spread",()=>{
  const title="Flareon 3/64 Jungle English PSA 9";
  expect(parseEbay({itemSummaries:copies(title,[100,120])},flareon)).toEqual([]);
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
