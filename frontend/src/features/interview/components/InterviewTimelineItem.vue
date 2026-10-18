<script setup lang="ts">
import { computed } from 'vue'

import InterviewEvaluationPane from '@/features/interview/components/InterviewEvaluationPane.vue'
import { displayCategory } from '@/features/interview/copy/categoryDisplay'
import { DETAIL_COPY } from '@/features/interview/copy/detailCopy'
import { QUESTION_TYPE_LABEL } from '@/features/interview/copy/sessionCopy'
import { formatInterviewDate } from '@/utils/formatInterviewDate'
import { InterviewQuestionType } from '@/types/enums'
import type { InterviewDetailQuestionResponse } from '@/types/interview'

const props = defineProps<{
  item: InterviewDetailQuestionResponse
  expanded?: boolean
  collapsible?: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const panelId = computed(() => `timeline-panel-${props.item.interviewQuestionId}`)
const headingId = computed(() => `timeline-heading-${props.item.interviewQuestionId}`)
const typeLabel = computed(() => QUESTION_TYPE_LABEL[props.item.questionType])
const markerClass = computed(() =>
  props.item.questionType === InterviewQuestionType.FOLLOW_UP ? 'is-follow-up' : 'is-main',
)
const showBody = computed(() => !props.collapsible || props.expanded !== false)
</script>

<template>
  <article class="timeline-item" :aria-labelledby="headingId">
    <header class="timeline-header">
      <span class="timeline-marker" :class="markerClass" aria-hidden="true"></span>
      <div class="timeline-heading-wrap">
        <p class="timeline-meta">
          <span>{{ typeLabel }}</span>
          <span>{{ displayCategory(item.category) }}</span>
          <span>{{ item.knowledgePoint }}</span>
        </p>
        <h3 :id="headingId" class="timeline-heading">
          <button
            v-if="collapsible"
            class="timeline-title"
            type="button"
            :aria-expanded="showBody"
            :aria-controls="panelId"
            @click="emit('toggle')"
          >
            {{ item.questionContent }}
          </button>
          <span v-else class="timeline-title">{{ item.questionContent }}</span>
        </h3>
      </div>
    </header>

    <div v-show="showBody" :id="panelId">
      <section class="timeline-answer" :aria-labelledby="`${panelId}-answer`">
        <h4 :id="`${panelId}-answer`">{{ DETAIL_COPY.answerHeading }}</h4>
        <template v-if="item.answer">
          <p class="answer-meta">{{ formatInterviewDate(item.answer.submittedAt) }}</p>
          <pre>{{ item.answer.answerContent }}</pre>
        </template>
        <p v-else class="u-muted">{{ DETAIL_COPY.missingAnswer }}</p>
      </section>

      <div v-if="item.evaluation" class="timeline-evaluation">
        <InterviewEvaluationPane
          :evaluation="item.evaluation"
          heading-tag="h4"
          :heading-id="`${panelId}-evaluation`"
        />
      </div>
      <p v-else class="u-muted missing-evaluation">{{ DETAIL_COPY.missingEvaluation }}</p>
    </div>
  </article>
</template>

<style scoped>
.timeline-item {
  position: relative;
  padding-left: 20px;
  min-width: 0;
}

.timeline-item + .timeline-item {
  margin-top: 32px;
  padding-top: 32px;
  border-top: 1px solid var(--border);
}

.timeline-header {
  display: flex;
  gap: 12px;
  min-width: 0;
}

.timeline-marker {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  margin-top: 8px;
  border-radius: 50%;
  background: var(--primary);
}

.timeline-marker.is-follow-up {
  background: var(--warning);
}

.timeline-heading-wrap {
  min-width: 0;
  flex: 1;
}

.timeline-heading {
  margin: 0;
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-2);
}

.timeline-title {
  display: block;
  width: 100%;
  min-height: 44px;
  text-align: left;
  font-size: 18px;
  font-weight: 650;
  line-height: 1.5;
  color: var(--text);
  overflow-wrap: anywhere;
  background: transparent;
}

.timeline-answer, .timeline-evaluation, .missing-evaluation {
  margin-top: 16px;
}

.timeline-answer {
  padding: 12px 14px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
  min-width: 0;
}

.timeline-answer h4, .answer-meta {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 650;
  color: var(--text-2);
}

.timeline-answer pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: var(--font);
  font-size: 15px;
  line-height: 1.7;
}

.timeline-evaluation :deep(.evaluation-heading) {
  font-size: 16px;
}

.timeline-item {
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.timeline-item + .timeline-item {
  margin-top: 16px;
  padding-top: 24px;
}

.timeline-title {
  font-size: 16px;
}

.timeline-title[aria-expanded]::after {
  content: '−';
  float: right;
  margin-left: 12px;
  color: var(--primary);
}

.timeline-title[aria-expanded='false']::after {
  content: '+';
}

@media (max-width:720px) {
  .timeline-item {
    padding: 18px;
  }

  .timeline-item + .timeline-item {
    padding-top: 18px;
  }

}
</style>
