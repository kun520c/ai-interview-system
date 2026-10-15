<script setup lang="ts">
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import { SESSION_COPY } from '@/features/interview/copy/sessionCopy'

defineProps<{
  draft: string
  locked: boolean
  invalid?: boolean
  describedBy?: string
}>()

const emit = defineEmits<{
  'update:draft': [value: string]
}>()
</script>

<template>
  <div class="answer-pane">
    <label class="answer-label" for="interview-answer">你的回答</label>
    <BaseTextarea
      id="interview-answer"
      class="session-answer-input"
      :model-value="draft"
      :readonly="locked"
      :invalid="invalid"
      :described-by="describedBy"
      :rows="12"
      placeholder="用完整技术表述作答。提交后不能再改成另一份答案。"
      @update:model-value="emit('update:draft', $event)"
    />
    <p v-if="locked" id="answer-lock-hint" class="lock-hint">{{ SESSION_COPY.dispatchedLock }}</p>
  </div>
</template>

<style scoped>
.answer-pane {
  display: grid;
  gap: 8px;
  min-width: 0;
  height: 100%;
}

.answer-label {
  font-size: 13px;
  font-weight: 650;
  color: var(--text-2);
}

.session-answer-input :deep(.base-textarea),
:deep(.session-answer-input) {
  min-height: 460px;
}

.lock-hint {
  font-size: 13px;
  color: var(--text-2);
}

@media (max-width: 720px) {
  .session-answer-input :deep(.base-textarea),
  :deep(.session-answer-input) {
    min-height: 180px;
  }
}
</style>
