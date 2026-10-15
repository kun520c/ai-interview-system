<script setup lang="ts">
import { computed } from 'vue'

import { HISTORY_COPY } from '@/features/history/copy/historyCopy'

const props = withDefaults(
  defineProps<{
    page: number
    totalPages: number
    disabled?: boolean
    ariaLabel?: string
  }>(),
  { ariaLabel: undefined },
)

const emit = defineEmits<{
  previous: []
  next: []
}>()

const canPrevious = computed(() => props.page > 1 && !props.disabled)
const canNext = computed(() => props.page < props.totalPages && !props.disabled)
</script>

<template>
  <nav v-if="totalPages > 1" class="history-pagination" :aria-label="ariaLabel ?? '分页'">
    <button
      type="button"
      class="page-button"
      :disabled="!canPrevious"
      @click="emit('previous')"
    >
      {{ HISTORY_COPY.previousPage }}
    </button>
    <p class="page-status" aria-current="page">
      {{ HISTORY_COPY.pageStatus(page, totalPages) }}
    </p>
    <button
      type="button"
      class="page-button"
      :disabled="!canNext"
      @click="emit('next')"
    >
      {{ HISTORY_COPY.nextPage }}
    </button>
  </nav>
</template>

<style scoped>
.history-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.page-button {
  min-height: 44px;
  min-width: 44px;
  padding: 0 16px;
  border-radius: var(--radius);
  box-shadow: inset 0 0 0 1px var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  background: var(--surface);
}

.page-button:hover:not(:disabled) {
  background: var(--surface-subtle);
}

.page-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.page-status {
  font-size: 14px;
  color: var(--text-2);
}
</style>
