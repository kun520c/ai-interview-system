export type AppShell = 'product' | 'interview' | 'public'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    anonymousOnly?: boolean
    shell: AppShell
    placeholderTitle: string
    documentTitle: string
  }
}

export {}
