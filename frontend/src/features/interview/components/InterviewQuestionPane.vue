<script setup lang="ts">
import { computed } from 'vue'

import InterviewProgress from '@/features/interview/components/InterviewProgress.vue'
import { QUESTION_TYPE_LABEL, SESSION_COPY } from '@/features/interview/copy/sessionCopy'
import type { InterviewQuestionResponse, InterviewSessionResponse } from '@/types/interview'

const props = defineProps<{
  session: InterviewSessionResponse | null
  question: InterviewQuestionResponse | null
  submittedAnswer?: string | null
}>()

const typeLabel = computed(() =>
  props.question ? QUESTION_TYPE_LABEL[props.question.questionType] : '',
)
</script>

<template>
  <div class="question-pane">
    <InterviewProgress v-if="session" :session="session" />
    <div v-if="question" class="question-meta">
      <span class="question-type">{{ typeLabel }}</span>
    </div>
    <div v-if="question" class="question-content">{{ question.content }}</div>
    <section v-if="submittedAnswer !== undefined && submittedAnswer !== null" class="submitted-answer">
      <h3>{{ SESSION_COPY.submittedAnswerPane }}</h3>
      <pre>{{ submittedAnswer }}</pre>
    </section>
  </div>
</template>

<style scoped>
.question-pane {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 13px;
  color: var(--text-2);
}

.question-type {
  color: var(--text);
  font-weight: 600;
}

.question-content {
  font-size: 20px;
  font-weight: 650;
  line-height: 1.55;
  color: var(--text);
  overflow-wrap: anywhere;
}

.submitted-answer {
  padding: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
  min-width: 0;
}

.submitted-answer h3 {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-2);
}

.submitted-answer pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: var(--font);
  font-size: 14px;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .question-content {
    font-size: 18px;
  }
}
</style>
