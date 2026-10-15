<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import ProductMark from '@/components/ui/ProductMark.vue'
import DashboardInterviewFlow from '@/features/dashboard/components/DashboardInterviewFlow.vue'
import DashboardRecentList from '@/features/dashboard/components/DashboardRecentList.vue'
import { useDashboardHistory } from '@/features/dashboard/composables/useDashboardHistory'
import InterviewProgress from '@/features/interview/components/InterviewProgress.vue'
import { useCurrentInterview } from '@/features/interview/composables/useCurrentInterview'
import { useAuthStore } from '@/app/stores/auth'
import { IdentityStatus } from '@/types/auth'

const auth = useAuthStore()
const {
  session,
  loaded,
  loading,
  error,
  refresh,
} = useCurrentInterview()
const {
  data: historyData,
  loading: historyLoading,
  error: historyError,
  load: loadHistory,
  retry: retryHistory,
} = useDashboardHistory()

const greeting = computed(() => {
  if (auth.identityStatus === IdentityStatus.RESOLVED && auth.me?.username) {
    return `你好，${auth.me.username}`
  }
  return '工作台'
})

const currentBusy = computed(() => loading.value && !loaded.value)
const currentError = computed(() => Boolean(error.value) && !session.value)
const noActive = computed(() => loaded.value && !session.value && !error.value)
const hasActive = computed(() => session.value !== null)

onMounted(() => {
  void refresh().catch(() => {
    // Region-local error is stored on the current-interview owner.
  })
  void loadHistory().catch(() => {
    // 401 is handled by global auth navigation. Other failures stay in the history region.
  })
})

async function retryCurrent(): Promise<void> {
  try {
    await refresh()
  } catch {
    // Keep region error.
  }
}

async function retryHistoryLoad(): Promise<void> {
  try {
    await retryHistory()
  } catch {
    // 401 is handled by global auth navigation.
  }
}
</script>

<template>
  <section class="dashboard">
    <header class="dashboard-greeting">
      <h1>{{ greeting }}</h1>
      <p class="u-muted">我现在应该做什么？开始一场新的模拟面试，或继续未完成的训练。</p>
    </header>

    <div class="dashboard-grid">
      <section
        class="training-surface"
        aria-labelledby="dashboard-training-title"
        :aria-busy="currentBusy ? 'true' : undefined"
      >
        <div v-if="currentBusy" class="training-skeleton" aria-label="正在加载当前面试状态">
          <span class="skeleton-line is-title"></span>
          <span class="skeleton-line"></span>
          <span class="skeleton-line is-short"></span>
          <span class="skeleton-button"></span>
        </div>

        <div v-else-if="currentError" role="alert">
          <h2 id="dashboard-training-title">当前面试状态暂时无法加载</h2>
          <p class="u-muted">工作台其他区域不受影响，可以稍后重试。</p>
          <BaseButton class="training-cta" @click="retryCurrent">重试</BaseButton>
        </div>

        <div v-else-if="session">
          <h2 id="dashboard-training-title">继续当前面试</h2>
          <InterviewProgress :session="session" />
          <RouterLink class="training-cta-link" :to="{ name: 'interview-session' }">
            继续面试
          </RouterLink>
        </div>

        <div v-else-if="noActive">
          <div class="idle-mark">
            <ProductMark :size="24" />
          </div>
          <h2 id="dashboard-training-title">开始一次模拟面试</h2>
          <p class="training-copy">选择难度后，系统会进入连续面试流程。</p>
          <RouterLink class="training-cta-link" :to="{ name: 'interview-create' }">
            开始面试
          </RouterLink>
        </div>
      </section>

      <DashboardInterviewFlow />
    </div>

    <DashboardRecentList
      :data="historyData"
      :loading="historyLoading"
      :error="historyError"
      :has-active-interview="hasActive"
      @retry="retryHistoryLoad()"
    />
  </section>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: 40px;
  min-width: 0;
}

.dashboard-greeting h1 {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
}

.dashboard-greeting p {
  margin-top: 8px;
  font-size: 14px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: 24px;
  align-items: stretch;
}

.training-surface {
  min-width: 0;
  padding: 28px 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: 0 1px 2px rgba(17, 24, 39, 0.04);
}

.training-surface h2 {
  font-size: 20px;
  font-weight: 650;
  line-height: 1.35;
}

.training-copy,
.training-surface .u-muted {
  margin-top: 8px;
  font-size: 14px;
  color: var(--text-2);
}

.idle-mark {
  margin-bottom: 16px;
}

.training-cta {
  margin-top: 24px;
}

.training-cta-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  margin-top: 24px;
  padding: 8px 16px;
  border-radius: var(--radius);
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.training-cta-link:hover {
  background: var(--primary-hover);
}

.training-skeleton {
  display: grid;
  gap: 12px;
}

.skeleton-line,
.skeleton-button {
  display: block;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-line {
  height: 14px;
}

.skeleton-line.is-title {
  height: 24px;
  width: 48%;
}

.skeleton-line.is-short {
  width: 36%;
}

.skeleton-button {
  width: 112px;
  height: 40px;
  margin-top: 12px;
}

@media (prefers-reduced-motion: no-preference) {
  .skeleton-line,
  .skeleton-button {
    animation: pulse 1.6s ease-in-out infinite;
  }
}

@keyframes pulse {
  50% {
    opacity: 0.65;
  }
}

@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
