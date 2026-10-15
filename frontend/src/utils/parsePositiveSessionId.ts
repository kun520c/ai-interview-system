export function parsePositiveSessionId(raw: unknown): number | null {
  if (typeof raw === 'number') {
    if (!Number.isSafeInteger(raw) || raw <= 0) {
      return null
    }
    return raw
  }

  if (typeof raw !== 'string') {
    return null
  }

  if (!/^[1-9]\d*$/.test(raw)) {
    return null
  }

  const parsed = Number(raw)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    return null
  }

  return parsed
}
