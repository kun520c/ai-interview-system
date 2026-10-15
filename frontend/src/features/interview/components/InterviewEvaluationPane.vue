<script setup lang="ts">
import { computed } from 'vue'
import { Check, TriangleAlert } from '@lucide/vue'

import { displayEvaluationLevel } from '@/features/interview/copy/evaluationLevelDisplay'
import { EVALUATION_PHASE_LABEL, SCORE_DIMENSIONS, SESSION_COPY } from '@/features/interview/copy/sessionCopy'
import { EvaluationLevel } from '@/types/enums'
import type { InterviewEvaluationResponse } from '@/types/interview'

const props = withDefaults(
  defineProps<{
    evaluation: InterviewEvaluationResponse
    headingTag?: 'h2' | 'h3' | 'h4'
    headingId?: string
  }>(),
  { headingTag: 'h2', headingId: 'evaluation-feedback-heading' },
)

const subheadingTag = computed(() => {
  if (props.headingTag === 'h2') {
    return 'h3'
  }
  if (props.headingTag === 'h3') {
    return 'h4'
  }
  return 'h5'
})

const ICON = { size: 16, strokeWidth: 1.7, 'aria-hidden': true }

function levelClass(level: EvaluationLevel): string {
  if (level === EvaluationLevel.EXCELLENT || level === EvaluationLevel.GOOD) {
    return 'is-success'
  }
  if (level === EvaluationLevel.FAIR) {
    return 'is-warning'
  }
  return 'is-danger'
}
</script>

<template>
  <section class="evaluation-pane" :aria-labelledby="headingId">
    <component
      :is="headingTag"
      :id="headingId"
      class="evaluation-heading"
      tabindex="-1"
    >
      {{ SESSION_COPY.feedbackPane }}
    </component>
    <p class="evaluation-phase">{{ EVALUATION_PHASE_LABEL[evaluation.evaluationPhase] }}</p>

    <div class="evaluation-score">
      <p class="total-score">
        <span class="total-number">{{ evaluation.totalScore }}</span>
        <span class="total-label">总分</span>
      </p>
      <p class="level-word" :class="levelClass(evaluation.level)">
        {{ displayEvaluationLevel(evaluation.level) }}
      </p>
    </div>

    <ul class="score-row" aria-label="五维分数">
      <li v-for="dimension in SCORE_DIMENSIONS" :key="dimension.key">
        <span class="score-label">{{ dimension.label }}</span>
        <span class="score-value">{{ evaluation[dimension.key] }}</span>
      </li>
    </ul>

    <section v-if="evaluation.strengths.length" class="feedback-block is-success">
      <component :is="subheadingTag" class="feedback-title">
        <Check v-bind="ICON" />
        做得好的
      </component>
      <ul>
        <li v-for="(item, index) in evaluation.strengths" :key="`s-${index}`">{{ item }}</li>
      </ul>
    </section>

    <section v-if="evaluation.missingPoints.length" class="feedback-block is-warning">
      <component :is="subheadingTag" class="feedback-title">
        <TriangleAlert v-bind="ICON" />
        需要补充
      </component>
      <ul>
        <li v-for="(item, index) in evaluation.missingPoints" :key="`m-${index}`">{{ item }}</li>
      </ul>
    </section>

    <section v-if="evaluation.correction" class="feedback-block is-correction">
      <component :is="subheadingTag" class="feedback-title">参考修正</component>
      <p>{{ evaluation.correction }}</p>
    </section>
  </section>
</template>

<style scoped>
.evaluation-pane {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.evaluation-heading {
  font-size: 18px;
  font-weight: 700;
  outline: none;
}

.evaluation-heading:focus-visible {
  outline: var(--focus);
  outline-offset: 4px;
}

.evaluation-phase {
  font-size: 12px;
  color: var(--text-2);
}

.evaluation-score {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px 20px;
}

.total-number {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  color: var(--text);
}

.total-label {
  margin-left: 8px;
  font-size: 13px;
  color: var(--text-2);
}

.level-word {
  font-size: 16px;
  font-weight: 650;
}

.level-word.is-success {
  color: var(--success);
}

.level-word.is-warning {
  color: var(--warning);
}

.level-word.is-danger {
  color: var(--danger);
}

.score-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.score-row li {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.score-label {
  font-size: 12px;
  color: var(--text-2);
}

.score-value {
  font-size: 16px;
  font-weight: 650;
  color: var(--text);
}

.feedback-block {
  padding: 12px 14px;
  border-radius: var(--radius);
  min-width: 0;
}

.feedback-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 650;
}

.feedback-block ul,
.feedback-block p {
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.feedback-block li + li {
  margin-top: 6px;
}

.is-success {
  background: var(--success-soft);
  color: var(--success);
}

.is-warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.is-correction {
  background: var(--primary-soft);
  color: var(--text);
}

@media (max-width: 720px) {
  .score-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 12px;
    padding: 12px;
  }

  .score-label {
    font-size: 13px;
  }

  .score-value {
    font-size: 16px;
  }
}
</style>
