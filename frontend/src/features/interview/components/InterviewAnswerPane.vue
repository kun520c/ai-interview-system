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
    <div class="answer-toolbar"><label class="answer-label" for="interview-answer">你的回答</label><span class="answer-format">文本作答</span></div>
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
    <p v-else class="answer-guidance">按你的理解展开，结合原理与实践说明。</p>
  </div>
</template>

<style scoped>
.answer-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  height: 100%;
}

.answer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.answer-label {
  font-size: 14px;
  font-weight: 650;
  color: var(--text);
}

.answer-format {
  font-size: 11px;
  color: var(--text-2);
  background: var(--surface-subtle);
  padding: 3px 8px;
  border-radius: var(--radius-sm);
}

:deep(.session-answer-input) {
  flex: 1;
  min-height: 340px;
  padding: 18px;
  line-height: 1.85;
  font-size: 15px;
  background: var(--surface);
  border-color: var(--border-strong);
  resize: vertical;
}

.lock-hint, .answer-guidance {
  font-size: 12px;
  color: var(--text-2);
}

@media (max-width: 720px) {
  .answer-pane {
    height: auto;
  }

  :deep(.session-answer-input) {
    min-height: 270px;
  }

}
</style>
