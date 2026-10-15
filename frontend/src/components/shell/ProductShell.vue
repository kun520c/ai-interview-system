<script setup lang="ts">
import { FileText, History, LayoutGrid, List, LogOut, Menu, Play, UserRound, X } from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/app/stores/auth'
import { useDismissibleMenu } from '@/components/shell/useDismissibleMenu'
import BaseIconButton from '@/components/ui/BaseIconButton.vue'
import ProductMark from '@/components/ui/ProductMark.vue'
import { IdentityStatus } from '@/types/auth'
import { UserRole } from '@/types/enums'

const ICON = { size: 18, strokeWidth: 1.7, 'aria-hidden': true }

interface ShellNavItem {
  name: string
  label: string
  icon: Component
}

const mainNav: ShellNavItem[] = [
  { name: 'dashboard', label: '工作台', icon: LayoutGrid },
  { name: 'interview-create', label: '开始面试', icon: Play },
  { name: 'interview-history', label: '历史', icon: History },
  { name: 'profile', label: '账号', icon: UserRound },
]

const adminNav: ShellNavItem[] = [
  { name: 'admin-questions', label: '题库', icon: List },
  { name: 'admin-knowledge', label: '知识文档', icon: FileText },
]

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const triggerRef = ref<unknown>(null)
const panelRef = ref<HTMLElement | null>(null)
const { open: menuOpen, close: closeMenu, toggle: toggleMenu } = useDismissibleMenu(triggerRef, panelRef)
const identityRetrying = ref(false)

watch(
  () => route.fullPath,
  () => {
    closeMenu()
  },
)

onMounted(() => {
  if (typeof window.matchMedia !== 'function') {
    return
  }
  const media = window.matchMedia('(min-width: 721px)')
  const onChange = (event: MediaQueryListEvent) => {
    if (event.matches) {
      closeMenu()
    }
  }
  media.addEventListener('change', onChange)
  onUnmounted(() => {
    media.removeEventListener('change', onChange)
  })
})

const showAdminNav = computed(() => auth.isAdmin)
const identityResolved = computed(() => auth.identityStatus === IdentityStatus.RESOLVED)
const identityLoading = computed(
  () => auth.identityStatus === IdentityStatus.UNKNOWN || auth.identityStatus === IdentityStatus.ANONYMOUS,
)
const identityUnavailable = computed(() => auth.identityStatus === IdentityStatus.UNAVAILABLE)
const displayName = computed(() => auth.me?.username ?? null)
const displayAccount = computed(() => auth.me?.account ?? null)
const displayRole = computed(() => {
  if (!auth.me) {
    return null
  }
  return auth.me.role === UserRole.ADMIN ? '管理员' : '用户'
})

function isNavActive(name: string): boolean {
  const current = String(route.name ?? '')
  if (name === 'dashboard') {
    return current === 'dashboard'
  }
  if (name === 'interview-history') {
    return current === 'interview-history' || current === 'interview-detail' || current === 'interview-report'
  }
  if (name === 'admin-questions') {
    return current.startsWith('admin-question')
  }
  return current === name
}

async function logout(): Promise<void> {
  closeMenu()
  auth.logout()
  await router.push({ name: 'login' })
}

async function retryIdentity(): Promise<void> {
  if (identityRetrying.value) {
    return
  }
  identityRetrying.value = true
  try {
    await auth.refreshMe()
  } catch {
    // refreshMe keeps UNAVAILABLE on transient failure; 401 is handled centrally.
  } finally {
    identityRetrying.value = false
  }
}
</script>

