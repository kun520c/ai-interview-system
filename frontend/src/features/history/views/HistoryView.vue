<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import HistoryPagination from '@/features/history/components/HistoryPagination.vue'
import HistoryRow from '@/features/history/components/HistoryRow.vue'
import { useInterviewHistory } from '@/features/history/composables/useInterviewHistory'
import { HISTORY_COPY } from '@/features/history/copy/historyCopy'
import { useCurrentInterview } from '@/features/interview/composables/useCurrentInterview'

const { data, loading, error, pagination, retry, goToPage } = useInterviewHistory()
const { session, refresh } = useCurrentInterview()

const items = computed(() => data.value?.items ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => data.value?.totalPages ?? 0)
const page = computed(() => data.value?.page ?? pagination.value.page)
const empty = computed(() => !loading.value && !error.value && items.value.length === 0)
const hasActive = computed(() => session.value !== null)

onMounted(() => {
  void refresh().catch(() => {
    // Empty CTA can still go to create/resume without blocking History.
  })
})
</script>

<template>
  <section class="history-page">
    <PageHeader :title="HISTORY_COPY.title" :description="HISTORY_COPY.summary(total)" />

    <div v-if="loading" class="history-skeleton" aria-busy="true" aria-label="正在加载历史面试">
      <div v-for="index in 4" :key="index" class="history-skeleton-row">
        <span class="skeleton-block"></span>
        <span class="skeleton-block is-short"></span>
      </div>
    </div>

    <StatusNotice v-else-if="error" tone="danger" role="alert">
      <p>加载失败</p>
      <p class="notice-copy">{{ HISTORY_COPY.loadError }}</p>
      <BaseButton variant="ghost" @click="retry()">{{ HISTORY_COPY.retry }}</BaseButton>
    </StatusNotice>

    <EmptyState
      v-else-if="empty"
      :title="HISTORY_COPY.emptyTitle"
      :description="HISTORY_COPY.emptyDescription"
    >
      <template #action>
        <RouterLink
          v-if="hasActive"
          class="empty-action"
          :to="{ name: 'interview-session' }"
        >
          {{ HISTORY_COPY.continueCta }}
        </RouterLink>
        <RouterLink v-else class="empty-action" :to="{ name: 'interview-create' }">
          {{ HISTORY_COPY.startCta }}
        </RouterLink>
      </template>
    </EmptyState>

    <template v-else>
      <ul class="history-list">
        <HistoryRow v-for="item in items" :key="item.sessionId" :item="item" />
      </ul>
      <HistoryPagination
        :page="page"
        :total-pages="totalPages"
        :disabled="loading"
        @previous="goToPage(page - 1)"
        @next="goToPage(page + 1)"
      />
    </template>
  </section>
</template>

<style scoped>
.history-page {
  width: 100%;
  min-width: 0;
}

.history-skeleton,
.history-list {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow: hidden;
}

.history-skeleton-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 16px;
  padding: 16px 20px;
}

.history-skeleton-row + .history-skeleton-row {
  border-top: 1px solid var(--border);
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

.notice-copy {
  margin: 8px 0 12px;
}

.empty-action {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  color: var(--primary);
  font-weight: 600;
}
</style>
