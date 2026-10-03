// Call budgets per provider, counted in D1 per UTC day.
//
// The monthly budget is spread over the month: on any day we allow at most
//   (monthly budget - calls used earlier this month) / (days left in the month, today included)
// so a busy first week can never use up the whole month. Unused calls roll over to later days
// automatically, because the remaining budget is divided by fewer days.

import { budgetFor, type Budget } from "./config";
import type { Env, ProviderName } from "./types";

export const PROVIDERS: ProviderName[] = ["justtcg", "tcgapi", "poketrace", "rapidapi", "ppt", "psa", "ximilar"];

// ---------- Pure date and budget math (unit tested) ----------

export function utcDay(now: Date): string {
  return now.toISOString().slice(0, 10); // "2026-10-03"
}

export function utcMonth(now: Date): string {
  return now.toISOString().slice(0, 7); // "2026-10"
}

export function daysInMonth(now: Date): number {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0)).getUTCDate();
}

/**
 * How many calls are allowed in total today (including the ones already made today).
 * @param usedEarlierThisMonth calls made this month before today
 */
export function dailyAllowance(budget: Budget, usedEarlierThisMonth: number, now: Date): number {
  if (budget.monthly <= 0) return budget.daily;
  const daysLeft = daysInMonth(now) - now.getUTCDate() + 1; // today counts as a day left
  const monthLeft = Math.max(0, budget.monthly - usedEarlierThisMonth);
  return Math.max(0, Math.min(budget.daily, Math.floor(monthLeft / daysLeft)));
}

/** Calls still allowed right now for one provider. */
export function remainingToday(budget: Budget, usedToday: number, usedEarlierThisMonth: number, now: Date): number {
  return Math.max(0, dailyAllowance(budget, usedEarlierThisMonth, now) - usedToday);
}

// ---------- D1-backed counters ----------

interface Usage {
  today: number;
  earlier: number; // earlier this month
  blocked: boolean; // provider answered "quota exceeded" today
}

export class Budgets {
  private usage = new Map<ProviderName, Usage>();
  private readonly day: string;

  constructor(private db: D1Database, private env: Env, private now: Date = new Date()) {
    this.day = utcDay(now);
  }

  /** Reads this month's counters for every provider with a single query. */
  async load(): Promise<this> {
    const monthStart = `${utcMonth(this.now)}-01`;
    const { results } = await this.db
      .prepare("SELECT provider, day, used, blocked FROM usage WHERE day >= ?1")
      .bind(monthStart)
      .all<{ provider: ProviderName; day: string; used: number; blocked: number }>();
    for (const p of PROVIDERS) this.usage.set(p, { today: 0, earlier: 0, blocked: false });
    for (const r of results ?? []) {
      const u = this.usage.get(r.provider);
      if (!u) continue;
      if (r.day === this.day) {
        u.today = r.used;
        u.blocked = r.blocked === 1;
      } else u.earlier += r.used;
    }
    return this;
  }

  private u(p: ProviderName): Usage {
    return this.usage.get(p) ?? { today: 0, earlier: 0, blocked: false };
  }

  remaining(p: ProviderName): number {
    const u = this.u(p);
    if (u.blocked) return 0;
    return remainingToday(budgetFor(this.env, p), u.today, u.earlier, this.now);
  }

  /**
   * Reserves up to `wanted` calls and returns how many were granted (possibly 0).
   * The write is atomic, so two app requests at the same moment cannot both overspend.
   */
  async reserve(p: ProviderName, wanted: number): Promise<number> {
    const grant = Math.min(wanted, this.remaining(p));
    if (grant <= 0) return 0;
    const allowance = dailyAllowance(budgetFor(this.env, p), this.u(p).earlier, this.now);
    const res = await this.db
      .prepare(
        `INSERT INTO usage (provider, day, used, blocked) VALUES (?1, ?2, ?3, 0)
         ON CONFLICT(provider, day) DO UPDATE SET used = usage.used + excluded.used
         WHERE usage.used + excluded.used <= ?4 AND usage.blocked = 0`,
      )
      .bind(p, this.day, grant, allowance)
      .run();
    if (!res.meta.changes) return 0; // someone else used the budget in the meantime
    this.u(p).today += grant;
    return grant;
  }

  /** Gives back reserved calls that were not needed after all. */
  async refund(p: ProviderName, calls: number): Promise<void> {
    if (calls <= 0) return;
    await this.db
      .prepare("UPDATE usage SET used = MAX(0, used - ?3) WHERE provider = ?1 AND day = ?2")
      .bind(p, this.day, calls)
      .run();
    this.u(p).today = Math.max(0, this.u(p).today - calls);
  }

  /** The provider said its quota is used up: stop calling it until tomorrow (UTC). */
  async block(p: ProviderName): Promise<void> {
    await this.db
      .prepare(
        `INSERT INTO usage (provider, day, used, blocked) VALUES (?1, ?2, 0, 1)
         ON CONFLICT(provider, day) DO UPDATE SET blocked = 1`,
      )
      .bind(p, this.day)
      .run();
    this.u(p).blocked = true;
  }

  /** Numbers for GET /v1/status. */
  report(configured: (p: ProviderName) => boolean) {
    const out: Record<string, unknown> = {};
    for (const p of PROVIDERS) {
      const b = budgetFor(this.env, p);
      const u = this.u(p);
      out[p] = {
        configured: configured(p),
        blockedToday: u.blocked,
        usedToday: u.today,
        allowedToday: dailyAllowance(b, u.earlier, this.now),
        remainingToday: this.remaining(p),
        usedThisMonth: u.earlier + u.today,
        monthlyBudget: b.monthly || null,
        dailyBudget: b.daily,
      };
    }
    return out;
  }
}
