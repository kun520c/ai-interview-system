<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@lucide/vue'

import { displayCategory } from '@/features/interview/copy/categoryDisplay'
import { INTERVIEW_PLANS } from '@/features/interview/copy/interviewPlan'
import type { QuestionDifficulty } from '@/types/enums'

const props = defineProps<{
  difficulty: QuestionDifficulty
  selected: boolean
  disabled?: boolean
  name?: string
}>()

const emit = defineEmits<{
  select: [difficulty: QuestionDifficulty]
}>()

const plan = computed(() => INTERVIEW_PLANS[props.difficulty])
const difficultyNames = { EASY: '基础巩固', MEDIUM: '综合进阶', HARD: '深度挑战' } as const
const ICON = { size: 16, strokeWidth: 1.7, 'aria-hidden': true }

function onChange(): void {
  if (!props.disabled) {
    emit('select', props.difficulty)
  }
}
</script>

<template>
  <label
    class="difficulty-card"
    :class="{ 'is-selected': selected, 'is-disabled': disabled }"
  >
    <input
      class="difficulty-input"
      type="radio"
      :name="name ?? 'interview-difficulty'"
      :value="difficulty"
      :checked="selected"
      :disabled="disabled"
      :aria-checked="selected"
      @change="onChange"
    />
    <span class="difficulty-check" aria-hidden="true">
      <Check v-if="selected" v-bind="ICON" />
    </span>
    <span class="difficulty-kicker">{{ plan.mainQuestionCount }} 道主问题</span>
    <span class="difficulty-title">{{ difficultyNames[difficulty] }}</span>
    <span class="difficulty-enum mono">{{ difficulty }}</span>
    <span class="difficulty-helper">{{ plan.helper }}</span>
    <ul class="difficulty-categories">
      <li v-for="category in plan.categories" :key="category">
        {{ displayCategory(category) }}
      </li>
    </ul>
  </label>
</template>

<style scoped>
.difficulty-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 26px 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: border-color var(--move), background var(--move), box-shadow var(--move);
}

.difficulty-card:hover:not(.is-disabled) {
  border-color: var(--primary-border);
  box-shadow: var(--shadow-panel);
}

.difficulty-card.is-selected {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px var(--primary);
}

.difficulty-card.is-disabled {
  cursor: not-allowed;
  opacity: .55;
}

.difficulty-card:has(input:focus-visible) {
  outline: var(--focus);
  outline-offset: 3px;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-soft);
}

.difficulty-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  cursor: inherit;
}

.difficulty-check {
  position: absolute;
  top: 22px;
  right: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--border-strong);
  background: var(--surface);
  color: var(--primary);
}

.is-selected .difficulty-check {
  border-color: var(--primary);
  background: var(--primary);
  color: #fff;
}

.difficulty-kicker {
  font-size: 12px;
  color: var(--text-2);
  margin-right: 24px;
}

.difficulty-title {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--text);
  margin-top: 10px;
}

.difficulty-enum {
  font-size: 10px;
  letter-spacing: .1em;
  color: var(--primary);
}

.difficulty-helper {
  font-size: 12px;
  color: var(--text-2);
  margin-block: 4px 10px;
}

.difficulty-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.difficulty-categories li {
  padding: 4px 7px;
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
  color: var(--text-2);
  font-size: 11px;
  line-height: 1.4;
}

.is-selected .difficulty-categories li {
  background: var(--surface);
}

@media (max-width: 1024px) and (min-width: 721px) {
  .difficulty-card {
    padding: 22px 16px;
  }

  .difficulty-title {
    font-size: 19px;
  }

}
</style>
