import { applyDocumentTitle } from '@/app/router/documentTitle'
import type { NavigationGuardWithThis, Router } from 'vue-router'

import { useAuthStore } from '@/app/stores/auth'
import { IdentityStatus } from '@/types/auth'
import { resolveSafeRedirect } from '@/utils/safeRedirect'

export const createAuthGuard = (): NavigationGuardWithThis<undefined> => {
  return async (to) => {
    const auth = useAuthStore()
    if (!auth.ready) {
      await auth.initialize()
    }

    if (to.meta.anonymousOnly && auth.isAuthenticated) {
      return { path: resolveSafeRedirect(to.query.redirect) }
    }

    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      return {
        name: 'login',
        query: {
          redirect: resolveSafeRedirect(to.fullPath),
        },
      }
    }

    if (to.meta.requiresAdmin) {
      if (auth.identityStatus === IdentityStatus.RESOLVED && !auth.isAdmin) {
        return { name: 'forbidden' }
      }
    }

    return true
  }
}

export function installRouterGuards(router: Router): void {
  router.beforeEach(createAuthGuard())
  router.afterEach((to) => {
    document.title = applyDocumentTitle(to.meta.documentTitle)
  })
}
