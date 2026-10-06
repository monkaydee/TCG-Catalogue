/** Conservative asking-price summary. Counts are observations, never probabilities. */
export function comparableSummary(values: number[], minimum: number) {
  const sorted = values.filter(v => Number.isFinite(v) && v > 0).sort((a,b) => a-b);
  if (sorted.length < minimum) return null;
  const median = (a: number[]) => a.length % 2 ? a[Math.floor(a.length/2)] : (a[a.length/2-1]+a[a.length/2])/2;
  const center = median(sorted);
  // Exclude extreme asking prices; still expose a broad remaining spread as limited evidence.
  const kept = sorted.filter(v => v >= center/4 && v <= center*4);
  if (kept.length < minimum) return null;
  return {amount:Math.round(median(kept)*100)/100,listings:kept.length,low:kept[0],high:kept[kept.length-1],
    excluded:sorted.length-kept.length,evidence:kept.length >= 5 && kept[kept.length-1]/kept[0] <= 2 ? "consistent" : "limited"};
}
export const normalize = (s: string) => s.normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
export function phraseIn(title: string, phrase: string): boolean {
  const words=normalize(phrase).split(/[^\p{L}0-9]+/u).filter(Boolean);
  if (!words.length) return false;
  const escaped=words.map(w=>w.replace(/[.*+?^${}()|[\]\\]/g,"\\$&")).join("[^\\p{L}0-9]+");
  return new RegExp(`(^|[^\\p{L}0-9])${escaped}([^\\p{L}0-9]|$)`,"u").test(normalize(title));
}
