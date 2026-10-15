import type { RouterHistory } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'

import { installRouterGuards } from '@/app/router/guards'
import { routes } from '@/app/router/routes'
import '@/app/router/types'

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes,
  })
  installRouterGuards(router)
  return router
}
