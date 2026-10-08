import type { Env } from "./types";
import { parseCard } from "./prices";
import { boundedJson, BodyTooLarge } from "./util";
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {status, headers: {"content-type": "application/json"}});
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const hash = async (value: string) => Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))), b => b.toString(16).padStart(2,"0")).join("");
export function normalizeRead(text: string): string { return text.normalize("NFKC").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "").slice(0, 300); }
/** One-time privacy migration: remove prior photos/free OCR, preserving new hash-only reports. */
export async function ensureLearningPolicy(env: Env): Promise<void> {
  if (await env.DB.prepare("SELECT revision FROM learning_policy WHERE revision=2").first()) return;
  const legacy = (await env.DB.prepare("SELECT image_key FROM recognition_reports WHERE image_key IS NOT NULL").all<{image_key:string}>()).results;
  const keys = legacy.map(r=>r.image_key).filter(k=>!k.startsWith("d1:"));
  // Do not drop references to R2 objects when their binding is unavailable.
  if (keys.length && !env.FEEDBACK_IMAGES) throw new Error("Legacy photo deletion needs the private bucket binding");
  if (env.FEEDBACK_IMAGES) for (let i=0;i<keys.length;i+=1000) await env.FEEDBACK_IMAGES.delete(keys.slice(i,i+1000));
  await env.DB.batch([
    env.DB.prepare("DELETE FROM recognition_rules WHERE context IN (SELECT context FROM recognition_reports WHERE image_key IS NOT NULL OR json_type(payload,'$.read') IS NOT NULL)"),
    env.DB.prepare("DELETE FROM recognition_images"),
    env.DB.prepare("DELETE FROM recognition_reports WHERE image_key IS NOT NULL OR json_type(payload,'$.read') IS NOT NULL"),
    env.DB.prepare("INSERT OR IGNORE INTO learning_policy(revision) VALUES (2)"),
  ]);
}
export function validJpeg(bytes: Uint8Array): boolean {
  if (bytes.length > 300000 || bytes.length < 10 || bytes[0] !== 255 || bytes[1] !== 216) return false;
  let p = 2; let dimensionsValid = false;
  while (p + 4 < bytes.length) {
    if (bytes[p++] !== 255) return false;
    const marker = bytes[p++];
    if (marker === 225) return false; // EXIF and embedded metadata are never accepted.
    if (marker === 218 || marker === 217) return dimensionsValid;
    const length = (bytes[p] << 8) | bytes[p+1];
    if (length < 2 || p + length > bytes.length) return false;
    if ([192,193,194].includes(marker)) {
      const h = (bytes[p+3] << 8) | bytes[p+4], w = (bytes[p+5] << 8) | bytes[p+6];
      dimensionsValid = w >= 100 && h >= 100 && w <= 800 && h <= 800 && w/h >= 0.55 && w/h <= 0.8;
    }
    p += length;
  }
  return false;
}
export async function feedback(req: Request, env: Env, path: string): Promise<Response> {
  if (path === "/v1/feedback/rules" && req.method === "GET") {
    await ensureLearningPolicy(env);
    const rules = (await env.DB.prepare("SELECT context,card_id,game,language,version FROM recognition_rules WHERE enabled = 1 ORDER BY version DESC LIMIT 500").all()).results;
    // Only manually reviewed rules are published. HTTPS authenticates the origin; hash detects corrupt caches.
    const payload = JSON.stringify(rules);
    return json({version: 1, payload, sha256: await hash(payload)});
  }
  if (Number(req.headers.get("content-length") || 0) > 450000) return json({error: "body_too_large"}, 413);
  const reader = req.body?.getReader(); if (!reader) return json({error:"invalid_json"},400);
  const chunks: Uint8Array[] = []; let size = 0;
  while (true) { const chunk = await reader.read(); if (chunk.done) break; size += chunk.value.byteLength;
    if (size > 450000) { await reader.cancel(); return json({error:"body_too_large"},413); } chunks.push(chunk.value); }
  const bytes = new Uint8Array(size); let offset=0; for (const chunk of chunks) { bytes.set(chunk,offset); offset+=chunk.length; }
  const text = new TextDecoder().decode(bytes);
  let body: Record<string, unknown>; try { body = JSON.parse(text); } catch { return json({error:"invalid_json"},400); }
  // JPEG shape/EXIF checks cannot classify sexual, illegal or unrelated content.
  // Fail closed before registration, storage or forwarding, including forged clients.
  if (req.method === "POST" && body && typeof body === "object" && ("image" in body || "photoApproved" in body))
    return json({error:"photo_uploads_disabled"},422);
  if (req.method !== "POST" && req.method !== "DELETE") return json({error:"method_not_allowed"},405);
  if (!body || typeof body !== "object" || typeof body.install !== "string" || !uuid.test(body.install) || typeof body.token !== "string" || !uuid.test(body.token)) return json({error:"invalid_install"},400);
  if (req.method === "POST" && (typeof body.id !== "string" || !uuid.test(body.id) || !parseCard(body.card))) return json({error:"invalid_report"},400);
  await ensureLearningPolicy(env);
  const install = await hash(`${env.APP_KEY}|${body.install}`), token = await hash(body.token);
  if (req.method === "POST") await env.DB.prepare("INSERT OR IGNORE INTO learning_installs(id,token_hash) VALUES (?,?)").bind(install,token).run();
  const stored = await env.DB.prepare("SELECT token_hash FROM learning_installs WHERE id=?").bind(install).first<{token_hash:string}>();
  if (!stored && req.method === "DELETE") return json({deleted:true});
  if (stored?.token_hash !== token) return json({error:"unauthorized_install"},403);
  if (req.method === "DELETE") {
    const images = (await env.DB.prepare("SELECT image_key FROM recognition_reports WHERE install_hash=? AND image_key IS NOT NULL").bind(install).all<{image_key:string}>()).results;
    if (env.FEEDBACK_IMAGES) for (let i=0;i<images.length;i+=1000) {
      const keys = images.slice(i,i+1000).map(image => image.image_key).filter(key => !key.startsWith("d1:"));
      if (keys.length) await env.FEEDBACK_IMAGES.delete(keys);
    }
    await env.DB.prepare("DELETE FROM recognition_images WHERE id IN (SELECT id FROM recognition_reports WHERE install_hash=?)").bind(install).run();
    await env.DB.prepare("DELETE FROM recognition_reports WHERE install_hash=?").bind(install).run();
    // Disable derived rules once consent withdrawal invalidates the evidence threshold.
    await env.DB.prepare("UPDATE recognition_rules SET enabled=0 WHERE NOT EXISTS (SELECT 1 FROM recognition_reports r WHERE r.context=recognition_rules.context AND r.card_id=recognition_rules.card_id AND r.kind='recognition' AND r.confirmed=1 GROUP BY r.context,r.card_id HAVING COUNT(DISTINCT r.install_hash)>=3)").run();
    await env.DB.prepare("DELETE FROM learning_installs WHERE id=?").bind(install).run();
    return json({deleted:true});
  }
  if (req.method !== "POST") return json({error:"method_not_allowed"},405);
  if (typeof body.id !== "string" || !uuid.test(body.id)) return json({error:"invalid_id"},400);
  const card = parseCard(body.card); if (!card) return json({error:"invalid_card"},400);
  const kind = body.kind === "price" ? "price" : "recognition";
  const read = typeof body.read === "string" ? body.read.slice(0,5000) : "";
  if (/@|https?:\/\//i.test(read)) return json({error:"non_card_text"},400);
  const readHash = typeof body.readHash === "string" && /^[a-f0-9]{64}$/.test(body.readHash) ? body.readHash : await hash(normalizeRead(read));
  const context = `${card.game}|${card.language || "EN"}|${readHash}`;
  const count = await env.DB.prepare("SELECT COUNT(*) n FROM recognition_reports WHERE install_hash=? AND created_at>?").bind(install,Date.now()-86400000).first<{n:number}>();
  if ((count?.n || 0) >= 100) return json({error:"report_limit"},429);
  const existing = await env.DB.prepare("SELECT install_hash FROM recognition_reports WHERE id=?").bind(body.id).first<{install_hash:string}>();
  if (existing) return existing.install_hash === install ? json({accepted:true}) : json({error:"id_conflict"},409);
  const total = await env.DB.prepare("SELECT reports n FROM learning_totals WHERE id=1").first<{n:number}>();
  if ((total?.n || 0) >= 20000) return json({error:"storage_limit"},503);
  const imageKey: string | null = null;
  const raw = body.evidence && typeof body.evidence === "object" ? body.evidence as Record<string,unknown> : {};
  const chosen = body.selectedGrade && typeof body.selectedGrade === "object" ? body.selectedGrade as Record<string,unknown> : {};
  const selectedGrade = typeof chosen.grader === "string" && ["PSA","CGC","BGS","SGC"].includes(chosen.grader) && typeof chosen.grade === "string" && /^(?:10|[1-9](?:\.5)?)$/.test(chosen.grade)
    ? {grader:chosen.grader,grade:chosen.grade,qualifier:typeof chosen.qualifier === "string" ? chosen.qualifier.slice(0,40) : null} : null;
  const evidence = kind === "price" ? {
    amount: typeof raw.amount === "number" && Number.isFinite(raw.amount) && raw.amount >= 0 && raw.amount <= 1e9 ? raw.amount : null,
    currency: raw.currency === "USD" || raw.currency === "EUR" ? raw.currency : null,
    source: typeof raw.source === "string" ? raw.source.slice(0,100) : null,
    grader: typeof raw.grader === "string" ? raw.grader.slice(0,10) : null,
    grade: typeof raw.grade === "string" ? raw.grade.slice(0,10) : null,
    qualifier: typeof raw.qualifier === "string" ? raw.qualifier.slice(0,50) : null,
    quotedAt: typeof raw.quotedAt === "number" && Number.isFinite(raw.quotedAt) ? raw.quotedAt : null,
  } : null;
  try {
    // Keep machine identifiers and evidence, never free-form OCR, names, set prose or suggestions.
    const identity = {game:card.game,id:card.id,language:card.language || "EN",number:card.number,printing:card.printing};
    await env.DB.prepare("INSERT INTO recognition_reports(id,install_hash,kind,context,card_id,game,language,payload,image_key,created_at,confirmed) VALUES (?,?,?,?,?,?,?,?,?,?,?)").bind(body.id,install,kind,context,card.id,card.game,card.language || "EN",JSON.stringify({card:identity,evidence,selectedGrade}),imageKey,Date.now(),body.humanConfirmed === true ? 1 : 0).run();
  } catch (e) { throw e; }
  return json({accepted:true});
}
export async function moderate(req: Request, env: Env): Promise<Response> {
  // This key is never shipped to phones. Three pseudonymous installs are a review threshold, not proof of distinct people.
  if (!env.FEEDBACK_ADMIN_KEY || req.headers.get("authorization") !== `Bearer ${env.FEEDBACK_ADMIN_KEY}`) return json({error:"unauthorized"},401);
  await ensureLearningPolicy(env);
  if (req.method === "GET") {
    const url = new URL(req.url);
    const crop = url.searchParams.get("crop");
    if (crop) {
      return json({error:"photo_uploads_disabled"},410);
    }
    const inspect = url.searchParams.get("inspect");
    if (inspect) {
      const reports = (await env.DB.prepare("SELECT id,card_id,payload,image_key,created_at FROM recognition_reports WHERE context=? ORDER BY created_at DESC LIMIT 50").bind(inspect).all()).results;
      return json({reports});
    }
    const candidates = await env.DB.prepare("SELECT context,card_id,game,language,COUNT(DISTINCT install_hash) votes FROM recognition_reports WHERE kind='recognition' AND confirmed=1 GROUP BY context,card_id HAVING votes>=3 ORDER BY votes DESC LIMIT 100").all();
    return json({candidates:candidates.results});
  }
  let body:{context?:string;cardId?:string;disable?:boolean};
  try {body=await boundedJson(req,4000) as typeof body;}catch(error){return json({error:error instanceof BodyTooLarge ? "body_too_large" : "invalid_json"},error instanceof BodyTooLarge ? 413 : 400);}
  if (!body || typeof body !== "object") return json({error:"invalid_rule"},400);
  if (!body.context || !body.cardId) return json({error:"invalid_rule"},400);
  if (body.disable) { await env.DB.prepare("UPDATE recognition_rules SET enabled=0 WHERE context=?").bind(body.context).run(); return json({disabled:true}); }
  const row = await env.DB.prepare("SELECT game,language,COUNT(DISTINCT install_hash) votes FROM recognition_reports WHERE context=? AND card_id=? AND kind='recognition' AND confirmed=1 GROUP BY game,language").bind(body.context,body.cardId).first<{game:string;language:string;votes:number}>();
  if (!row || row.votes < 3) return json({error:"insufficient_evidence"},400);
  await env.DB.prepare("INSERT INTO recognition_rules(context,card_id,game,language,version,enabled) VALUES (?,?,?,?,?,1) ON CONFLICT(context) DO UPDATE SET card_id=excluded.card_id,game=excluded.game,language=excluded.language,version=excluded.version,enabled=1").bind(body.context,body.cardId,row.game,row.language,Date.now()).run();
  return json({approved:true});
}
export async function expireFeedback(env: Env): Promise<void> {
  await ensureLearningPolicy(env);
  const cutoff=Date.now()-90*86400000;
  // The global 20,000 report limit also bounds a complete retention pass.
  const rows = (await env.DB.prepare("SELECT id,image_key FROM recognition_reports WHERE created_at<? LIMIT 20000").bind(cutoff).all<{id:string;image_key:string|null}>()).results;
  const images = rows.flatMap(r => r.image_key && !r.image_key.startsWith("d1:") ? [r.image_key] : []);
  if (env.FEEDBACK_IMAGES) for (let i=0;i<images.length;i+=1000) await env.FEEDBACK_IMAGES.delete(images.slice(i,i+1000));
  await env.DB.batch([
    env.DB.prepare("DELETE FROM recognition_images WHERE id IN (SELECT id FROM recognition_reports WHERE created_at<?)").bind(cutoff),
    env.DB.prepare("DELETE FROM recognition_reports WHERE created_at<?").bind(cutoff),
    env.DB.prepare("UPDATE recognition_rules SET enabled=0 WHERE NOT EXISTS (SELECT 1 FROM recognition_reports r WHERE r.context=recognition_rules.context AND r.card_id=recognition_rules.card_id AND r.kind='recognition' AND r.confirmed=1 GROUP BY r.context,r.card_id HAVING COUNT(DISTINCT r.install_hash)>=3)"),
    env.DB.prepare("DELETE FROM learning_installs WHERE NOT EXISTS (SELECT 1 FROM recognition_reports r WHERE r.install_hash=learning_installs.id)"),
  ]);
}
