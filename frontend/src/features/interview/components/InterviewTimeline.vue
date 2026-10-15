<script setup lang="ts">
import { reactive } from 'vue'

import InterviewTimelineItem from '@/features/interview/components/InterviewTimelineItem.vue'
import type { InterviewDetailQuestionResponse } from '@/types/interview'

const props = defineProps<{
  items: InterviewDetailQuestionResponse[]
  collapsible?: boolean
}>()

const expanded = reactive<Record<number, boolean>>({})

function isExpanded(item: InterviewDetailQuestionResponse): boolean {
  if (!props.collapsible) {
    return true
  }
  if (expanded[item.interviewQuestionId] === undefined) {
    return true
  }
  return expanded[item.interviewQuestionId]
}

function toggle(item: InterviewDetailQuestionResponse): void {
  expanded[item.interviewQuestionId] = !isExpanded(item)
}
</script>

<template>
  <div class="timeline">
    <InterviewTimelineItem
      v-for="item in items"
      :key="item.interviewQuestionId"
      :item="item"
      :collapsible="collapsible"
      :expanded="isExpanded(item)"
      @toggle="toggle(item)"
    />
  </div>
</template>
