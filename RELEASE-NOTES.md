Fix Japanese Pokémon printing selection and price lookup.

- Selecting JP now offers native Japanese printings with their own set codes and collector numbers. Confirm the number printed on the card before saving; an English printing with only its language changed is no longer treated as a Japanese price identity.
- Fetch prices and images from the selected Japanese catalogue entry. Keep its exact TCGplayer product link when the catalogue supplies it, and separate Japanese condition data from English data and caches.
- Add English-name searches such as “JP Steelix” and retain searches by native printed codes and Japanese names.
- Explain how to repair older Japanese entries that still use English identities: open Edit, choose Japanese printing, then Save. Remove incompatible market prices during refresh while preserving purchase costs and manual values.
- Preserve the language read from scans. Quick-add sends unresolved Japanese printings to confirmation instead of saving an English identity.
- Prevent a background refresh from reverting a printing or language edited during lookup.
- Include the native Japanese set code in graded-price searches. Keep price sources and condition limitations visible.

CardNavo name, signing key and the 0.1.<build-number> release convention are preserved. Provider coverage varies; missing quotes are not replaced with English prices.
