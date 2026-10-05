# Private recognition learning

The app includes separate, default-off text and photo switches in Settings and Collection tools.
Each confirmed addition creates a report only after text consent. Camera/photo confirmations
attach OCR and the initial suggestion. Manual additions report the selected name/number.
Photo reporting detects and rectifies a card region, re-encodes a JPEG without EXIF, and requires
an individual preview and approval. There is no full-photo fallback. Rejecting a crop sends text only.
Pending originals for import review are private local files, never uploaded by this feature.
Acquisition costs, account email, location and collection backups are never included.

The Worker authenticates the app and separately binds a random installation ID to a random
installation token. Neither is an account identity. Reports have a 100/day installation limit and
an overall 20,000 report storage ceiling, in addition to the existing IP limit. Crops are limited to
300 KB and 800 pixels, card-shaped JPEGs; EXIF segments are rejected. Crops use a private R2
bucket without public access. When R2 is unavailable, a private D1 image table has a strict 32 MiB total cap; reaching it retains uploads on the phone for retry. The daily retention job removes reports/photos after 90 days.
Deletion and opt-out delete that installation's reports and images; an offline deletion retries.
Withdrawal also disables rules that no longer have supporting evidence. Installation token hashes are also removed on explicit deletion.

## Deployment

The existing GitHub Worker workflow creates/attaches `cardnavo-recognition-private`. If R2 is
not enabled or the deployment token lacks Workers R2 Storage Edit, it deploys text reporting and
reports the R2 setup failure and uses bounded private D1 storage. Add Workers R2 Storage Edit and enable R2 to increase image capacity.

Optionally add repository secret `FEEDBACK_ADMIN_KEY` (a random secret distinct from APP_KEY) for HTTP/CLI moderation.
It is uploaded to the Worker only, never built into Android. Without it HTTP moderation is disabled. The account owner can still inspect reports and publish reviewed rules directly in the existing Cloudflare D1 dashboard; see `scripts/recognition/moderate.sql`. Existing provider secrets are unchanged.

## Moderation and rollback

`scripts/recognition/moderate.py` uses `LEARNING_SERVER_URL`, `APP_KEY` and `FEEDBACK_ADMIN_KEY`
from environment variables. List consensus candidates, inspect their reports and privately
download crops before approving. Three installation IDs constitute a review threshold, **not**
proof of three different people. A malicious client can create IDs; human review is essential.
The same tool disables an incorrect rule. Never commit downloaded private crops.

Apps fetch approved rules daily over authenticated HTTPS, verify the payload SHA-256, and
stop using caches more than seven days old. This is origin authentication and corruption checking,
not an independently signed model manifest. Rules can reorder cards already matched locally;
they cannot invent a card ID, override printed-name checks or enable automatic additions.

Confirmed crops are available to the operator for a held-out recognition evaluation set.
A release does not automatically train or publish new neural weights. Monthly model training
requires rights-cleared, curated images, split by physical card/installation and a regression
threshold; reported card IDs are recognition labels, not known grading labels.
