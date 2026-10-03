-- D1 tables for the price server. Safe to run again: nothing is dropped.

-- Shared results (prices, PSA certs, population). Rows stay after expiry so they can be
-- served as "stale" when providers are out of budget; the daily cron removes very old rows.
CREATE TABLE IF NOT EXISTS cache (
  key        TEXT PRIMARY KEY,   -- e.g. "raw|pokemon:tcg123456:holofoil", "cert|12345678"
  value      TEXT NOT NULL,      -- JSON
  source     TEXT,               -- provider that answered
  fetched_at INTEGER NOT NULL,   -- ms since epoch
  expires_at INTEGER NOT NULL    -- ms since epoch
);
CREATE INDEX IF NOT EXISTS cache_expires ON cache (expires_at);

-- Provider calls per UTC day. Monthly totals are the sum of the month's days.
CREATE TABLE IF NOT EXISTS usage (
  provider TEXT NOT NULL,
  day      TEXT NOT NULL,              -- "2026-10-03"
  used     INTEGER NOT NULL DEFAULT 0,
  blocked  INTEGER NOT NULL DEFAULT 0, -- 1 = provider said its quota is used up today
  PRIMARY KEY (provider, day)
);

-- Requests per caller per day, for the fairness limit. The IP is stored only as a salted hash
-- that changes every day.
CREATE TABLE IF NOT EXISTS ip_hits (
  ip_hash TEXT NOT NULL,
  day     TEXT NOT NULL,
  hits    INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (ip_hash, day)
);
