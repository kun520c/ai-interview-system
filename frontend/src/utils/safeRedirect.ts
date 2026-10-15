const FALLBACK_PATH = '/'

function isSafeInternalPath(path: string): boolean {
  if (!path.startsWith('/')) {
    return false
  }
  if (path.startsWith('//')) {
    return false
  }
  if (path.includes('\\')) {
    return false
  }
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(path)) {
    return false
  }
  if (path === '/login' || path.startsWith('/login?') || path.startsWith('/login/')) {
    return false
  }
  if (path === '/register' || path.startsWith('/register?') || path.startsWith('/register/')) {
    return false
  }
  return true
}

export function resolveSafeRedirect(raw: unknown, fallback = FALLBACK_PATH): string {
  if (typeof raw !== 'string' || raw.trim().length === 0) {
    return fallback
  }

  let candidate = raw.trim()
  try {
    candidate = decodeURIComponent(candidate)
  } catch {
    return fallback
  }

  if (!isSafeInternalPath(candidate)) {
    return fallback
  }
  return candidate
}
