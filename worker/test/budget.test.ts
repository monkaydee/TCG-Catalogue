import { describe, expect, it } from "vitest";
import { Budgets, dailyAllowance, daysInMonth, remainingToday, utcDay, utcMonth } from "../src/budget";
import { budgetFor, DEFAULT_BUDGETS } from "../src/config";
import type { Env } from "../src/types";

const at = (iso: string) => new Date(iso);

describe("dates", () => {
  it("uses UTC days and months", () => {
    expect(utcDay(at("2026-10-03T23:59:59Z"))).toBe("2026-10-03");
    expect(utcMonth(at("2026-10-03T12:00:00Z"))).toBe("2026-10");
    expect(daysInMonth(at("2026-02-10T00:00:00Z"))).toBe(28);
    expect(daysInMonth(at("2028-02-10T00:00:00Z"))).toBe(29);
    expect(daysInMonth(at("2026-10-31T00:00:00Z"))).toBe(31);
  });
});

describe("monthly budget spreading", () => {
  const justtcg = { daily: 90, monthly: 900 };

  it("spreads the month evenly: 900 over 31 days ≈ 29 per day on day 1", () => {
    expect(dailyAllowance(justtcg, 0, at("2026-10-01T08:00:00Z"))).toBe(30); // 900 × 1/31, rounded up
  });

  it("never lets day 10 use up the month", () => {
    // 600 used in the first 9 days is ahead of the pace (291 by day 10): nothing more today.
    expect(dailyAllowance(justtcg, 600, at("2026-10-10T08:00:00Z"))).toBe(0);
    // Back on pace later in the month.
    expect(dailyAllowance(justtcg, 600, at("2026-10-22T08:00:00Z"))).toBe(39);
  });

  it("rolls unused calls over to later days", () => {
    // Nothing used for 30 days: the last day may use up to the daily cap.
    expect(dailyAllowance(justtcg, 0, at("2026-10-31T08:00:00Z"))).toBe(90);
    // 880 used: only 20 left in the month.
    expect(dailyAllowance(justtcg, 880, at("2026-10-31T08:00:00Z"))).toBe(20);
  });

  it("is zero when the month is used up", () => {
    expect(dailyAllowance(justtcg, 900, at("2026-10-15T08:00:00Z"))).toBe(0);
    expect(dailyAllowance(justtcg, 950, at("2026-10-15T08:00:00Z"))).toBe(0);
  });

  it("uses only the daily limit when there is no monthly limit", () => {
    expect(dailyAllowance({ daily: 90, monthly: 0 }, 5000, at("2026-10-15T08:00:00Z"))).toBe(90);
  });

  it("subtracts calls already made today", () => {
    expect(remainingToday(justtcg, 10, 0, at("2026-10-01T08:00:00Z"))).toBe(20);
    expect(remainingToday(justtcg, 40, 0, at("2026-10-01T08:00:00Z"))).toBe(0);
  });

  it("small monthly budgets still allow calls, carried over between days", () => {
    // RapidAPI: 22 a month → 1 on the 1st, 3 by the 4th if nothing was used
    expect(dailyAllowance({ daily: 3, monthly: 22 }, 0, at("2026-10-01T00:00:00Z"))).toBe(1);
    expect(dailyAllowance({ daily: 3, monthly: 22 }, 0, at("2026-10-04T00:00:00Z"))).toBe(3);
    expect(dailyAllowance({ daily: 3, monthly: 22 }, 3, at("2026-10-04T00:00:00Z"))).toBe(0);
    // Ximilar: 90 a month → 3 on the 1st
    expect(dailyAllowance({ daily: 10, monthly: 90 }, 0, at("2026-10-01T00:00:00Z"))).toBe(3);
  });

  it("a whole month of steady use stays within the monthly budget", () => {
    let used = 0;
    for (let d = 1; d <= 31; d++) used += dailyAllowance(justtcg, used, at(`2026-10-${String(d).padStart(2, "0")}T12:00:00Z`));
    expect(used).toBeLessThanOrEqual(900);
    expect(used).toBeGreaterThan(880); // and almost all of it gets used
  });
});

describe("budget settings", () => {
  it("loads and retains eBay usage and quota blocks in the provider status",async()=>{
    const db={prepare:()=>({bind(){return this;},async all(){return {results:[{provider:"ebay",day:"2026-10-07",used:2380,blocked:0}]};},async run(){return {meta:{changes:1}};}})} as unknown as D1Database;
    const budgets=await new Budgets(db,{} as Env,at("2026-10-07T04:00:00Z")).load();
    expect(budgets.remaining("ebay")).toBe(20);
    expect(await budgets.reserve("ebay",4)).toBe(4);
    expect(budgets.remaining("ebay")).toBe(16);
    await budgets.block("ebay");
    expect(budgets.remaining("ebay")).toBe(0);
    expect(budgets.report(p=>p==="ebay").ebay).toMatchObject({configured:true,usedToday:2384,blockedToday:true});
  });
  it("defaults are the free tiers minus 10%", () => {
    expect(DEFAULT_BUDGETS.justtcg).toEqual({ daily: 90, monthly: 900 });
    expect(DEFAULT_BUDGETS.tcgapi.daily).toBe(90);
    expect(DEFAULT_BUDGETS.rapidapi.monthly).toBe(22);
  });
  it("reads overrides from vars and ignores junk", () => {
    const env = { BUDGET_JUSTTCG_DAILY: "50", BUDGET_JUSTTCG_MONTHLY: "oops" } as unknown as Env;
    expect(budgetFor(env, "justtcg")).toEqual({ daily: 50, monthly: 900 });
  });
});
