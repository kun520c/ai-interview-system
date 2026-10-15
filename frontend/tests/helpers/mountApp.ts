import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'

import App from '@/App.vue'
import { createAppRouter } from '@/app/router'
import { useAuthStore } from '@/app/stores/auth'
import { IdentityStatus } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'
import type { CurrentUserResponse } from '@/types/user'
import type { Router } from 'vue-router'

export function makeUser(role: (typeof UserRole)[keyof typeof UserRole] = UserRole.USER): CurrentUserResponse {
  return {
    userId: role === UserRole.ADMIN ? 9 : 3,
    account: role === UserRole.ADMIN ? 'admin' : 'alice',
    username: role === UserRole.ADMIN ? '管理员甲' : '爱丽丝',
    email: role === UserRole.ADMIN ? 'admin@example.com' : 'alice@example.com',
    role,
    status: UserStatus.ENABLED,
    createdAt: '2026-01-01T00:00:00',
  }
}

export function authenticate(
  auth: ReturnType<typeof useAuthStore>,
  role: (typeof UserRole)[keyof typeof UserRole] = UserRole.USER,
  overrides: Partial<CurrentUserResponse> = {},
): CurrentUserResponse {
  const me = { ...makeUser(role), ...overrides }
  auth.ready = true
  auth.accessToken = 'token'
  auth.tokenType = 'Bearer'
  auth.expiresAt = Date.now() + 3_600_000
  auth.userId = me.userId
  auth.identityStatus = IdentityStatus.RESOLVED
  auth.me = me
  return me
}

export async function mountApp(
  path = '/',
  setup?: (auth: ReturnType<typeof useAuthStore>) => void,
): Promise<{
  wrapper: VueWrapper
  router: Router
  auth: ReturnType<typeof useAuthStore>
}> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const auth = useAuthStore()
  auth.ready = true
  setup?.(auth)
  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  })
  await router.push(path)
  await flushPromises()
  return { wrapper, router, auth }
}
