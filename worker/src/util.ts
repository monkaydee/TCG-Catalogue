// Small helpers shared by the provider adapters.

import { QuotaError, type Conditions, type ProviderName } from "./types";

/** A number from anything a provider might send ("1.23", 1.23, null, "N/A"). Negative or 0 → null. */
export function price(v: unknown): number | null {
  const n = typeof v === "string" ? Number(v.replace(/[$,\s]/g, "")) : typeof v === "number" ? v : NaN;
  return Number.isFinite(n) && n > 0 ? Math.round(n * 100) / 100 : null;
}

export function str(v: unknown): string | undefined {
  if (typeof v === "string") return v.trim() || undefined;
  if (typeof v === "number" && Number.isFinite(v)) return String(v);
  return undefined;
}

export function obj(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
}

export function arr(v: unknown): unknown[] {
  return Array.isArray(v) ? v : [];
}

export function emptyConditions(): Conditions {
  return { NM: null, LP: null, MP: null, HP: null, DMG: null };
}

/** True when at least one condition has a price. */
export function hasAnyCondition(c: Conditions): boolean {
  return Object.values(c).some((v) => v !== null);
}

/**
 * Maps a provider's condition label to our short code. Handles "Near Mint", "NEAR_MINT", "NM",
 * "Lightly Played Foil" and so on. Unknown labels (e.g. "Mint", graded tiers) → null.
 */
export function conditionCode(label: string): keyof Conditions | null {
  const s = label.toUpperCase().replace(/[^A-Z]/g, "");
  if (s.startsWith("NEARMINT") || s === "NM") return "NM";
  if (s.startsWith("LIGHTLYPLAYED") || s === "LP") return "LP";
  if (s.startsWith("MODERATELYPLAYED") || s === "MP") return "MP";
  if (s.startsWith("HEAVILYPLAYED") || s === "HP") return "HP";
  if (s.startsWith("DAMAGED") || s === "DMG") return "DMG";
  return null;
}

/** Splits a list into groups of at most `size`. */
export function chunk<T>(items: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += Math.max(1, size)) out.push(items.slice(i, i + Math.max(1, size)));
  return out;
}

/** Normalizes printing names so "Holofoil", "holo foil" and "HOLOFOIL" compare equal. */
export function samePrinting(a: string | undefined, b: string | undefined): boolean {
  if (!b) return true; // no requested preference
  if (!a) return false; // an unspecified provider printing cannot prove a match
  const n = (s: string) => {
    const key = s.toLowerCase().replace(/[^a-z0-9]/g, "");
    return ({ holo: "holofoil", reverse: "reverseholofoil", reversefoil: "reverseholofoil", foil: "holofoil", nonfoil: "normal", nonholo: "normal" } as Record<string, string>)[key] ?? key;
  };
  return n(a) === n(b);
}

/**
 * fetch() with a timeout that returns parsed JSON. 404 returns null ("not found").
 * HTTP 429, and 402/403 with a quota message, become a QuotaError so the caller stops using that
 * provider (for this request, or for the rest of the day when the message mentions a quota).
 * Other errors throw a plain Error. Error messages never contain keys or URLs.
 */
export async function fetchJson(provider: ProviderName, url: string, init: RequestInit = {}, timeoutMs = 10_000): Promise<unknown> {
  const res = await fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
  if (res.status === 404) return null; // "not found" is an answer, not a failure
  if (!res.ok) {
    const text = (await res.text().catch(() => "")).slice(0, 300);
    const quotaWords = /daily|monthly|per day|per month|credits/i.test(text);
    if (res.status === 429) throw new QuotaError(provider, "HTTP 429", quotaWords);
    if ((res.status === 402 || res.status === 403) && quotaWords) throw new QuotaError(provider, `HTTP ${res.status}`, true);
    throw new Error(`${provider}: HTTP ${res.status}`);
  }
  return res.json();
}
