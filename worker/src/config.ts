// Settings read from wrangler.toml [vars], with safe defaults when a value is missing.

import type { Env, ProviderName } from "./types";

/** Daily and monthly call budgets for one provider. monthly = 0 means "no monthly limit". */
export interface Budget {
  daily: number;
  monthly: number;
}

/**
 * Default budgets = the provider's free tier minus a 10% safety margin.
 * Counted in calls to the provider (one batch of 20 cards = 1 call).
 */
export const DEFAULT_BUDGETS: Record<ProviderName, Budget> = {
  justtcg: { daily: 90, monthly: 900 }, // free: 100/day, 1,000/month
  tcgapi: { daily: 90, monthly: 0 }, // free: 100/day
  poketrace: { daily: 225, monthly: 0 }, // free: 250/day
  rapidapi: { daily: 3, monthly: 22 }, // free: 25/month (TCGplayer Price Data BASIC, hard limit)
  ppt: { daily: 9000, monthly: 0 }, // paid Pro: 20,000 credits/day, 2 credits per graded lookup
  psa: { daily: 90, monthly: 0 }, // free token: 100/day (PSA may have lowered this, see docs)
  ebay: { daily: 2400, monthly: 0 }, // free: 5,000 Browse calls/day; a graded lookup makes 2 calls
  ximilar: { daily: 10, monthly: 90 }, // free: 1,000 credits/month, 10 credits per identification
};

function num(env: Env, name: string, fallback: number): number {
  const raw = env[name];
  if (raw === undefined || raw === null || raw === "") return fallback;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

/** Budget for a provider, e.g. vars BUDGET_JUSTTCG_DAILY and BUDGET_JUSTTCG_MONTHLY. */
export function budgetFor(env: Env, p: ProviderName): Budget {
  const up = p.toUpperCase();
  const d = DEFAULT_BUDGETS[p];
  return { daily: num(env, `BUDGET_${up}_DAILY`, d.daily), monthly: num(env, `BUDGET_${up}_MONTHLY`, d.monthly) };
}

export function settings(env: Env) {
  return {
    rawTtlHours: num(env, "RAW_TTL_HOURS", 24),
    gradedTtlHours: num(env, "GRADED_TTL_HOURS", 72),
    notFoundTtlHours: num(env, "NOT_FOUND_TTL_HOURS", 6),
    certTtlDays: num(env, "CERT_TTL_DAYS", 30),
    popTtlDays: num(env, "POP_TTL_DAYS", 7),
    ipDailyLimit: num(env, "IP_DAILY_LIMIT", 500),
    /** Cloudflare's free plan allows 50 outgoing requests per Worker run; stay well under it. */
    maxProviderCallsPerRequest: num(env, "MAX_PROVIDER_CALLS_PER_REQUEST", 20),
    /** RapidAPI host for the PSA population API. Empty = not used (see docs/CLOUDFLARE.md). */
    rapidapiPopHost: String(env["RAPIDAPI_POP_HOST"] ?? ""),
    rapidapiPopPath: String(env["RAPIDAPI_POP_PATH"] ?? "/psa/pop"),
    rapidapiTcgHost: String(env["RAPIDAPI_TCG_HOST"] ?? "tcgplayer-price-data.p.rapidapi.com"),
  };
}

export type Settings = ReturnType<typeof settings>;
