import { describe, expect, it } from 'vitest'

import {
  AUTH_STORAGE_KEY,
  clearAuthCredential,
  readAuthCredential,
  writeAuthCredential,
} from '@/features/auth/storage/authStorage'
import { createMemoryStorage } from './helpers/memoryStorage'

const credential = {
  accessToken: 'token-1',
  tokenType: 'Bearer',
  expiresAt: 1_800_000_000_000,
  userId: 12,
}

describe('auth storage', () => {
  it('round-trips credential fields and never stores a password', () => {
    const storage = createMemoryStorage()
    writeAuthCredential(credential, storage)
    const raw = storage.getItem(AUTH_STORAGE_KEY)
    expect(raw).not.toContain('password')
    expect(readAuthCredential(storage)).toEqual(credential)
  })

  it('drops invalid stored JSON', () => {
    const storage = createMemoryStorage({
      [AUTH_STORAGE_KEY]: '{not-json',
    })
    expect(readAuthCredential(storage)).toBeNull()
    expect(storage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('clears only the auth key', () => {
    const storage = createMemoryStorage()
    writeAuthCredential(credential, storage)
    storage.setItem('interview.submit.12.1.1', '{"keep":true}')
    clearAuthCredential(storage)
    expect(readAuthCredential(storage)).toBeNull()
    expect(storage.getItem('interview.submit.12.1.1')).toBe('{"keep":true}')
  })
})
