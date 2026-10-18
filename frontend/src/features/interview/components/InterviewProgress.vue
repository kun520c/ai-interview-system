<script setup lang="ts">
import { computed } from 'vue'

import { displayCategory } from '@/features/interview/copy/categoryDisplay'
import {
  currentMainNumber,
  isFollowUpQuestion,
  progressLabel,
} from '@/features/interview/utils/interviewProgress'
import type { InterviewSessionResponse } from '@/types/interview'

const props = defineProps<{
  session: InterviewSessionResponse
}>()

const mainNumber = computed(() => currentMainNumber(props.session))
const label = computed(() => progressLabel(props.session))
const followUp = computed(() => isFollowUpQuestion(props.session))
const categoryLabel = computed(() => {
  const category = props.session.currentQuestion?.category
  return category ? displayCategory(category) : null
})
const knowledgePoint = computed(() => props.session.currentQuestion?.knowledgePoint ?? null)
const progressMax = computed(() => Math.max(props.session.plannedQuestionCount, 0))
const progressNow = computed(() => Math.max(props.session.completedQuestionCount, 0))
const progressWidth = computed(() => {
  if (progressMax.value <= 0) {
    return '0%'
  }
  return `${Math.min(100, (progressNow.value / progressMax.value) * 100)}%`
})
</script>

<template>
  <div class="interview-progress">
    <p class="progress-meta">
      <span class="progress-difficulty">{{ session.difficulty }}</span>
      <span>{{ label }}</span>
    </p>
    <div
      class="progress-bar"
      role="progressbar"
      :aria-valuenow="progressNow"
      :aria-valuemin="0"
      :aria-valuemax="progressMax"
      :aria-label="`本场进度 ${label}`"
    >
      <span class="progress-bar-fill" :style="{ width: progressWidth }"></span>
    </div>
    <p v-if="categoryLabel || knowledgePoint" class="progress-context">
      <span v-if="categoryLabel">{{ categoryLabel }}</span>
      <span v-if="knowledgePoint">{{ knowledgePoint }}</span>
      <span v-if="followUp" class="progress-follow-up">追问</span>
    </p>
    <p class="u-sr-only">当前主问题第 {{ mainNumber }} 题，共 {{ session.plannedQuestionCount }} 题</p>
  </div>
</template>

<style scoped>
.interview-progress {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.progress-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  font-size: 13px;
  color: var(--text-2);
}

.progress-difficulty {
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--primary);
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: var(--primary-soft);
}

.progress-bar {
  height: 5px;
  border-radius: 999px;
  background: var(--border);
  overflow: hidden;
}

.progress-bar-fill {
  display: block;
  height: 100%;
  background: var(--primary);
  border-radius: inherit;
}

.progress-context {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
  color: var(--text-2);
  overflow-wrap: anywhere;
}

.progress-context > span {
  min-width: 0;
  max-width: 100%;
}

.progress-follow-up {
  color: var(--primary);
  font-weight: 600;
}
</style>
