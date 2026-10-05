-- Run privately in Cloudflare Dashboard > D1 > tcg-price-cache > Console.
-- No report or crop needs to be copied to GitHub. Inspect evidence before approving.
SELECT context, card_id, game, language, COUNT(DISTINCT install_hash) AS votes
FROM recognition_reports WHERE kind='recognition' AND confirmed=1
GROUP BY context,card_id HAVING votes>=3 ORDER BY votes DESC;

-- Substitute the chosen context/card ID in the private dashboard only.
SELECT id,card_id,payload,image_key,created_at FROM recognition_reports
WHERE context='REPLACE_WITH_REVIEWED_CONTEXT' ORDER BY created_at DESC;

-- Publication is conditional on three supporting installations, after operator review.
INSERT INTO recognition_rules(context,card_id,game,language,version,enabled)
SELECT context,card_id,game,language,unixepoch()*1000,1 FROM recognition_reports
WHERE context='REPLACE_WITH_REVIEWED_CONTEXT' AND card_id='REPLACE_WITH_REVIEWED_CARD_ID'
AND kind='recognition' AND confirmed=1
GROUP BY context,card_id,game,language HAVING COUNT(DISTINCT install_hash)>=3
ON CONFLICT(context) DO UPDATE SET card_id=excluded.card_id,game=excluded.game,
language=excluded.language,version=excluded.version,enabled=1;

-- Roll back an incorrectly reviewed rule.
UPDATE recognition_rules SET enabled=0 WHERE context='REPLACE_WITH_REVIEWED_CONTEXT';
