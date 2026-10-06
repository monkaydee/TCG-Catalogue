// TCG Catalogue price server: a free Cloudflare Worker that keeps provider keys out of the app,
// shares results between all users through a D1 cache, and keeps every provider within its
// free-tier limits. See docs/CLOUDFLARE.md for setup and the API contract.

import { sealedPrice } from "./sealed";
import { feedback, moderate, expireFeedback } from "./feedback";
import { Budgets } from "./budget";
import { Cache } from "./cache";
import { settings, type Settings } from "./config";
import { gradedForSchema, getPrices, hours, MAX_CARDS, parseCard, type ChainProvider } from "./prices";
import { justTcgGraded, justTcgRaw } from "./providers/justtcg";
import { poketrace } from "./providers/poketrace";
import { ppt } from "./providers/ppt";
import { ebay, ebayRaw } from "./providers/ebay";
import { psaCert, psaSpecPop, type Cert } from "./providers/psa";
import { rapidPop, rapidTcg } from "./providers/rapidapi";
import { tcgApi } from "./providers/tcgapi";
import { ximilarIdentify } from "./providers/ximilar";
import { QuotaError, type CardRequest, type Env, type GradedPrice, type Population, type ProviderName, type RawPrice } from "./types";

const DAY = 86_400_000;
const MAX_IMAGE_BYTES = 1_536_000; // 1.5 MB
const MAX_JSON_BYTES = 100_000;

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8" } });
const fail = (status: number, error: string, extra: Record<string, unknown> = {}) => json({ error, ...extra }, status);

/** Which provider secrets exist. */
function keyOf(env: Env, p: ProviderName): string | undefined {
  const k = {
    justtcg: env.JUSTTCG_KEY,
    tcgapi: env.TCGAPI_KEY,
    poketrace: env.POKETRACE_KEY,
    rapidapi: env.RAPIDAPI_KEY,
    ppt: env.PPT_KEY,
    psa: env.PSA_TOKEN,
    ximilar: env.XIMILAR_KEY,
    ebay: env.EBAY_CLIENT_ID?.trim() && env.EBAY_CLIENT_SECRET?.trim() ? `${env.EBAY_CLIENT_ID.trim()}:${env.EBAY_CLIENT_SECRET.trim()}` : undefined,
  }[p];
  return k && k.trim() ? k.trim() : undefined;
}

/** Provider chains in priority order; providers without a key are left out. */
function chains(env: Env, s: Settings) {
  const withKey = <T>(list: ChainProvider<T>[]) =>
    list.flatMap((provider) => {
      const key = keyOf(env, provider.name);
      return key ? [{ provider, key }] : [];
    });
  // These sources price the English print; other languages only come from eBay listings in that language.
  const english = <P extends { supports(c: CardRequest): boolean }>(p: P): P =>
    ({ ...p, supports: (c: CardRequest) => (!c.language || c.language === "EN") && p.supports(c) });
  return {
    raw: withKey<RawPrice>([english(justTcgRaw()), english(tcgApi()), english(poketrace()), english(rapidTcg(s.rapidapiTcgHost)), ebayRaw()]),
    graded: withKey<GradedPrice[]>([english(justTcgGraded()), english(ppt()), ebay()]),
  };
}

