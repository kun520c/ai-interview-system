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
      <p class="studio-eyebrow">YOUR INTERVIEW STUDIO</p>
      <h1>{{ greeting }}</h1>
      <p class="u-muted">从一次认真作答，到下一次从容表达。</p>
    </header>

    <div class="dashboard-grid">
      <section
        class="training-surface"
        aria-labelledby="dashboard-training-title"
        :aria-busy="currentBusy ? 'true' : undefined"
      >
        <p class="training-label"><span class="training-indicator" aria-hidden="true"></span>JAVA 后端 · 结构化面试</p>
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
            继续面试<span class="cta-arrow" aria-hidden="true"></span>
          </RouterLink>
        </div>

        <div v-else-if="noActive">
          <div class="idle-mark">
            <ProductMark :size="24" />
          </div>
          <h2 id="dashboard-training-title">开始一次模拟面试</h2>
          <p class="training-copy">选择难度后，系统会进入连续面试流程。</p>
          <RouterLink class="training-cta-link" :to="{ name: 'interview-create' }">
            开始面试<span class="cta-arrow" aria-hidden="true"></span>
          </RouterLink>
        </div>
        <p class="training-footnote">逐题作答 <span aria-hidden="true">/</span> 针对性评价 <span aria-hidden="true">/</span> 完整复盘</p>
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
  gap: 32px;
  min-width: 0;
}

.studio-eyebrow {
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .14em;
  margin-bottom: 10px;
}

.dashboard-greeting h1 {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -.03em;
}

.dashboard-greeting > .u-muted {
  margin-top: 10px;
  font-size: 14px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: 24px;
}

.training-surface {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 28px 32px;
  background: var(--training-wash);
  border: 1px solid var(--primary-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-panel);
}

.training-label {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--primary);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: .06em;
  margin-bottom: 24px;
}

.training-indicator {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.training-surface h2 {
  font-size: 27px;
  font-weight: 700;
  letter-spacing: -.025em;
  line-height: 1.4;
}

.training-surface :deep(.interview-progress) {
  margin-top: 20px;
  max-width: 400px;
}

.training-copy, .training-surface .u-muted {
  margin-top: 10px;
  font-size: 14px;
  color: var(--text-2);
}

.idle-mark {
  display: none;
}

.training-cta, .training-cta-link {
  margin-top: 24px;
}

.training-cta-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  min-height: 44px;
  padding: 10px 20px;
  border-radius: var(--radius);
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  box-shadow: var(--shadow-sm);
  transition: background var(--move);
}

.training-cta-link:hover {
  background: var(--primary-hover);
}

.cta-arrow::after {
  content: '→';
}

.training-footnote {
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid var(--primary-border);
  font-size: 12px;
  color: var(--text-2);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.training-footnote span {
  color: var(--border-strong);
}

.training-skeleton {
  display: grid;
  gap: 12px;
}

.skeleton-line, .skeleton-button {
  display: block;
  border-radius: var(--radius-sm);
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
  height: 44px;
  margin-top: 12px;
}

@media (prefers-reduced-motion: no-preference) {
  .skeleton-line, .skeleton-button {
    animation: pulse 1.6s ease-in-out infinite;
  }

}

@keyframes pulse {
  50% {
    opacity: .65;
  }

}

@media (max-width: 1100px) {
  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }

}

@media (max-width: 720px) {
  .dashboard {
    gap: 24px;
  }

  .dashboard-greeting h1 {
    font-size: 26px;
  }

  .training-surface {
    padding: 24px;
  }

  .training-surface h2 {
    font-size: 24px;
  }

}
</style>
