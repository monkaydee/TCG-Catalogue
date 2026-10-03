// Ximilar collectibles recognition (https://docs.ximilar.com/collectibles/recognition)
//
// Identify:    POST https://api.ximilar.com/collectibles/v2/tcg_id
//              body { records: [{ _base64: "<jpeg>" }] }
//              → { records: [{ _objects: [{ name: "Card", _tags: { Subcategory: [{ name }] },
//                  _identification: { best_match: { name, full_name, set, set_code, card_number,
//                  out_of, year, rarity, subcategory, links: { "tcgplayer.com": url } },
//                  alternatives: [ …same shape ], distances: [0.31, 0.38, …] } }] }] }
// Auth header: Authorization: Token <key>
// Free plan:   1,000 credits/month; one TCG identification costs 10 credits (≈100 per month).
//
// Notes:
//  - Ximilar does not return a confidence; it returns visual distances (lower = closer). We report
//    confidence = 1 - distance, clamped to 0..1. It is a rough guide for sorting, not a probability.
//  - Supported games per Ximilar: Pokémon, Yu-Gi-Oh!, Magic, One Piece, Lorcana and others; for
//    other games the result can be wrong or empty.
//  - The photo is sent to Ximilar and is never stored or logged by this server.

import { arr, fetchJson, obj, str } from "../util";

const URL = "https://api.ximilar.com/collectibles/v2/tcg_id";

export interface Match {
  name: string;
  fullName?: string;
  set?: string;
  setCode?: string;
  number?: string;
  outOf?: string;
  year?: number;
  rarity?: string;
  game: string; // our game id when we know it, otherwise Ximilar's own label in lower case
  tcgplayerId?: string;
  confidence: number | null;
}

/** Ximilar "Subcategory" → our game id. */
export function ximilarGame(label: string | undefined): string {
  const s = (label ?? "").toLowerCase().replace(/[^a-z]/g, "");
  if (s.includes("pokemon")) return "pokemon";
  if (s.includes("onepiece")) return "one_piece";
  if (s.includes("magic")) return "magic";
  if (s.includes("fusionworld")) return "dragon_ball_fw";
  if (s.includes("dragonball")) return "dragon_ball_super";
  if (s.includes("unionarena")) return "union_arena";
  if (s.includes("weiss")) return "weiss_schwarz";
  if (s.includes("naruto")) return "naruto";
  return (label ?? "unknown").toLowerCase();
}

function toMatch(m: Record<string, unknown>, distance: unknown): Match | null {
  const name = str(m.name);
  if (!name) return null;
  const tcgUrl = str(obj(m.links)["tcgplayer.com"]);
  const tcgplayerId = tcgUrl ? /\/product\/(\d+)/.exec(tcgUrl)?.[1] : undefined;
  const year = typeof m.year === "number" ? m.year : undefined;
  return {
    name,
    fullName: str(m.full_name),
    set: str(m.set),
    setCode: str(m.set_code),
    number: str(m.card_number),
    outOf: str(m.out_of),
    year,
    rarity: str(m.rarity),
    game: ximilarGame(str(m.subcategory)),
    tcgplayerId,
    confidence: typeof distance === "number" ? Math.round(Math.min(1, Math.max(0, 1 - distance)) * 100) / 100 : null,
  };
}

/** Best match first, then alternatives. With a game hint, matches of that game come first. */
export function parseXimilar(json: unknown, gameHint?: string): Match[] {
  const out: Match[] = [];
  for (const rec of arr(obj(json).records).map(obj)) {
    for (const o of arr(rec._objects).map(obj)) {
      const id = obj(o._identification);
      if (!Object.keys(id).length) continue;
      const d = arr(id.distances);
      const best = toMatch(obj(id.best_match), d[0]);
      if (best) out.push(best);
      arr(id.alternatives).forEach((a, i) => {
        const m = toMatch(obj(a), d[i + 1]);
        if (m) out.push(m);
      });
    }
  }
  if (gameHint) out.sort((a, b) => Number(b.game === gameHint) - Number(a.game === gameHint));
  return out.slice(0, 5);
}

/** Base64 without blowing the stack on large images. */
export function toBase64(bytes: Uint8Array): string {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

export async function ximilarIdentify(key: string, jpeg: Uint8Array, gameHint?: string): Promise<Match[]> {
  const json = await fetchJson(
    "ximilar",
    URL,
    {
      method: "POST",
      headers: { Authorization: `Token ${key}`, "content-type": "application/json" },
      body: JSON.stringify({ records: [{ _base64: toBase64(jpeg) }] }),
    },
    25_000,
  );
  return parseXimilar(json, gameHint);
}
