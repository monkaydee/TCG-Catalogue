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

CREATE TABLE IF NOT EXISTS learning_installs (id TEXT PRIMARY KEY, token_hash TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS recognition_reports (
 id TEXT PRIMARY KEY, install_hash TEXT NOT NULL, kind TEXT NOT NULL, context TEXT NOT NULL,
 card_id TEXT NOT NULL, game TEXT NOT NULL, language TEXT NOT NULL, payload TEXT NOT NULL,
 image_key TEXT, created_at INTEGER NOT NULL, confirmed INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS recognition_reports_install ON recognition_reports(install_hash,created_at);
CREATE INDEX IF NOT EXISTS recognition_reports_consensus ON recognition_reports(context,card_id);
CREATE TABLE IF NOT EXISTS recognition_rules (
 context TEXT PRIMARY KEY, card_id TEXT NOT NULL, game TEXT NOT NULL, language TEXT NOT NULL,
 version INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1
);

-- Keep the free-tier storage guard constant-time rather than scanning all reports per upload.
CREATE TABLE IF NOT EXISTS learning_totals (id INTEGER PRIMARY KEY CHECK(id=1), reports INTEGER NOT NULL DEFAULT 0);
INSERT OR IGNORE INTO learning_totals(id,reports) SELECT 1,COUNT(*) FROM recognition_reports;
CREATE TRIGGER IF NOT EXISTS learning_report_cap BEFORE INSERT ON recognition_reports
WHEN (SELECT reports FROM learning_totals WHERE id=1)>=20000
BEGIN SELECT RAISE(ABORT,'recognition storage limit'); END;
CREATE TRIGGER IF NOT EXISTS learning_report_added AFTER INSERT ON recognition_reports
BEGIN UPDATE learning_totals SET reports=reports+1 WHERE id=1; END;
CREATE TRIGGER IF NOT EXISTS learning_report_removed AFTER DELETE ON recognition_reports
BEGIN UPDATE learning_totals SET reports=reports-1 WHERE id=1; END;

-- Private, bounded bootstrap storage when the deployment account cannot yet enable R2.
-- 32 MiB is intentionally small; R2 remains preferred for a growing image corpus.
CREATE TABLE IF NOT EXISTS recognition_images (id TEXT PRIMARY KEY, data BLOB NOT NULL);
CREATE TABLE IF NOT EXISTS learning_image_totals (id INTEGER PRIMARY KEY CHECK(id=1), bytes INTEGER NOT NULL DEFAULT 0);
INSERT OR IGNORE INTO learning_image_totals(id,bytes) SELECT 1,COALESCE(SUM(LENGTH(data)),0) FROM recognition_images;
CREATE TRIGGER IF NOT EXISTS learning_image_cap BEFORE INSERT ON recognition_images
WHEN (SELECT bytes FROM learning_image_totals WHERE id=1)+LENGTH(NEW.data)>33554432
BEGIN SELECT RAISE(ABORT,'private image storage limit'); END;
CREATE TRIGGER IF NOT EXISTS learning_image_added AFTER INSERT ON recognition_images
BEGIN UPDATE learning_image_totals SET bytes=bytes+LENGTH(NEW.data) WHERE id=1; END;
CREATE TRIGGER IF NOT EXISTS learning_image_removed AFTER DELETE ON recognition_images
BEGIN UPDATE learning_image_totals SET bytes=bytes-LENGTH(OLD.data) WHERE id=1; END;
