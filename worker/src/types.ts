// Shared types for the price server. Everything the app sends or receives is described here.

/** The secrets and settings Cloudflare hands to the Worker. Every secret is optional. */
export interface Env {
  DB: D1Database;
  FEEDBACK_IMAGES?: R2Bucket;
  FEEDBACK_ADMIN_KEY?: string;

  // Secrets (set with `wrangler secret put` or by the GitHub workflow). Missing = provider skipped.
  APP_KEY?: string;
  JUSTTCG_KEY?: string;
  POKETRACE_KEY?: string;
  TCGAPI_KEY?: string;
  XIMILAR_KEY?: string;
  PSA_TOKEN?: string;
  RAPIDAPI_KEY?: string;
  PPT_KEY?: string;
  EBAY_CLIENT_ID?: string;
  EBAY_CLIENT_SECRET?: string;

  // Plain settings from [vars] in wrangler.toml. All are strings; see config.ts for defaults.
  [name: string]: unknown;
}

/** Game ids as the app sends them (the Kotlin enum names, any case). */
export type Game =
  | "pokemon"
  | "one_piece"
  | "magic"
  | "dragon_ball_fw"
  | "dragon_ball_super"
  | "union_arena"
  | "weiss_schwarz"
  | "naruto";

/** One card in a POST /v1/prices request, after validation. */
export interface CardRequest {
  game: Game;
  id: string;
  name: string;
  set: string;
  setAliases?: string[];
  releaseYear?: string;
  number: string;
  tcgplayerId?: string;
  printing?: string;
  printingUnique?: boolean;
  /** Card language ("EN", "DE", "JA" …); prices for other languages than English come from eBay only. */
  language?: string;
  market?: "US" | "DE";
  /** The card's name in [language] (e.g. "Flamara" for Flareon in German), when known. */
  localName?: string;
  graded?: boolean;
  /** Cache key (game + best identifier + printing), filled in by the server. */
  key: string;
}

/** Raw (ungraded) prices in USD, one per condition. null = the source did not report it. */
export interface Conditions {
  NM: number | null;
  LP: number | null;
  MP: number | null;
  HP: number | null;
  DMG: number | null;
}

/** A raw price result from one provider. */
export interface RawPrice {
  conditions: Conditions;
  conditionEvidence?: Partial<Record<keyof Conditions,{listings:number;low:number;high:number;evidence:string;excluded:number}>>;
  /**
   * TCGplayer "market price" when a source only gives one blended number and not a price per
   * condition (TCG API free tier, RapidAPI). It is kept apart so it is never mistaken for NM.
   */
  market: number | null;
  source: string;
  /** Currency of the prices (default USD). */
  currency?: string;
  /** For asking prices: how many listings, and their lowest and highest price. */
  listings?: number;
  low?: number;
  high?: number;
}

/** One graded price. Only ever filled from data the provider labels as graded. */
export interface GradedPrice {
  grader: string; // "PSA", "BGS", "CGC", "SGC", ...
  grade: string; // "10", "9.5", "Authentic", ...
  qualifier?: string; // "Black Label", "Pristine", "Perfect"; never a plain 10
  price: number;
  currency: string;
  source: string;
  date?: string; // ISO date of the price, when known
  sales?: number; // number of sales the price is based on, when known
  listings?: number; // number of current listings an asking price is based on
  evidence?: string;
  excluded?: number;
  fetchedAt?: string;
  stale?: boolean;
  low?: number; // lowest and highest of those prices
  high?: number;
}

/** The provider names, also used as the budget counter names in D1. */
export type ProviderName = "justtcg" | "tcgapi" | "poketrace" | "rapidapi" | "ppt" | "psa" | "ximilar" | "ebay";

/**
 * Thrown by an adapter when the provider says "too many requests" or "quota used up".
 * The server then stops using that provider until the next UTC day.
 */
export class QuotaError extends Error {
  /**
   * @param untilTomorrow true when the provider says a daily/monthly quota is used up (stop for
   *   the rest of the UTC day); false for a short burst limit (stop only for this request).
   */
  constructor(public provider: ProviderName, message = "quota exceeded", public untilTomorrow = false) {
    super(`${provider}: ${message}`);
  }
}

/** PSA population numbers, from the PSA API or the RapidAPI population API. */
export interface Population {
  total: number | null;
  higher: number | null;
  byGrade: Record<string, number>;
  description?: string;
  specId?: string;
  source: string;
}

/** Result of a batch call: prices by card key. Cards missing from the map were not found. */
export type BatchResult<T> = Map<string, T>;

/** A provider that can price raw cards. */
export interface RawProvider {
  name: ProviderName;
  /** How many cards fit in one call (1 = no batching). */
  batchSize: number;
  callsPerBatch?: number;
  /** Wait at least this long between two calls in the same request (provider burst limits). */
  minIntervalMs?: number;
  /** Is this card something the provider can look up (right game, needed identifier present)? */
  supports(card: CardRequest): boolean;
  /** One provider call for up to `batchSize` cards. */
  fetch(cards: CardRequest[], key: string): Promise<BatchResult<RawPrice>>;
}

/** A provider that can price graded cards. */
export interface GradedProvider {
  name: ProviderName;
  batchSize: number;
  callsPerBatch?: number;
  minIntervalMs?: number;
  supports(card: CardRequest): boolean;
  fetch(cards: CardRequest[], key: string): Promise<BatchResult<GradedPrice[]>>;
}
