import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import { login as loginRequest } from '@/features/auth/api/authApi'
import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { AUTH_STORAGE_KEY } from '@/features/auth/storage/authStorage'
import { IdentityStatus, LoginOutcomeKind } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'
import type { LoginResponse } from '@/types/auth'
import type { CurrentUserResponse } from '@/types/user'

vi.mock('@/features/auth/api/authApi', () => ({
  login: vi.fn(),
  registerAccount: vi.fn(),
}))

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

const loginResponse: LoginResponse = {
  userId: 3,
  account: 'alice',
  username: '爱丽丝',
  role: UserRole.USER,
  accessToken: 'access-token',
  tokenType: 'Bearer',
  expiresInSeconds: 7200,
}

const me: CurrentUserResponse = {
  userId: 3,
  account: 'alice',
  username: '爱丽丝',
  email: 'alice@example.com',
  role: UserRole.USER,
  status: UserStatus.ENABLED,
  createdAt: '2026-01-01T00:00:00',
}

describe('auth login outcome', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(loginRequest).mockReset()
    vi.mocked(fetchCurrentUser).mockReset()
  })

  it('resolves identity after login and /me success', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    const auth = useAuthStore()
    const outcome = await auth.login({ account: 'alice', password: 'password1' })
    expect(outcome.kind).toBe(LoginOutcomeKind.AUTHENTICATED)
    expect(outcome.credentialAccepted).toBe(true)
    expect(auth.accessToken).toBe('access-token')
    expect(auth.identityStatus).toBe(IdentityStatus.RESOLVED)
    expect(auth.me?.username).toBe('爱丽丝')
  })

  it('keeps the credential when /me fails transiently after login 200', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError({
        httpStatus: 0,
        code: 0,
        message: '网络异常，请稍后重试',
        kind: ErrorKind.NETWORK,
      }),
    )
    const auth = useAuthStore()
    const outcome = await auth.login({ account: 'alice', password: 'password1' })
    expect(outcome.kind).toBe(LoginOutcomeKind.AUTHENTICATED_IDENTITY_UNAVAILABLE)
    expect(outcome.credentialAccepted).toBe(true)
    expect(auth.accessToken).toBe('access-token')
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.identityStatus).toBe(IdentityStatus.UNAVAILABLE)
    expect(auth.me).toBeNull()
    expect(auth.isAdmin).toBe(false)
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? '{}').accessToken).toBe('access-token')
  })

  it('does not treat login 400 as an authenticated session', async () => {
    vi.mocked(loginRequest).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '账号或密码错误',
        kind: ErrorKind.VALIDATION,
      }),
    )
    const auth = useAuthStore()
    await expect(auth.login({ account: 'alice', password: 'wrongpass' })).rejects.toMatchObject({
      message: '账号或密码错误',
    })
    expect(auth.accessToken).toBeNull()
    expect(auth.identityStatus).toBe(IdentityStatus.ANONYMOUS)
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })
})
