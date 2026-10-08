// PSA Public API (https://api.psacard.com/publicapi/swagger.json)
//
// Cert:        GET https://api.psacard.com/publicapi/cert/GetByCertNumber/{certNumber}
//              → { PSACert: { CertNumber, SpecID, Year, Brand, Category, CardNumber, Subject,
//                  Variety, CardGrade, GradeDescription, TotalPopulation, PopulationHigher, … } }
// Population:  GET https://api.psacard.com/publicapi/pop/GetPSASpecPopulation/{specID}
//              → { SpecID, Description, PSAPop: { Total, Auth, Grade1 … Grade10, Grade9Q … } }
// Auth header: Authorization: bearer <token>   (token from a free psacard.com account)
// Free limit:  historically 100 calls/day. Several sources report that PSA lowered free tokens
//              in 2026; if PSA answers 429 the server stops calling it until the next UTC day.
//
// Uncertain: an unknown cert may come back as 200 with an empty PSACert or with a
// "ServerMessage" field instead of 404; both are treated as "not found".

import { fetchJson, obj, str } from "../util";
import type { Population } from "../types";

const BASE = "https://api.psacard.com/publicapi";

export interface Cert {
  certNumber: string;
  description: string;
  grade: string | null;
  gradeDescription: string | null;
  year: string | null;
  set: string | null;
  cardNumber: string | null;
  subject: string | null;
  variety: string | null;
  specId: string | null;
  population: { total: number | null; higher: number | null };
  source: "psa";
}

const int = (v: unknown) => (typeof v === "number" ? v : typeof v === "string" && /^\d+$/.test(v) ? +v : null);

export function parsePsaCert(json: unknown): Cert | null {
  const c = obj(obj(json).PSACert);
  const cert = str(c.CertNumber);
  if (!cert) return null;
  const year = str(c.Year) ?? null;
  const set = str(c.Brand) ?? null;
  const subject = str(c.Subject) ?? null;
  const cardNumber = str(c.CardNumber) ?? null;
  const variety = str(c.Variety) ?? null;
  return {
    certNumber: cert,
    description: [year, set, subject, cardNumber ? `#${cardNumber}` : null, variety].filter(Boolean).join(" "),
    grade: str(c.CardGrade) ?? null,
    gradeDescription: str(c.GradeDescription) ?? null,
    year,
    set,
    cardNumber,
    subject,
    variety,
    specId: str(c.SpecID) ?? null,
    population: { total: int(c.TotalPopulation), higher: int(c.PopulationHigher) },
    source: "psa",
  };
}

export function parsePsaSpecPop(json: unknown): Population | null {
  const o = obj(json);
  const pop = obj(o.PSAPop);
  const total = int(pop.Total);
  if (total === null) return null;
  const byGrade: Record<string, number> = {};
  for (const [k, v] of Object.entries(pop)) {
    const m = /^Grade(\d+)(?:_(5))?(Q?)$/.exec(k);
    const n = int(v);
    if (!m || n === null || n === 0) continue;
    byGrade[`${m[1]}${m[2] ? ".5" : ""}${m[3] ? " (Q)" : ""}`] = n;
  }
  if (int(pop.Auth)) byGrade["Authentic"] = int(pop.Auth)!;
  return { total, higher: null, byGrade, description: str(o.Description), specId: str(o.SpecID), source: "psa" };
}

const headers = (token: string) => ({ Authorization: `bearer ${token}` });

export async function psaCert(token: string, cert: string): Promise<Cert | null> {
  return parsePsaCert(await fetchJson("psa", `${BASE}/cert/GetByCertNumber/${encodeURIComponent(cert)}`, { headers: headers(token) }));
}

export async function psaSpecPop(token: string, specId: string): Promise<Population | null> {
  return parsePsaSpecPop(await fetchJson("psa", `${BASE}/pop/GetPSASpecPopulation/${encodeURIComponent(specId)}`, { headers: headers(token) }));
}
