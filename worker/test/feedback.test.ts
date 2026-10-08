import { describe, it, expect, vi } from "vitest";
import { ensureLearningPolicy, feedback, normalizeRead, validJpeg } from "../src/feedback";
import type { Env } from "../src/types";
const header = () => new Uint8Array([255,216,255,192,0,17,8,1,44,0,200,3,1,17,0,2,17,0,3,17,0,255,218,0,12,0]);
describe("recognition feedback privacy", () => {
 it("stores hash-only recognition evidence and treats absent confirmation as unconfirmed", async () => {
   const install="11111111-1111-1111-1111-111111111111",token="22222222-2222-2222-2222-222222222222";
   const digest=Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(token))),b=>b.toString(16).padStart(2,"0")).join("");
   let inserted:unknown[]=[];
   const DB={prepare:(sql:string)=>{
    let args:unknown[]=[];
    const statement={bind:(...a:unknown[])=>{args=a;return statement},run:async()=>{if(sql.startsWith("INSERT INTO recognition_reports"))inserted=args},first:async()=>{
     if(sql.includes("learning_policy"))return {revision:2};
     if(sql.includes("SELECT token_hash"))return {token_hash:digest};
     if(sql.includes("SELECT COUNT"))return {n:0};
     if(sql.includes("learning_totals"))return {n:0};
     return null;
    }};return statement;
   }};
   const body={install,token,id:"33333333-3333-3333-3333-333333333333",kind:"recognition",read:"personal words from a background",suggested:"private suggestion",card:{game:"POKEMON",id:"base1-4",name:"Charizard",set:"Base Set",number:"4/102"}};
   const response=await feedback(new Request("https://fixture/v1/feedback",{method:"POST",body:JSON.stringify(body)}),{DB} as unknown as Env,"/v1/feedback");
   expect(response.status).toBe(200);
   expect(inserted[3]).toMatch(/^pokemon\|EN\|[a-f0-9]{64}$/);
   expect(JSON.parse(inserted[7] as string)).toEqual({card:{game:"pokemon",id:"base1-4",language:"EN",number:"4/102"},evidence:null,selectedGrade:null});
   expect(inserted[8]).toBeNull();expect(inserted[10]).toBe(0);
   expect(JSON.stringify(inserted)).not.toContain("personal words");
 });
 it("keeps legacy photo references when the private bucket cannot be cleaned", async () => {
  const batch=vi.fn();
  const DB={batch,prepare:(sql:string)=>({first:async()=>null,all:async()=>({results:[{image_key:"legacy.jpg"}]})})};
  await expect(ensureLearningPolicy({DB} as unknown as Env)).rejects.toThrow("private bucket binding");
  expect(batch).not.toHaveBeenCalled();
 });
 it("rejects forged approved photos before touching database or private storage", async () => {
  const env = {} as Env; // Any attempt at storage would fail this regression.
  for (const photo of [{image:"",photoApproved:true},{image:"pretend-card"},{photoApproved:false}]) {
   const response=await feedback(new Request("https://fixture/v1/feedback",{method:"POST",body:JSON.stringify(photo)}),env,"/v1/feedback");
   expect(response.status).toBe(422);
   expect(await response.json()).toEqual({error:"photo_uploads_disabled"});
  }
 });
 it("retains native script and normalizes OCR spacing", () => expect(normalizeRead("ＳＶＰ １２３ • ピカチュウ")).toBe("svp123ピカチュウ"));
 it("accepts only bounded card-shaped JPEG crops", () => { expect(validJpeg(header())).toBe(true); const wide=header();wide[9]=2;wide[10]=88;expect(validJpeg(wide)).toBe(false); });
 it("rejects EXIF even after image dimensions", () => { const b=header(); b[22]=225; expect(validJpeg(b)).toBe(false); });
 it("rejects full photo formats and oversized images", () => {expect(validJpeg(new Uint8Array(400000))).toBe(false);expect(validJpeg(new Uint8Array([137,80,78,71]))).toBe(false);});
});
