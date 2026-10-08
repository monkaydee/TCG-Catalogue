import { describe, expect, it } from "vitest";
import { boundedBody, boundedJson, BodyTooLarge } from "../src/util";

describe("upload/request size limits", () => {
 it("cancels a chunked payload without Content-Length as soon as it crosses the limit",async()=>{
  let canceled=false;
  const stream=new ReadableStream<Uint8Array>({start(c){c.enqueue(new Uint8Array(6));c.enqueue(new Uint8Array(6));},cancel(){canceled=true;}});
  const request=new Request("https://fixture",{method:"POST",body:stream,duplex:"half"} as RequestInit);
  await expect(boundedBody(request,10)).rejects.toBeInstanceOf(BodyTooLarge);
  expect(canceled).toBe(true);
 });
 it("accepts a bounded JSON body and rejects oversized declared length before parsing",async()=>{
  expect(await boundedJson(new Request("https://fixture",{method:"POST",body:'{"ok":true}'}),11)).toEqual({ok:true});
  await expect(boundedBody(new Request("https://fixture",{method:"POST",headers:{"Content-Length":"1000"},body:"tiny"}),10)).rejects.toBeInstanceOf(BodyTooLarge);
 });
});