/** Constant-time comparison so the key cannot be guessed from response timing. */
function sameSecret(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

/** Counts this request for the caller's IP (stored only as a salted daily hash). Returns false over the limit. */
async function ipAllowed(req: Request, env: Env, limit: number): Promise<boolean> {
  if (limit <= 0) return true;
  const ip = req.headers.get("cf-connecting-ip") ?? "unknown";
  const day = new Date().toISOString().slice(0, 10);
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${day}|${ip}|${env.APP_KEY}`));
  const hash = [...new Uint8Array(digest).slice(0, 12)].map((b) => b.toString(16).padStart(2, "0")).join("");
  const row = await env.DB.prepare(
    `INSERT INTO ip_hits (ip_hash, day, hits) VALUES (?1, ?2, 1)
     ON CONFLICT(ip_hash, day) DO UPDATE SET hits = ip_hits.hits + 1 RETURNING hits`,
  )
    .bind(hash, day)
    .first<{ hits: number }>();
  return (row?.hits ?? 0) <= limit;
}

// ---------- Endpoints ----------

async function prices(req: Request, env: Env, s: Settings): Promise<Response> {
  if (Number(req.headers.get("content-length") ?? 0) > MAX_JSON_BYTES) return fail(413, "body_too_large");
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return fail(400, "invalid_json");
  }
  const list = (body as { cards?: unknown })?.cards;
  if (!Array.isArray(list)) return fail(400, "missing_cards");
  if (list.length > MAX_CARDS) return fail(400, "too_many_cards", { max: MAX_CARDS });
  const cards: CardRequest[] = [];
  const invalid: number[] = [];
  list.forEach((c, i) => {
    const card = parseCard(c);
    if (card) cards.push(card);
    else invalid.push(i);
  });

  const budgets = await new Budgets(env.DB, env).load();
  const { raw, graded } = chains(env, s);
  const results = await getPrices(cards, {
    cache: new Cache(env.DB),
    gate: budgets,
    raw,
    graded,
    rawTtlMs: hours(s.rawTtlHours),
    gradedTtlMs: hours(s.gradedTtlHours),
    notFoundTtlMs: hours(s.notFoundTtlHours),
    maxCalls: s.maxProviderCallsPerRequest,
    now: Date.now(),
  });
  const schemaVersion = (body as { schemaVersion?: unknown })?.schemaVersion;
  return json({ results: results.map((r) => ({ ...r, graded: gradedForSchema(r.graded, schemaVersion) })), ...(invalid.length ? { invalid } : {}) });
}

/**
 * Generic "cached single lookup": fresh cache → budget → provider → cache; stale cache when the
 * budget is gone or the provider fails.
 */
async function cachedLookup<T>(
  env: Env,
  key: string,
  ttlMs: number,
  provider: ProviderName,
  budgets: Budgets,
  load: () => Promise<T | null>,
): Promise<{ value: T | null; fetchedAt: number; stale: boolean } | { unavailable: true }> {
  const cache = new Cache(env.DB);
  const hit = await cache.get<T | null>(key);
  const now = Date.now();
  if (hit && hit.expiresAt > now) return { value: hit.value, fetchedAt: hit.fetchedAt, stale: false };
  const staleOrNothing = () => (hit ? { value: hit.value, fetchedAt: hit.fetchedAt, stale: true } : ({ unavailable: true } as const));
  if (!keyOf(env, provider) || (await budgets.reserve(provider, 1)) === 0) return staleOrNothing();
  try {
    const value = await load();
    // "Not found" answers are cached for a day only, in case the lookup was premature.
    await cache.putMany([{ key, value, source: provider, fetchedAt: now, ttlMs: value === null ? DAY : ttlMs }]);
    return { value, fetchedAt: now, stale: false };
  } catch (e) {
    if (e instanceof QuotaError && e.untilTomorrow) await budgets.block(provider);
    console.log(`lookup error: ${e instanceof Error ? e.message : "unknown"}`);
    return staleOrNothing();
  }
}

function respond<T>(r: Awaited<ReturnType<typeof cachedLookup<T>>>, field: string): Response {
  if ("unavailable" in r) return fail(503, "unavailable", { reason: "budget used up or provider not configured; try again tomorrow" });
  if (r.value === null) return fail(404, "not_found", { stale: r.stale });
  return json({ [field]: r.value, fetchedAt: new Date(r.fetchedAt).toISOString(), stale: r.stale });
}

async function cert(env: Env, s: Settings, certNumber: string): Promise<Response> {
  if (!/^\d{5,12}$/.test(certNumber)) return fail(400, "invalid_cert_number");
  const budgets = await new Budgets(env.DB, env).load();
  const r = await cachedLookup<Cert>(env, `cert|${certNumber}`, s.certTtlDays * DAY, "psa", budgets, () =>
    psaCert(keyOf(env, "psa")!, certNumber),
  );
  return respond(r, "cert");
}

async function pop(env: Env, s: Settings, url: URL): Promise<Response> {
  const certNumber = url.searchParams.get("cert") ?? "";
  const specId = url.searchParams.get("specId") ?? "";
  const budgets = await new Budgets(env.DB, env).load();
  const ttl = s.popTtlDays * DAY;

  if (/^\d{1,12}$/.test(specId)) {
    return respond(
      await cachedLookup<Population>(env, `pop|spec|${specId}`, ttl, "psa", budgets, () => psaSpecPop(keyOf(env, "psa")!, specId)),
      "population",
    );
  }
  if (!/^\d{5,12}$/.test(certNumber)) return fail(400, "need_cert_or_specId");

  // Preferred: the RapidAPI population API, when its host is configured.
  if (s.rapidapiPopHost && keyOf(env, "rapidapi")) {
    const r = await cachedLookup<Population>(env, `pop|cert|${certNumber}`, ttl, "rapidapi", budgets, () =>
      rapidPop(s.rapidapiPopHost, s.rapidapiPopPath, keyOf(env, "rapidapi")!, certNumber),
    );
    if (!("unavailable" in r)) return respond(r, "population");
  }
  // Otherwise: PSA cert lookup (shares the /v1/cert cache) → grade breakdown for its SpecID.
  const c = await cachedLookup<Cert>(env, `cert|${certNumber}`, s.certTtlDays * DAY, "psa", budgets, () =>
    psaCert(keyOf(env, "psa")!, certNumber),
  );
  if ("unavailable" in c || c.value === null) return respond(c, "population");
  const certValue = c.value;
  if (!certValue.specId) {
    return json({
      population: { total: certValue.population.total, higher: certValue.population.higher, byGrade: {}, description: certValue.description, source: "psa" },
      fetchedAt: new Date(c.fetchedAt).toISOString(),
      stale: c.stale,
    });
  }
  const specId2 = certValue.specId;
  const p = await cachedLookup<Population>(env, `pop|spec|${specId2}`, ttl, "psa", budgets, () => psaSpecPop(keyOf(env, "psa")!, specId2));
  if ("unavailable" in p || p.value === null) {
    // Fall back to the totals that came with the cert.
    return json({
      population: { total: certValue.population.total, higher: certValue.population.higher, byGrade: {}, description: certValue.description, specId: specId2, source: "psa" },
      fetchedAt: new Date(c.fetchedAt).toISOString(),
      stale: c.stale,
    });
  }
  return json({
    population: { ...p.value, higher: certValue.population.higher },
    fetchedAt: new Date(p.fetchedAt).toISOString(),
    stale: p.stale || c.stale,
  });
}

async function identify(req: Request, env: Env): Promise<Response> {
  const key = keyOf(env, "ximilar");
  if (!key) return fail(503, "unavailable", { reason: "identification is not configured" });
  if (Number(req.headers.get("content-length") ?? 0) > MAX_IMAGE_BYTES) return fail(413, "image_too_large", { maxBytes: MAX_IMAGE_BYTES });
  const bytes = new Uint8Array(await req.arrayBuffer());
  if (bytes.length > MAX_IMAGE_BYTES) return fail(413, "image_too_large", { maxBytes: MAX_IMAGE_BYTES });
  if (bytes.length < 3 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff) return fail(415, "jpeg_required");
  const budgets = await new Budgets(env.DB, env).load();
  if ((await budgets.reserve("ximilar", 1)) === 0) return fail(503, "unavailable", { reason: "daily identification budget used up; try again tomorrow" });
  const game = (req.headers.get("x-game") ?? "").toLowerCase() || undefined;
  try {
    // The image goes straight to Ximilar. It is not stored, cached or logged here.
    return json({ matches: await ximilarIdentify(key, bytes, game) });
  } catch (e) {
    if (e instanceof QuotaError && e.untilTomorrow) await budgets.block("ximilar");
    console.log(`identify error: ${e instanceof Error ? e.message : "unknown"}`);
    return fail(502, "provider_error");
  }
}

async function status(env: Env): Promise<Response> {
  const budgets = await new Budgets(env.DB, env).load();
  return json({
    ok: true,
    priceMatchingRevision: 5,
    time: new Date().toISOString(),
    providers: budgets.report((p) => !!keyOf(env, p)),
    cache: await new Cache(env.DB).count(),
    learning: { textReports: true, privatePhotos: true, photoStorage: env.FEEDBACK_IMAGES ? "r2" : "private-d1-32mib", dashboardModeration: true, moderationConfigured: !!env.FEEDBACK_ADMIN_KEY, retentionDays: 90 },
  });
}

// ---------- Entry points ----------

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname.replace(/\/+$/, "");
    if (!path.startsWith("/v1/")) return fail(404, "not_found");
    if (!env.APP_KEY) return fail(500, "server_not_configured", { reason: "APP_KEY secret is missing" });
    if (!sameSecret(req.headers.get("x-app-key") ?? "", env.APP_KEY)) return fail(401, "unauthorized");

    const s = settings(env);
    try {
      if (!(await ipAllowed(req, env, s.ipDailyLimit))) return fail(429, "daily_limit_reached");

      if (path === "/v1/feedback/moderate") return await moderate(req, env);
      if (path === "/v1/feedback" || path === "/v1/feedback/rules") return await feedback(req, env, path);
      if (path === "/v1/sealed/price" && req.method === "POST") return await sealedPrice(req, env);
      if (path === "/v1/prices" && req.method === "POST") return await prices(req, env, s);
      if (path === "/v1/identify" && req.method === "POST") return await identify(req, env);
      if (path === "/v1/status" && req.method === "GET") return await status(env);
      if (path === "/v1/pop" && req.method === "GET") return await pop(env, s, url);
      const m = /^\/v1\/cert\/([^/]+)$/.exec(path);
      if (m && req.method === "GET") return await cert(env, s, decodeURIComponent(m[1]));
      return fail(404, "not_found");
    } catch (e) {
      console.log(`server error: ${e instanceof Error ? e.message : "unknown"}`);
      return fail(500, "server_error");
    }
  },

  /** Daily clean-up (cron in wrangler.toml): drop very old cache rows and counters. */
  async scheduled(_event: ScheduledController, env: Env): Promise<void> {
    await expireFeedback(env);
    const now = Date.now();
    const old = (days: number) => new Date(now - days * DAY).toISOString().slice(0, 10);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM cache WHERE expires_at < ?1").bind(now - 60 * DAY),
      env.DB.prepare("DELETE FROM usage WHERE day < ?1").bind(old(100)),
      env.DB.prepare("DELETE FROM ip_hits WHERE day < ?1").bind(old(2)),
    ]);
  },
} satisfies ExportedHandler<Env>;
