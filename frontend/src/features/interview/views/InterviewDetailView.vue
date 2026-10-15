<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import InterviewRecordMissing from '@/features/interview/components/InterviewRecordMissing.vue'
import InterviewTimeline from '@/features/interview/components/InterviewTimeline.vue'
import { DetailPageState, useInterviewDetail } from '@/features/interview/composables/useInterviewDetail'
import { DETAIL_COPY } from '@/features/interview/copy/detailCopy'
import { formatInterviewDate } from '@/utils/formatInterviewDate'

const { pageState, detail, timeline, hasIncompleteItems, retry } = useInterviewDetail()

const headingDescription = computed(() => {
  if (!detail.value) {
    return undefined
  }
  const parts: string[] = [
    detail.value.difficulty,
    `${detail.value.completedQuestionCount} / ${detail.value.plannedQuestionCount}`,
  ]
  if (detail.value.endedAt) {
    parts.push(formatInterviewDate(detail.value.endedAt))
  }
  return parts.join(' · ')
})

const showTimeline = computed(() => pageState.value === DetailPageState.READY)
</script>

<template>
  <section class="detail-page">
    <template
      v-if="
        pageState === DetailPageState.INVALID_ID ||
        pageState === DetailPageState.NOT_FOUND ||
        pageState === DetailPageState.UNAVAILABLE
      "
    >
      <InterviewRecordMissing :title="DETAIL_COPY.missingRecord" />
    </template>

    <template v-else-if="pageState === DetailPageState.LOADING || pageState === DetailPageState.REDIRECTING">
      <div class="detail-skeleton" aria-busy="true" :aria-label="DETAIL_COPY.loading">
        <span class="skeleton-block is-title"></span>
        <span class="skeleton-block"></span>
        <span class="skeleton-block is-short"></span>
      </div>
    </template>

    <StatusNotice v-else-if="pageState === DetailPageState.ERROR" tone="danger" role="alert">
      <p>{{ DETAIL_COPY.loadError }}</p>
      <BaseButton variant="ghost" @click="retry()">{{ DETAIL_COPY.retry }}</BaseButton>
    </StatusNotice>

    <StatusNotice v-else-if="pageState === DetailPageState.EMPTY_TIMELINE" tone="danger" role="alert">
      <p>{{ DETAIL_COPY.emptyTimeline }}</p>
      <div class="detail-actions">
        <BaseButton variant="ghost" @click="retry()">{{ DETAIL_COPY.retry }}</BaseButton>
        <RouterLink class="detail-link" :to="{ name: 'interview-history' }">
          {{ DETAIL_COPY.backToHistory }}
        </RouterLink>
      </div>
    </StatusNotice>

    <template v-else-if="showTimeline && detail">
      <PageHeader :title="DETAIL_COPY.title" :description="headingDescription">
        <template #actions>
          <div class="detail-actions">
            <RouterLink class="detail-link" :to="{ name: 'interview-history' }">
              {{ DETAIL_COPY.backToHistory }}
            </RouterLink>
            <RouterLink
              class="detail-link is-primary"
              :to="{ name: 'interview-report', params: { sessionId: String(detail.sessionId) } }"
            >
              {{ DETAIL_COPY.openReport }}
            </RouterLink>
          </div>
        </template>
      </PageHeader>

      <StatusNotice v-if="hasIncompleteItems" tone="warning">
        {{ DETAIL_COPY.incompleteNotice }}
      </StatusNotice>

      <InterviewTimeline :items="timeline" />
    </template>
  </section>
</template>

<style scoped>
.detail-page {
  width: 100%;
  min-width: 0;
}

.detail-skeleton {
  display: grid;
  gap: 12px;
}

.skeleton-block {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-block.is-title {
  height: 24px;
  width: 40%;
}

.skeleton-block.is-short {
  width: 55%;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
}

.detail-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  color: var(--text);
}

.detail-link.is-primary {
  color: var(--primary);
}
</style>
