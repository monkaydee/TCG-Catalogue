# Private recognition learning — metadata only

Updated 8 October 2026. **Shared photo uploads are disabled in both app and server.**

Recognition sharing is separate and default-off. The client sends a normalized OCR hash,
selected card identifiers/language/printing and optional grade, not readable OCR/photos.
The server stores machine identifiers, the fingerprint, quote/grade evidence and an explicit
confirmation flag. Missing confirmation means unconfirmed. Neither automatic reports nor
price challenges automatically train a model or change shared prices.

Reports are private, pseudonymous and subject to GDPR assessment. Per-installation
100/day, global 20,000 reports and daily IP limits remain. Upload endpoints enforce body
limits while reading, including requests lacking Content-Length. Current server price
matching is revision 8; sealed matching is revision 9. JP is accepted and canonicalized to
JA internally; UI displays JP.

## Existing photos and consent withdrawal

A revision-2 policy migration removes legacy photos/free-OCR reports and their derived
rules. Private R2 is attached only to clean existing objects; it does not enable uploads.
If referenced R2 objects cannot be deleted, cleanup fails and retains references. The
migration runs before feedback/rule access and in scheduled cleanup; deployment smoke
checks verify completion and photo rejection. It does not claim to remove separately
operator-downloaded photos or infrastructure backups.

The app discards pending JPGs and requests remote deletion when upgrading a formerly
photo-enabled installation. Opt-out stops future transfers and clears pending local files;
remote deletion retries offline. New queued transfers wait for pending deletion. Reports
expire after 90 days at the next successful daily cleanup. Empty installation token records
are removed during cleanup. Delete before uninstalling to retain the token-based ability.

## Operator review

Optional secret FEEDBACK_ADMIN_KEY stays server-only. The private CLI/dashboard still
inspect metadata and approve/disable hints after three supporting installation IDs and
manual review. IDs are not proof of separate people. Crop downloads return disabled.
Raw text contexts in old moderation scripts are obsolete: use the published hash contexts.
Hints reorder candidates already matched locally, never invent IDs or loosen auto-add.

Re-enabling photos requires validated server-side content/card-identity moderation,
quarantine without public access, rights-cleared evidence, deletion/report/appeal processes,
processor arrangements and false-positive/false-negative evaluation. JPEG checks and a
MobileNet distance threshold alone do not satisfy that requirement. Keep the feature off
until those dependencies are real. See [UPLOAD-PRIVACY-REVIEW.md](UPLOAD-PRIVACY-REVIEW.md).
