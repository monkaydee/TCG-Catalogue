// Shared result cache in D1. One row per key; rows outlive their expiry so they can still be
// served as "stale" when every provider is out of budget. A daily cron deletes very old rows.

export interface CacheEntry<T> {
  value: T;
  source: string | null;
  fetchedAt: number; // ms since epoch
  expiresAt: number; // ms since epoch
}

// D1 allows at most 100 bound parameters per query.
const MAX_PARAMS = 90;

export class Cache {
  constructor(private db: D1Database) {}

  async getMany<T>(keys: string[]): Promise<Map<string, CacheEntry<T>>> {
    const out = new Map<string, CacheEntry<T>>();
    const unique = [...new Set(keys)];
    for (let i = 0; i < unique.length; i += MAX_PARAMS) {
      const part = unique.slice(i, i + MAX_PARAMS);
      const { results } = await this.db
        .prepare(`SELECT key, value, source, fetched_at, expires_at FROM cache WHERE key IN (${part.map(() => "?").join(",")})`)
        .bind(...part)
        .all<{ key: string; value: string; source: string | null; fetched_at: number; expires_at: number }>();
      for (const r of results ?? []) {
        try {
          out.set(r.key, { value: JSON.parse(r.value) as T, source: r.source, fetchedAt: r.fetched_at, expiresAt: r.expires_at });
        } catch {
          // A broken row is treated as a cache miss.
        }
      }
    }
    return out;
  }

  async get<T>(key: string): Promise<CacheEntry<T> | undefined> {
    return (await this.getMany<T>([key])).get(key);
  }

  /** Writes all entries in one D1 batch (one round trip). */
  async putMany(entries: { key: string; value: unknown; source: string | null; fetchedAt: number; ttlMs: number }[]): Promise<void> {
    if (entries.length === 0) return;
    const stmt = this.db.prepare(
      "INSERT OR REPLACE INTO cache (key, value, source, fetched_at, expires_at) VALUES (?1, ?2, ?3, ?4, ?5)",
    );
    await this.db.batch(entries.map((e) => stmt.bind(e.key, JSON.stringify(e.value), e.source, e.fetchedAt, e.fetchedAt + e.ttlMs)));
  }

  async count(): Promise<{ rows: number; fresh: number }> {
    const r = await this.db
      .prepare("SELECT COUNT(*) AS rows, SUM(CASE WHEN expires_at > ?1 THEN 1 ELSE 0 END) AS fresh FROM cache")
      .bind(Date.now())
      .first<{ rows: number; fresh: number | null }>();
    return { rows: r?.rows ?? 0, fresh: r?.fresh ?? 0 };
  }
}
