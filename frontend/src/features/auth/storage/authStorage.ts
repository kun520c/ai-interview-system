import type { AuthCredential } from '@/types/auth'

export const AUTH_STORAGE_KEY = 'interview.auth.credential'

export class AuthStorageError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AuthStorageError'
  }
}

function getStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function readAuthCredential(storage: Storage | null = getStorage()): AuthCredential | null {
  if (!storage) {
    return null
  }
  const raw = storage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<AuthCredential>
    if (
      typeof parsed.accessToken !== 'string' ||
      parsed.accessToken.length === 0 ||
      typeof parsed.tokenType !== 'string' ||
      typeof parsed.expiresAt !== 'number' ||
      typeof parsed.userId !== 'number'
    ) {
      storage.removeItem(AUTH_STORAGE_KEY)
      return null
    }
    return {
      accessToken: parsed.accessToken,
      tokenType: parsed.tokenType,
      expiresAt: parsed.expiresAt,
      userId: parsed.userId,
    }
  } catch {
    storage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export function writeAuthCredential(
  credential: AuthCredential,
  storage: Storage | null = getStorage(),
): void {
  if (!storage) {
    throw new AuthStorageError('无法写入登录凭证')
  }
  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(credential))
}

export function clearAuthCredential(storage: Storage | null = getStorage()): void {
  storage?.removeItem(AUTH_STORAGE_KEY)
}
