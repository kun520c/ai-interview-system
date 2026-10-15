import { createPinia, setActivePinia } from 'pinia'
import { createApp } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { notifyUnauthorized, resetUnauthorizedGate } from '@/api/unauthorized'
import { createAppRouter } from '@/app/router'
import { bindUnauthorizedNavigation } from '@/app/router/unauthorizedNavigation'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import { writeAuthCredential } from '@/features/auth/storage/authStorage'
import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { ApiError, ErrorKind } from '@/api/apiError'
import { IdentityStatus } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'
import type { CurrentUserResponse } from '@/types/user'

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

function authenticate(role: (typeof UserRole)[keyof typeof UserRole]): void {
  const auth = useAuthStore()
  auth.ready = true
  auth.accessToken = 'token'
  auth.tokenType = 'Bearer'
  auth.expiresAt = Date.now() + 3_600_000
  auth.userId = 3
  auth.identityStatus = IdentityStatus.RESOLVED
  auth.me = {
    userId: 3,
    account: 'demo',
    username: 'demo',
    email: 'demo@example.com',
    role,
    status: UserStatus.ENABLED,
    createdAt: '2026-01-01T00:00:00',
  } satisfies CurrentUserResponse
}

function setupRouter() {
  const app = createApp({ template: '<div />' })
  const pinia = createPinia()
  const router = createAppRouter(createMemoryHistory())
  app.use(pinia)
  app.use(router)
  setActivePinia(pinia)
  bindUnauthorizedNavigation(router)
  const auth = useAuthStore()
  auth.ready = true
  return { router, auth }
}

describe('route guards', () => {
  beforeEach(() => {
    resetUnauthorizedGate()
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentUser).mockReset()
  })

  it('sends anonymous users from protected routes to login', async () => {
    const { router } = setupRouter()
    await router.push('/profile')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/profile')
  })

  it('sends confirmed USER away from admin routes without logging out', async () => {
    const { router, auth } = setupRouter()
    authenticate(UserRole.USER)
    await router.push('/admin/questions')
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(auth.accessToken).toBe('token')
  })

  it('keeps confirmed ADMIN on admin routes', async () => {
    const { router } = setupRouter()
    authenticate(UserRole.ADMIN)
    await router.push('/admin/questions')
    expect(router.currentRoute.value.name).toBe('admin-questions')
  })

  it('does not map unresolved identity to forbidden', async () => {
    const { router, auth } = setupRouter()
    auth.accessToken = 'token'
    auth.userId = 9
    auth.me = null
    auth.identityStatus = IdentityStatus.UNAVAILABLE
    await router.push('/admin/questions')
    expect(router.currentRoute.value.name).toBe('admin-questions')
    expect(auth.accessToken).toBe('token')
    expect(router.currentRoute.value.name).not.toBe('forbidden')
  })

  it('does not fake-403 when stored token /me fails with a network error', async () => {
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError({
        httpStatus: 0,
        code: 0,
        message: '网络异常，请稍后重试',
        kind: ErrorKind.NETWORK,
      }),
    )
    writeAuthCredential({
      accessToken: 'admin-shaped-token',
      tokenType: 'Bearer',
      expiresAt: Date.now() + 3_600_000,
      userId: 9,
    })
    const { router, auth } = setupRouter()
    auth.ready = false
    await router.push('/admin/questions')
    expect(auth.identityStatus).toBe(IdentityStatus.UNAVAILABLE)
    expect(auth.me).toBeNull()
    expect(auth.isAdmin).toBe(false)
    expect(router.currentRoute.value.name).toBe('admin-questions')
    expect(router.currentRoute.value.name).not.toBe('forbidden')
  })

  it('redirects authenticated users away from login', async () => {
    const { router } = setupRouter()
    authenticate(UserRole.USER)
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('uses a safe redirect when an authenticated user opens login', async () => {
    const { router } = setupRouter()
    authenticate(UserRole.USER)
    await router.push('/login?redirect=/profile')
    expect(router.currentRoute.value.fullPath).toBe('/profile')
  })

  it('does not loop when login redirect points at login', async () => {
    const { router } = setupRouter()
    authenticate(UserRole.USER)
    await router.push('/login?redirect=/login')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('redirects 401 to login once and keeps a safe return path', async () => {
    const { router, auth } = setupRouter()
    authenticate(UserRole.USER)
    await router.push('/profile')
    expect(router.currentRoute.value.name).toBe('profile')
    notifyUnauthorized()
    notifyUnauthorized()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('login')
    })
    expect(router.currentRoute.value.query.redirect).toBe('/profile')
    expect(auth.accessToken).toBeNull()
  })

  it('ignores open-redirect payloads after authentication', async () => {
    const { router } = setupRouter()
    authenticate(UserRole.USER)
    await router.push('/login?redirect=https://evil.com')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('sets a static product document title after navigation', async () => {
    const { router } = setupRouter()
    await router.push('/login')
    expect(document.title).toBe('面试训练 · 登录')
    authenticate(UserRole.USER)
    await router.push('/')
    expect(document.title).toBe('面试训练 · 工作台')
    await router.push('/interview')
    expect(document.title).toBe('面试训练 · 面试进行中')
    await router.push('/interviews')
    expect(document.title).toBe('面试训练 · 历史')
    await router.push('/profile')
    expect(document.title).toBe('面试训练 · 账号')
  })
})
