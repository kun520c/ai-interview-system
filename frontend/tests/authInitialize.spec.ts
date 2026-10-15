import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/app/stores/auth'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { writeAuthCredential } from '@/features/auth/storage/authStorage'
import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { ApiError, ErrorKind } from '@/api/apiError'
import { IdentityStatus } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'
import type { CurrentUserResponse } from '@/types/user'
import { deferred } from './helpers/deferred'

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

const me: CurrentUserResponse = {
  userId: 9,
  account: 'admin',
  username: 'admin',
  email: 'admin@example.com',
  role: UserRole.ADMIN,
  status: UserStatus.ENABLED,
  createdAt: '2026-01-01T00:00:00',
}

describe('auth initialize single-flight', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(fetchCurrentUser).mockReset()
    writeAuthCredential({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresAt: Date.now() + 3_600_000,
      userId: 9,
    })
  })

  it('runs fetchCurrentUser once for two concurrent initialize callers', async () => {
    const pending = deferred<CurrentUserResponse>()
    vi.mocked(fetchCurrentUser).mockReturnValue(pending.promise)

    const auth = useAuthStore()
    const first = auth.initialize()
    const second = auth.initialize()
    await Promise.resolve()
    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)

    pending.resolve(me)
    await Promise.all([first, second])

    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)
    expect(auth.me).toEqual(me)
    expect(auth.identityStatus).toBe(IdentityStatus.RESOLVED)
    expect(auth.ready).toBe(true)
    expect(auth.isAdmin).toBe(true)
  })

  it('returns immediately after ready without a second /me', async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    const auth = useAuthStore()
    await auth.initialize()
    await auth.initialize()
    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('keeps the token and marks identity unavailable on /me network failure', async () => {
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError({
        httpStatus: 0,
        code: 0,
        message: '网络异常，请稍后重试',
        kind: ErrorKind.NETWORK,
      }),
    )
    const auth = useAuthStore()
    await auth.initialize()
    expect(auth.accessToken).toBe('token')
    expect(auth.me).toBeNull()
    expect(auth.identityStatus).toBe(IdentityStatus.UNAVAILABLE)
    expect(auth.isAdmin).toBe(false)
  })
})
