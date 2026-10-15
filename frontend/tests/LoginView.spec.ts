import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { login as loginRequest } from '@/features/auth/api/authApi'
import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { AUTH_STORAGE_KEY } from '@/features/auth/storage/authStorage'
import { IdentityStatus } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'
import type { LoginResponse } from '@/types/auth'
import type { CurrentUserResponse } from '@/types/user'
import { authenticate, mountApp } from './helpers/mountApp'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { emptyHistoryPage } from './helpers/interviewFixtures'

vi.mock('@/features/auth/api/authApi', () => ({
  login: vi.fn(),
  registerAccount: vi.fn(),
}))

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

vi.mock('@/features/interview/api/interviewApi', () => ({
  fetchCurrentInterview: vi.fn(),
  createInterview: vi.fn(),
  createOrResumeInterview: vi.fn(),
  submitInterviewAnswer: vi.fn(),
  fetchInterviewDetail: vi.fn(),
  fetchInterviewReport: vi.fn(),
  generateInterviewReport: vi.fn(),
}))

vi.mock('@/features/history/api/historyApi', () => ({
  fetchInterviewHistory: vi.fn(),
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

describe('LoginView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(loginRequest).mockReset()
    vi.mocked(fetchCurrentUser).mockReset()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
  })

  it('submits a valid form to login', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    const { wrapper, router } = await mountApp('/login')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => {
      expect(loginRequest).toHaveBeenCalledWith({ account: 'alice', password: 'password1' })
      expect(router.currentRoute.value.name).toBe('dashboard')
    })
  })

  it('keeps fields and shows the backend message on login 400', async () => {
    vi.mocked(loginRequest).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '账号或密码错误',
        kind: ErrorKind.VALIDATION,
      }),
    )
    const { wrapper, auth } = await mountApp('/login')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('账号或密码错误')
    expect((wrapper.get('#login-account').element as HTMLInputElement).value).toBe('alice')
    expect((wrapper.get('#login-password').element as HTMLInputElement).value).toBe('password1')
    expect(auth.accessToken).toBeNull()
    expect(auth.identityStatus).toBe(IdentityStatus.ANONYMOUS)
    expect(wrapper.text()).not.toContain('登录请求暂时失败')
  })

  it('redirects to a safe internal path after successful login', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    const { wrapper, router } = await mountApp('/login?redirect=/profile')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.fullPath).toBe('/profile')
    })
  })

  it('ignores an unsafe redirect after successful login', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    const { wrapper, router } = await mountApp('/login?redirect=https://evil.com')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.fullPath).toBe('/')
    })
  })

  it('does not render a credential failure after login 200 and /me transient failure', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError({
        httpStatus: 0,
        code: 0,
        message: '网络异常，请稍后重试',
        kind: ErrorKind.NETWORK,
      }),
    )
    const { wrapper, router, auth } = await mountApp('/login')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => {
      expect(auth.accessToken).toBe('access-token')
      expect(auth.identityStatus).toBe(IdentityStatus.UNAVAILABLE)
      expect(router.currentRoute.value.name).toBe('dashboard')
    })
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? '{}').accessToken).toBe('access-token')
    expect(wrapper.text()).not.toContain('账号或密码错误')
    expect(wrapper.text()).not.toContain('登录失败')
    expect(wrapper.text()).toContain('用户信息暂时无法加载')
  })

  it('prefills account from the query after register', async () => {
    const { wrapper } = await mountApp('/login?account=alice')
    expect((wrapper.get('#login-account').element as HTMLInputElement).value).toBe('alice')
  })

  it('shows a retryable message on network failure without clearing the account', async () => {
    vi.mocked(loginRequest).mockRejectedValue(
      new ApiError({
        httpStatus: 0,
        code: 0,
        message: '网络异常，请稍后重试',
        kind: ErrorKind.NETWORK,
      }),
    )
    const { wrapper } = await mountApp('/login')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('登录请求暂时失败')
    expect((wrapper.get('#login-account').element as HTMLInputElement).value).toBe('alice')
  })

  it('uses honest auth copy when login 200 is followed by /me 401', async () => {
    vi.mocked(loginRequest).mockResolvedValue(loginResponse)
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError({
        httpStatus: 401,
        code: 401,
        message: '未认证或访问令牌无效',
        kind: ErrorKind.UNAUTHENTICATED,
      }),
    )
    const { wrapper } = await mountApp('/login')
    await wrapper.get('#login-account').setValue('alice')
    await wrapper.get('#login-password').setValue('password1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('登录状态无效，请重新登录')
    expect(wrapper.text()).not.toContain('账号或密码错误')
    expect(wrapper.text()).not.toContain('登录请求暂时失败')
  })

  it('sends an already authenticated visitor to a safe redirect instead of looping on login', async () => {
    const { router } = await mountApp('/login?redirect=/login', (auth) => {
      authenticate(auth)
    })
    expect(router.currentRoute.value.fullPath).toBe('/')
  })
})
