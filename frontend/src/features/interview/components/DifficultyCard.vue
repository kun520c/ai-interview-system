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
    <span class="difficulty-title">{{ difficulty }}</span>
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
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 20px 16px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  cursor: pointer;
  transition: border-color var(--move), background var(--move);
}

.difficulty-card:hover:not(.is-disabled) {
  border-color: var(--primary-border);
}

.difficulty-card.is-selected {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.difficulty-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.difficulty-card:has(input:focus-visible) {
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
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--primary);
}

.is-selected .difficulty-check {
  border-color: var(--primary);
  background: var(--primary);
  color: #fff;
}

.difficulty-kicker {
  font-size: 13px;
  color: var(--text-2);
}

.difficulty-title {
  font-size: 20px;
  font-weight: 650;
  line-height: 1.3;
  color: var(--text);
}

.difficulty-helper {
  font-size: 13px;
  color: var(--text-2);
}

.difficulty-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.difficulty-categories li {
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--surface-subtle);
  color: var(--text-2);
  font-size: 12px;
  line-height: 1.4;
}

.is-selected .difficulty-categories li {
  background: var(--surface);
}
</style>
