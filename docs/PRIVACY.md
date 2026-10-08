# Privacy notice — CardNavo

_Last updated: 8 October 2026. Technical description for the current development release._

**Publisher action required before public launch:** supply the controller/operator name,
address and private privacy contact; verify processor contracts, processing locations,
international-transfer safeguards, incident handling and hosting backup retention. The
repository issue tracker is for non-sensitive support, not private access/deletion requests.
This file describes implemented controls; it is not a certification of GDPR/DSGVO compliance.

## Collection and local photos

Collection records, costs, wishlist, sales, settings, imported review photos and saved
pre-grade reports live in the app's private phone storage. Normal scanning and pre-grading
run locally. Background pictures are also local. No collection-wide upload or social
photo gallery is implemented.

Android automatic cloud backup/device transfer is disabled for app data. Explicit JSON
export/import and the cloud-backup file you select in Android's document picker remain
available. That chosen file can contain personal collection data; its storage, sharing and
retention depend on your selected storage provider. CardNavo does not receive that file.

## Optional recognition reports

Recognition sharing is **off by default** and can be changed in Settings / Collection tools.
When enabled, reports contain selected card identifiers, printed language, printing and
optional grading selection, a SHA-256 fingerprint of normalized OCR, and whether the
selection was explicitly confirmed. The readable OCR and original suggestion are removed
before the new app submits the report; the server also omits readable OCR, card-name/set
prose and suggestions from stored reports. Older clients' readable OCR may be received
transiently and fingerprinted, but is not retained by the updated server.

A random installation ID and a separate random deletion token bind reports to one app
installation. The server stores salted installation hashes and token hashes. These and
OCR fingerprints are **pseudonymous, not guaranteed anonymous**, and must be treated as
potential personal data. Reports are private and require operator review before they can
become hints. Three installation IDs are a review threshold, not verified distinct people.
Hints only reorder existing candidates; they do not create a match or enable auto-add.

**Shared photo uploads are disabled.** The app no longer generates/transmits learning crops
and the server rejects image/approval fields before writing a report or image. Shape,
resolution, EXIF stripping and visual similarity do not establish content safety. Photo
sharing must remain disabled until independent card-identity/content checks, a private
review/deletion process and the processor arrangements are validated.

The policy migration removes old shared photos and free-OCR reports, and rules derived
from those reports. The deployment check exercises this migration. If old R2 objects exist
but their private bucket is unavailable, migration fails visibly and retains deletion
references rather than claiming success. Private operator downloads and provider/hosting
backups require separate operator cleanup and retention verification.

## Incorrect-price reports

Tapping “Report incorrect price” sends that selected card's identifiers and saved quote
amount/currency/source/grade/time to the Worker. It is a separate explicit report action;
it does not upload your collection, costs, photo or readable OCR. Such reports do not
change shared prices automatically.

## Withdrawal and retention

“Delete shared reports” deletes reports and images associated with the installation and
invalidates dependent hints when evidence falls below the review threshold. Disabling
recognition sharing stops future queued transfers, clears local queued reports and requests
server deletion. Failed/offline deletion is marked pending and retried by background work;
queued transfers wait for pending withdrawal to finish. Previously enabled photo sharing
also schedules deletion during the upgrade.

Reports expire after 90 days through the daily cleanup; actual removal may occur on the
next successful scheduled run. Empty installation records are removed by cleanup. Local
queue expiry is checked during a send attempt. Removing the app may remove the deletion
token; request deletion before uninstalling when possible. The controller must provide a
private assistance route and explain infrastructure backup expiry.

## Optional Identify online

Only when you explicitly select **Identify online**, one JPEG is sent to the Worker and
forwarded to Ximilar for recognition. The Worker code does not persist or log the photo;
request bodies are size-bounded while reading. Ximilar's processing/retention and
Cloudflare infrastructure logging are governed by their contracts and policies, which the
publisher must verify. This is separate from disabled shared-learning photo storage and
is not a validated adult/illegal-content classifier.

## Network lookups

Direct lookups can send card number/set/name and network metadata (including IP address)
to TCGdex, OPTCG API, Scryfall, TCGplayer/CDNs, GitHub daily catalogue hosting and
Frankfurter exchange rates. Server price/cert requests send card identity or certification
number to a Cloudflare Worker and its configured providers (JustTCG, TCG API, PokeTrace,
PSA, RapidAPI, PokemonPriceTracker, eBay). The Worker keeps shared quote caches, provider
usage counters and a daily salted IP hash for rate limits. It does not need your account,
location, collection-wide holdings or purchase costs.

Opening marketplace links opens your browser; those sites' policies apply. No first-party
analytics or advertising feature is implemented. A voluntarily shared crash report includes
technical version/device/stack information; review it before sending.

## Your rights and public-launch readiness

The intended basis for optional learning contributions is consent; it must be freely given,
specific, informed and withdrawable. The controller must document the appropriate lawful
basis and notice for each other processing purpose, including service security. Applicable
GDPR rights include access, correction, deletion, restriction, portability and complaint to
a supervisory authority. Identify the actual controller/contact/authority before public
launch; a public GitHub issue is not an adequate private rights channel.

Children's consent/age rules, processor agreements, international transfers, deletion from
backups, least-privilege operator access and breach procedures remain operator review
items. Do not describe the app as collecting no personal data merely because it has no
account. See [the upload/privacy review](UPLOAD-PRIVACY-REVIEW.md).
