<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { HISTORY_COPY } from '@/features/history/copy/historyCopy'
import {
  historyReportActionLabel,
  historyScoreText,
} from '@/features/history/utils/historyScoreDisplay'
import { formatInterviewDate } from '@/utils/formatInterviewDate'
import { InterviewReportStatus } from '@/types/enums'
import type { InterviewHistoryItemResponse } from '@/types/interview'

defineProps<{
  item: InterviewHistoryItemResponse
}>()

function scoreTone(item: InterviewHistoryItemResponse): string {
  if (item.totalScore !== null && item.totalScore !== undefined) {
    return 'has-score'
  }
  if (item.reportStatus === InterviewReportStatus.FAILED) {
    return 'is-failed'
  }
  if (item.reportStatus === InterviewReportStatus.READY) {
    return 'is-abnormal'
  }
  return 'is-pending'
}
</script>

<template>
  <li class="history-row">
    <div class="history-primary">
      <p class="history-difficulty">{{ item.difficulty }}</p>
      <p class="history-date">{{ formatInterviewDate(item.endedAt ?? item.createdAt) }}</p>
    </div>
    <div class="history-score" :class="scoreTone(item)">
      <p>{{ historyScoreText(item) }}</p>
    </div>
    <p class="history-progress">
      {{ item.completedQuestionCount }} / {{ item.plannedQuestionCount }}
    </p>
    <div class="history-actions">
      <RouterLink
        class="history-action"
        :to="{ name: 'interview-detail', params: { sessionId: String(item.sessionId) } }"
      >
        {{ HISTORY_COPY.reviewAction }}
      </RouterLink>
      <RouterLink
        class="history-action is-report"
        :to="{ name: 'interview-report', params: { sessionId: String(item.sessionId) } }"
      >
        {{ historyReportActionLabel(item.reportStatus) }}
      </RouterLink>
    </div>
  </li>
</template>

<style scoped>
.history-row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) auto auto;
  gap: 16px;
  align-items: center;
  padding: 16px 20px;
  min-width: 0;
}

.history-row + .history-row {
  border-top: 1px solid var(--border);
}

.history-row:hover {
  background: var(--surface-subtle);
}

.history-difficulty {
  font-family: var(--mono);
  font-weight: 500;
}

.history-date,
.history-progress {
  font-size: 13px;
  color: var(--text-2);
}

.history-score p {
  font-size: 14px;
}

.has-score p {
  font-family: var(--mono);
  font-size: 16px;
  font-weight: 500;
}

.is-failed p,
.is-abnormal p {
  color: var(--danger);
}

.is-pending p {
  color: var(--text-2);
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 16px;
}

.history-action {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.history-action.is-report {
  color: var(--primary);
}

.history-action:hover {
  color: var(--primary-hover);
}

@media (max-width: 1024px) {
  .history-row {
    grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) auto;
    grid-template-areas:
      'primary score actions'
      'progress progress actions';
  }

  .history-primary {
    grid-area: primary;
  }

  .history-score {
    grid-area: score;
  }

  .history-progress {
    grid-area: progress;
  }

  .history-actions {
    grid-area: actions;
  }
}

@media (max-width: 720px) {
  .history-row {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: none;
    gap: 8px;
    padding: 16px;
  }

  .history-primary,
  .history-score,
  .history-progress,
  .history-actions {
    grid-area: auto;
  }

  .history-actions {
    justify-content: flex-start;
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
