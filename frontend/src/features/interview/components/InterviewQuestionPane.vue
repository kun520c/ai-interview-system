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
  gap: 24px;
  min-width: 0;
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 12px;
  color: var(--text-2);
}

.question-type {
  color: var(--primary);
  font-weight: 600;
  padding: 3px 10px;
  border: 1px solid var(--primary-border);
  border-radius: var(--radius-sm);
  background: var(--primary-soft);
}

.question-content {
  font-size: 23px;
  font-weight: 650;
  line-height: 1.75;
  letter-spacing: -.02em;
  color: var(--text);
  overflow-wrap: anywhere;
}

.submitted-answer {
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-subtle);
  min-width: 0;
}

.submitted-answer h3 {
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 650;
  color: var(--text-2);
}

.submitted-answer pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: var(--font);
  font-size: 14px;
  line-height: 1.8;
}

@media (max-width: 720px) {
  .question-pane {
    gap: 18px;
  }

  .question-content {
    font-size: 19px;
    line-height: 1.7;
  }

}
</style>
