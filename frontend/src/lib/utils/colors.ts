/**
 * Maps CRAP (mu + sigma) and mutation score to a color on a red-green spectrum.
 * 0 (red/risky) to 10 (green/healthy).
 */
export function getHealthColor(crapMu: number, mutationScore: number): string {
  // Low CRAP is good (< 5), high CRAP is risky (> 15)
  const crapHealth = Math.max(0, Math.min(10, 10 - (crapMu / 2)));
  // Mutation score 0..1 -> 0..10
  const mutationHealth = (mutationScore || 0) * 10;
  const composite = (crapHealth + mutationHealth) / 2;

  if (composite >= 7.5) return '#22c55e'; // Green
  if (composite >= 5.0) return '#eab308'; // Amber
  return '#ef4444'; // Red
}

export function formatCrap(mu: number, max: number, sigma: number): string {
  return `Crap μ ${(mu || 0).toFixed(1)}  max ${(max || 0).toFixed(1)}  σ ${(sigma || 0).toFixed(1)}`;
}

export function formatCoverage(cov: number): string {
  return `${Math.round((cov || 0) * 100)}%`;
}