<template>
  <div class="product-shell">
    <a class="skip-link" href="#product-main">跳到正文</a>
    <aside class="product-sidebar" aria-label="产品导航">
      <RouterLink class="product-brand" :to="{ name: 'dashboard' }">
        <ProductMark :size="22" />
        <span class="product-brand-copy">
          <span class="product-brand-name">面试训练</span>
          <span class="product-brand-sub">技术面试工作台</span>
        </span>
      </RouterLink>
      <nav class="product-nav">
        <RouterLink
          v-for="item in mainNav"
          :key="item.name"
          :to="{ name: item.name }"
          custom
          v-slot="{ href, navigate }"
        >
          <a
            :href="href"
            :class="{ 'is-active': isNavActive(item.name) }"
            :aria-current="isNavActive(item.name) ? 'page' : undefined"
            @click="navigate"
          >
            <component :is="item.icon" v-bind="ICON" />
            {{ item.label }}
          </a>
        </RouterLink>
        <template v-if="showAdminNav">
          <p class="nav-group-label">管理</p>
          <RouterLink
            v-for="item in adminNav"
            :key="item.name"
            :to="{ name: item.name }"
            custom
            v-slot="{ href, navigate }"
          >
            <a
              :href="href"
              :class="{ 'is-active': isNavActive(item.name) }"
              :aria-current="isNavActive(item.name) ? 'page' : undefined"
              @click="navigate"
            >
              <component :is="item.icon" v-bind="ICON" />
              {{ item.label }}
            </a>
          </RouterLink>
        </template>
      </nav>
      <div class="sidebar-footer">
        <div v-if="identityLoading" class="identity-skeleton" aria-busy="true" aria-label="正在加载用户信息">
          <span class="skeleton-line"></span>
          <span class="skeleton-line is-short"></span>
        </div>
        <div v-else-if="identityUnavailable" class="identity-unavailable" role="status">
          <p>用户信息暂时无法加载</p>
          <button class="identity-retry" type="button" :disabled="identityRetrying" @click="retryIdentity">
            重试
          </button>
        </div>
        <div v-else-if="identityResolved && displayName" class="sidebar-user">
          <p class="identity-name">{{ displayName }}</p>
          <p v-if="displayAccount" class="identity-meta">{{ displayAccount }} · {{ displayRole }}</p>
        </div>
        <button class="nav-item" type="button" @click="logout">
          <LogOut v-bind="ICON" />
          退出登录
        </button>
      </div>
    </aside>

    <div class="product-main">
      <header class="mobile-topbar">
        <RouterLink class="product-brand" :to="{ name: 'dashboard' }">
          <ProductMark :size="20" />
          <span class="product-brand-name">面试训练</span>
        </RouterLink>
        <BaseIconButton
          ref="triggerRef"
          :label="menuOpen ? '关闭菜单' : '打开菜单'"
          :aria-expanded="menuOpen"
          aria-controls="product-mobile-menu"
          @click="toggleMenu"
        >
          <X v-if="menuOpen" v-bind="ICON" />
          <Menu v-else v-bind="ICON" />
        </BaseIconButton>
      </header>
      <div v-if="menuOpen" class="mobile-backdrop" @click="closeMenu"></div>
      <nav
        v-if="menuOpen"
        id="product-mobile-menu"
        ref="panelRef"
        class="mobile-menu is-open product-nav"
        aria-label="导航菜单"
        role="dialog"
        aria-modal="true"
      >
        <RouterLink
          v-for="item in mainNav"
          :key="`mobile-${item.name}`"
          :to="{ name: item.name }"
          custom
          v-slot="{ href, navigate }"
        >
          <a
            :href="href"
            :class="{ 'is-active': isNavActive(item.name) }"
            :aria-current="isNavActive(item.name) ? 'page' : undefined"
            @click="navigate"
          >
            <component :is="item.icon" v-bind="ICON" />
            {{ item.label }}
          </a>
        </RouterLink>
        <template v-if="showAdminNav">
          <p class="nav-group-label">管理</p>
          <RouterLink
            v-for="item in adminNav"
            :key="`mobile-${item.name}`"
            :to="{ name: item.name }"
            custom
            v-slot="{ href, navigate }"
          >
            <a
              :href="href"
              :class="{ 'is-active': isNavActive(item.name) }"
              :aria-current="isNavActive(item.name) ? 'page' : undefined"
              @click="navigate"
            >
              <component :is="item.icon" v-bind="ICON" />
              {{ item.label }}
            </a>
          </RouterLink>
        </template>
        <div v-if="identityUnavailable" class="identity-unavailable" role="status">
          <p>用户信息暂时无法加载</p>
          <button class="identity-retry" type="button" :disabled="identityRetrying" @click="retryIdentity">
            重试
          </button>
        </div>
        <div v-else-if="identityResolved && displayName" class="mobile-identity">
          <p class="identity-name">{{ displayName }}</p>
          <p v-if="displayAccount" class="identity-meta">{{ displayAccount }}</p>
        </div>
        <button class="nav-item" type="button" @click="logout">
          <LogOut v-bind="ICON" />
          退出登录
        </button>
      </nav>
      <main id="product-main" class="product-workspace">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.sidebar-footer {
  margin-top: auto;
  padding: 12px 8px 0;
  display: grid;
  gap: 8px;
}

.sidebar-user,
.mobile-identity {
  padding: 0 12px;
  min-width: 0;
}

.identity-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  overflow-wrap: anywhere;
}

.identity-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-2);
  overflow-wrap: anywhere;
}

.identity-skeleton {
  display: grid;
  gap: 8px;
  padding: 4px 12px 8px;
}

.skeleton-line {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-line.is-short {
  width: 48%;
}

.identity-unavailable {
  display: grid;
  gap: 8px;
  padding: 8px 12px;
  color: var(--text-2);
  font-size: 13px;
}

.identity-retry {
  justify-self: start;
  min-height: 44px;
  padding: 8px 12px;
  border-radius: var(--radius);
  color: var(--primary);
  font-size: 14px;
  font-weight: 600;
}

.identity-retry:hover:not(:disabled) {
  background: var(--primary-soft);
}

.identity-retry:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.sidebar-footer .nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border-radius: var(--radius);
  padding: 8px 12px;
  font-size: 14px;
  color: var(--text-2);
}

.mobile-identity {
  padding: 12px;
}
</style>
