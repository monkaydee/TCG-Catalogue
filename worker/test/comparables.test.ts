import { describe, expect, it } from "vitest";
import { comparableSummary } from "../src/comparables";
import { price } from "../src/util";

describe("asking-price deviations", () => {
  it("rejects finite inputs that overflow during price rounding", () => {
    expect(price(1e308)).toBeNull();
    expect(comparableSummary([1e308],1)).toBeNull();
    expect(comparableSummary([1e308,1e308],1)).toBeNull();
  });
  it("excludes both abnormally cheap and expensive minority bands", () => {
    expect(comparableSummary([1,100,110,120,130,9000],1)).toMatchObject({amount:115,listings:4,excluded:2,evidence:"limited"});
  });
  it("abstains on equally supported disconnected bands", () => {
    expect(comparableSummary([10,12,100,120],1)).toBeNull();
    expect(comparableSummary([1,100,10000],1)).toBeNull();
  });
  it("does not invent confidence from a single or two sparse asks", () => {
    expect(comparableSummary([100],1)?.evidence).toBe("limited");
    expect(comparableSummary([100,200],1)?.evidence).toBe("limited");
    expect(comparableSummary([100,110],3)).toBeNull();
  });
});
