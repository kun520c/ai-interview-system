export function formatScore(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) {
    return ''
  }
  return numeric.toFixed(2).replace(/\.?0+$/, '')
}
