# Upload deviations, unsafe content and DSGVO review

8 October 2026. User request: reject strong deviations so arbitrary/wrong photos and adult
or prohibited content cannot enter the shared database, and assess DSGVO readiness.

## Findings and changes

| Finding | Current control | Boundary |
| --- | --- | --- |
| Any client could submit a card-shaped JPEG after setting photoApproved | App/server photo sharing disabled; server rejects image or approval fields before persistence | Not an NSFW or prohibited-content detector; photos stay disabled |
| EXIF/size/aspect-ratio checks mistaken for content assurance | Documented as format checks only | A forged rectangle can contain anything |
| Readable OCR could include background names/details | New client fingerprints OCR; Worker omits raw OCR/name/set prose/suggestions from stored payloads | Fingerprints/install hashes can still be personal data |
| Legacy uploaded crops/readable text | Policy migration deletes referenced R2 objects, D1 images/old reports and dependent rules | Missing R2 binding stops cleanup visibly; backups/operator downloads need operator handling |
| Missing humanConfirmed defaulted to a vote | Default is now unconfirmed; explicit confirmed selections are still reviewed | Pseudonymous installs can be fabricated; no Sybil-proof identity claimed |
| Size limits depended on Content-Length for several endpoints | Streaming limits for prices, sealed, identify and moderation; feedback already bounded | App key in an APK is not a secret user identity |
| Installation-token rows outlived report retention | Scheduled cleanup removes orphan rows; deleting an unknown installation does not register one | Token lost on uninstall can prevent self-service deletion |
| Privacy notice omitted learning and implied zero personal data | Updated purpose/consent/pseudonymisation/retention/withdrawal/provider/backup description | Actual controller details/contracts are still required |
| Android implicit backup contradicted phone-only description | Automatic cloud backup/device transfer excluded; explicit chosen-file export/cloud backup preserved | Cannot revoke old OS/provider backups already made |
| Extreme asking prices could split samples into unrelated bands | Majority-cluster filter; tied disconnected clusters return no quote; counts/exclusions disclosed | Statistical agreement does not prove listing image authenticity or a completed sale |

## What to require before shared photos are restored

1. Server-side validation independent of client claims: decode/re-encode safely, strict file,
   pixel, orientation and resource bounds; strip metadata; reject malformed/polyglot input.
2. Match against trusted exact-product/printing references. Evaluate embedding-distance and
   printed-evidence thresholds on held-out genuine cards and non-card negatives. Abstain for
   large deviations. Card art can be unusual; never invent a universal similarity threshold.
3. A vetted content-moderation service/model for adult and prohibited content. Use suitable
   specialist processes for potentially illegal material; do not create an improvised illegal
   image corpus or ask staff to browse suspect uploads. Similarity alone is insufficient.
4. Fail closed before durable storage/publication if safety/identity service is unavailable.
   If a private quarantine is required, define lawful processing, encryption/access, short
   retention and operator handling first. Never make an incoming image a catalogue image.
5. Explicit per-photo informed consent and preview; private rights/report/deletion channel;
   no automatic public release or training. Curate rights-cleared training/evaluation data.
6. Tested rejection paths, rate/cost quotas, retention/deletion audits and false-positive/
   false-negative metrics. Any approved moderation vendor needs real access and reviewed terms.

Local imports remain on the user's phone. Optional Identify online is an explicit external
processor request, not shared database upload; the Worker does not persist its JPEG. Its
processor contract/content/retention controls still require review before public launch.

## DSGVO status

Technical improvements are implemented; **full compliance is not established**. The owner
must supply controller identity/address/privacy contact, document lawful bases, verify
Cloudflare/Ximilar and other processor/transfer arrangements, define backup retention and
rights handling, review children/age-consent rules, and run least-privilege/incident procedures.
Ask for professional review where the deployment requires it. Do not advertise certification
based on hashed IDs, a privacy markdown file or a disabled upload button.

Primary reference: [EDPB security guide](https://www.edpb.europa.eu/sme/be-compliant/secure-personal-data_en)
explains pseudonymisation remains personal-data processing and includes organisational as
well as technical controls. Sources were read on 8 October 2026.
