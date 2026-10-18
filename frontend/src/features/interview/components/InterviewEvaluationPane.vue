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
    <p class="evaluation-phase">{{ EVALUATION_PHASE_LABEL[evaluation.evaluationPhase] }}<span class="dimension-scale">五维单项满分 20 分</span></p>

    <div class="evaluation-score">
      <p class="total-score">
        <span class="total-number">{{ evaluation.totalScore }}</span>
        <span class="score-maximum">/ 100</span>
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
        <span class="score-track" aria-hidden="true"><span :style="{ width: `${Math.max(0, Math.min(100, evaluation[dimension.key] * 5))}%` }"></span></span>
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
  gap: 18px;
  min-width: 0;
}

.evaluation-heading {
  font-size: 20px;
  font-weight: 700;
  outline: none;
  letter-spacing: -.02em;
}

.evaluation-heading:focus-visible {
  outline: var(--focus);
  outline-offset: 4px;
}

.evaluation-phase {
  font-size: 11px;
  color: var(--text-2);
  margin-top: -12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
}

.dimension-scale {
  margin-left: auto;
}

.evaluation-score {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  padding: 18px 20px;
  background: var(--surface-subtle);
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

.total-number {
  font-family: var(--mono);
  font-size: 46px;
  font-weight: 600;
  letter-spacing: -.06em;
  line-height: 1;
  color: var(--text);
}

.score-maximum {
  margin-left: 8px;
  font-family: var(--mono);
  font-size: 13px;
  color: var(--text-2);
}

.total-label {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-2);
}

.level-word {
  margin-left: auto;
  font-size: 13px;
  font-weight: 650;
  border: 1px solid currentColor;
  padding: 4px 12px;
  border-radius: 999px;
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
  gap: 16px;
}

.score-row li {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.score-label {
  font-size: 11px;
  color: var(--text-2);
}

.score-value {
  font-family: var(--mono);
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
}

.score-track {
  display: block;
  height: 4px;
  background: var(--border);
  border-radius: 4px;
  overflow: hidden;
}

.score-track > span {
  display: block;
  height: 100%;
  background: var(--primary);
  border-radius: inherit;
}

.feedback-block {
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  min-width: 0;
  background: var(--surface);
}

.feedback-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 650;
}

.feedback-block ul, .feedback-block p {
  font-size: 13px;
  line-height: 1.85;
  overflow-wrap: anywhere;
  color: var(--text);
}

.feedback-block li {
  position: relative;
  padding-left: 12px;
}

.feedback-block li::before {
  content: '';
  width: 3px;
  height: 3px;
  background: var(--text-2);
  border-radius: 50%;
  position: absolute;
  left: 0;
  top: .85em;
}

.feedback-block li + li {
  margin-top: 6px;
}

.feedback-block.is-success {
  border-left: 3px solid var(--success);
}

.is-success .feedback-title {
  color: var(--success);
}

.feedback-block.is-warning {
  border-left: 3px solid var(--warning);
}

.is-warning .feedback-title {
  color: var(--warning);
}

.is-correction {
  background: var(--primary-soft);
  border-color: var(--primary-border);
}

.is-correction .feedback-title {
  color: var(--primary);
}

@media (max-width: 720px) {
  .score-row {
    grid-template-columns: repeat(5, minmax(0,1fr));
    gap: 10px;
  }

  .total-number {
    font-size: 40px;
  }

  .evaluation-score {
    padding: 16px;
  }

  .feedback-block {
    padding: 14px;
  }

}
</style>
