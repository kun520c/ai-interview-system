import type { Router } from 'vue-router'

import { resetUnauthorizedGate, setUnauthorizedHandler } from '@/api/unauthorized'
import { useAuthStore } from '@/app/stores/auth'
import { resolveSafeRedirect } from '@/utils/safeRedirect'

export function bindUnauthorizedNavigation(router: Router): void {
  setUnauthorizedHandler(() => {
    const auth = useAuthStore()
    auth.handleUnauthorized()
    const current = router.currentRoute.value
    if (current.meta.anonymousOnly || current.name === 'login' || current.name === 'register') {
      resetUnauthorizedGate()
      return
    }
    void router
      .replace({
        name: 'login',
        query: { redirect: resolveSafeRedirect(current.fullPath) },
      })
      .finally(() => {
        resetUnauthorizedGate()
      })
  })
}
