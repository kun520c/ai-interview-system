<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import {
  historyScoreText,
} from '@/features/history/utils/historyScoreDisplay'
import { evaluationLevelFromScore, displayEvaluationLevel } from '@/features/interview/copy/evaluationLevelDisplay'
import {
  reportActionLabel,
  reportStatusLabel,
} from '@/features/interview/copy/reportStatusDisplay'
import { formatInterviewDate, formatInterviewDateShort } from '@/utils/formatInterviewDate'
import { InterviewReportStatus } from '@/types/enums'
import type { ApiError } from '@/api/apiError'
import type { InterviewHistoryItemResponse, InterviewHistoryPageResponse } from '@/types/interview'

const props = defineProps<{
  data: InterviewHistoryPageResponse | null
  loading: boolean
  error: ApiError | null
  hasActiveInterview: boolean
}>()

const emit = defineEmits<{
  retry: []
}>()

const items = computed(() => props.data?.items ?? [])
const empty = computed(() => !props.loading && !props.error && items.value.length === 0)

function scoreText(item: InterviewHistoryItemResponse): string {
  return historyScoreText(item)
}

function levelText(item: InterviewHistoryItemResponse): string | null {
  if (item.totalScore === null || item.totalScore === undefined) {
    return null
  }
  if (item.reportStatus !== InterviewReportStatus.READY) {
    return null
  }
  return displayEvaluationLevel(evaluationLevelFromScore(item.totalScore))
}

function actionAriaLabel(item: InterviewHistoryItemResponse): string {
  const date = formatInterviewDateShort(item.endedAt ?? item.createdAt)
  const action = reportActionLabel(item.reportStatus)
  return `${action} ${date} ${item.difficulty} 面试`
}
</script>

<template>
  <section class="recent" aria-labelledby="dashboard-recent-title" :aria-busy="loading ? 'true' : undefined">
    <div class="recent-heading"><h2 id="dashboard-recent-title">最近完成的面试</h2><RouterLink :to="{ name: 'interview-history' }">全部记录 <span aria-hidden="true">↗</span></RouterLink></div>
    <p class="u-muted">回看回答与反馈，找到下一次训练的方向。</p>

    <div v-if="loading" class="recent-skeleton" aria-label="正在加载最近面试">
      <div v-for="index in 3" :key="index" class="recent-skeleton-row">
        <span class="skeleton-block"></span>
        <span class="skeleton-block is-short"></span>
      </div>
    </div>

    <StatusNotice v-else-if="error" tone="danger">
      <p>加载失败</p>
      <p class="notice-copy">最近面试记录暂时无法加载。</p>
      <BaseButton variant="ghost" @click="emit('retry')">重试</BaseButton>
    </StatusNotice>

    <EmptyState
      v-else-if="empty"
      title="还没有完成的面试"
      description="完成第一场后，这里会保存你的面试记录和报告入口。"
    >
      <template #action>
        <RouterLink
          v-if="hasActiveInterview"
          class="empty-action"
          :to="{ name: 'interview-session' }"
        >
          继续当前面试
        </RouterLink>
        <RouterLink v-else class="empty-action" :to="{ name: 'interview-create' }">
          开始第一次面试
        </RouterLink>
      </template>
    </EmptyState>

    <ul v-else class="recent-list">
      <li v-for="item in items" :key="item.sessionId" class="recent-row">
        <div class="recent-main">
          <p class="recent-difficulty">{{ item.difficulty }}</p>
          <p class="recent-date">{{ formatInterviewDate(item.endedAt ?? item.createdAt) }}</p>
        </div>
        <div class="recent-score">
          <p>{{ scoreText(item) }}</p>
          <p v-if="levelText(item)" class="recent-level">{{ levelText(item) }}</p>
          <p class="recent-status">{{ reportStatusLabel(item.reportStatus) }}</p>
        </div>
        <RouterLink
          class="recent-action"
          :to="{ name: 'interview-report', params: { sessionId: String(item.sessionId) } }"
          :aria-label="actionAriaLabel(item)"
        >
          {{ reportActionLabel(item.reportStatus) }}
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.recent {
  min-width: 0;
}

.recent h2 {
  font-size: 20px;
  font-weight: 650;
  line-height: 1.35;
}

.recent > .u-muted {
  margin-top: 8px;
  margin-bottom: 16px;
  font-size: 14px;
}

.recent-skeleton, .recent-list {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow: hidden;
}

.recent-skeleton-row, .recent-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px 20px;
  min-width: 0;
}

.recent-skeleton-row + .recent-skeleton-row, .recent-row + .recent-row {
  border-top: 1px solid var(--border);
}

.recent-row:hover {
  background: var(--surface-subtle);
}

.skeleton-block {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-block.is-short {
  width: 40%;
}

.recent-difficulty {
  font-family: var(--mono);
  font-weight: 500;
}

.recent-date, .recent-status, .recent-level {
  font-size: 13px;
  color: var(--text-2);
}

.recent-score p:first-child {
  font-family: var(--mono);
}

.recent-action {
  display: inline-flex;
  align-items: center;
  justify-self: end;
  min-height: 44px;
  color: var(--primary);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.recent-action:hover {
  color: var(--primary-hover);
}

.notice-copy {
  margin: 8px 0 12px;
}

.empty-action {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  color: var(--primary);
  font-weight: 600;
}

@media (max-width: 720px) {
  .recent-skeleton-row, .recent-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .recent-action {
    justify-self: start;
  }

}

.recent-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.recent-heading h2 {
  font-size: 18px;
}

.recent-heading > a {
  color: var(--primary);
  font-size: 12px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.recent-row {
  padding: 20px 24px;
}

.recent-difficulty {
  font-size: 12px;
  font-weight: 650;
}

.recent-date {
  margin-top: 4px;
  font-size: 12px;
}

.recent-score p:first-child {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -.02em;
}

.recent-action {
  font-size: 12px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

@media (max-width:720px) {
  .recent-row {
    grid-template-columns: minmax(0,1fr) auto;
    padding: 18px;
    gap: 12px;
  }

  .recent-action {
    grid-column: 1 / -1;
    justify-self: stretch;
    justify-content: center;
  }

  .recent-score {
    text-align: right;
  }

}
</style>
