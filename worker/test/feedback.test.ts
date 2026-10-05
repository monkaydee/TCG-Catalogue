import { describe, it, expect } from "vitest";
import { normalizeRead, validJpeg } from "../src/feedback";
const header = () => new Uint8Array([255,216,255,192,0,17,8,1,44,0,200,3,1,17,0,2,17,0,3,17,0,255,218,0,12,0]);
describe("recognition feedback privacy", () => {
 it("retains native script and normalizes OCR spacing", () => expect(normalizeRead("ＳＶＰ １２３ • ピカチュウ")).toBe("svp123ピカチュウ"));
 it("accepts only bounded card-shaped JPEG crops", () => { expect(validJpeg(header())).toBe(true); const wide=header();wide[9]=2;wide[10]=88;expect(validJpeg(wide)).toBe(false); });
 it("rejects EXIF even after image dimensions", () => { const b=header(); b[22]=225; expect(validJpeg(b)).toBe(false); });
 it("rejects full photo formats and oversized images", () => {expect(validJpeg(new Uint8Array(400000))).toBe(false);expect(validJpeg(new Uint8Array([137,80,78,71]))).toBe(false);});
});
