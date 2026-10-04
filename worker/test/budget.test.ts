import { describe, expect, it } from "vitest";
import { dailyAllowance, daysInMonth, remainingToday, utcDay, utcMonth } from "../src/budget";
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
    expect(dailyAllowance(justtcg, 0, at("2026-10-01T08:00:00Z"))).toBe(29);
  });

  it("never lets day 10 use up the month", () => {
    // Even if 600 were used in the first 9 days, the rest is spread over the 22 days left.
    expect(dailyAllowance(justtcg, 600, at("2026-10-10T08:00:00Z"))).toBe(Math.floor(300 / 22));
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
    expect(remainingToday(justtcg, 10, 0, at("2026-10-01T08:00:00Z"))).toBe(19);
    expect(remainingToday(justtcg, 40, 0, at("2026-10-01T08:00:00Z"))).toBe(0);
  });

  it("small monthly budgets still allow at least one call on most days", () => {
    // RapidAPI: 45 a month → 1 per day on a 31-day month
    expect(dailyAllowance({ daily: 5, monthly: 45 }, 0, at("2026-10-01T00:00:00Z"))).toBe(1);
    // Ximilar: 90 a month → 2 per day at the start of a 31-day month, 3 in a 30-day one
    expect(dailyAllowance({ daily: 10, monthly: 90 }, 0, at("2026-10-01T00:00:00Z"))).toBe(2);
    expect(dailyAllowance({ daily: 10, monthly: 90 }, 0, at("2026-11-01T00:00:00Z"))).toBe(3);
  });

  it("a whole month of steady use stays within the monthly budget", () => {
    let used = 0;
    for (let d = 1; d <= 31; d++) used += dailyAllowance(justtcg, used, at(`2026-10-${String(d).padStart(2, "0")}T12:00:00Z`));
    expect(used).toBeLessThanOrEqual(900);
    expect(used).toBeGreaterThan(880); // and almost all of it gets used
  });
});

describe("budget settings", () => {
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
