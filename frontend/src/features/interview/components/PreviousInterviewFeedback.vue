<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'

import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import BaseButton from '@/components/ui/BaseButton.vue'
import { fetchInterviewDetail } from '@/features/interview/api/interviewApi'
import InterviewEvaluationPane from '@/features/interview/components/InterviewEvaluationPane.vue'
import { isInterviewEvaluationResponse } from '@/features/interview/storage/evaluationGateStorage'
import { InterviewAnswerStatus, InterviewSessionStatus } from '@/types/enums'
import type { InterviewDetailQuestionResponse, InterviewDetailResponse } from '@/types/interview'

const props = defineProps<{ sessionId: number; currentQuestionId: number }>()
const auth = useAuthStore()
const open = ref(false)
const loading = ref(false)
const failed = ref(false)
const previous = ref<InterviewDetailQuestionResponse | null>(null)
let requestVersion = 0
let mounted = true

function reset() {
  requestVersion += 1
  open.value = false
  loading.value = false
  failed.value = false
  previous.value = null
}

watch(() => [props.sessionId, props.currentQuestionId, auth.userId], reset, { flush: 'sync' })
onUnmounted(() => { mounted = false; reset() })

function previousFrom(detail: InterviewDetailResponse): InterviewDetailQuestionResponse | null {
  if (detail.sessionId !== props.sessionId || detail.status !== InterviewSessionStatus.IN_PROGRESS || !Array.isArray(detail.questions)) {
    throw new Error('Interview state changed')
  }
  const current = detail.questions.filter(item => item.interviewQuestionId === props.currentQuestionId)
  if (current.length !== 1 || !Number.isInteger(current[0]!.displayOrder)) {
    throw new Error('Current question changed')
  }
  return detail.questions
    .filter(item => Number.isInteger(item.displayOrder) && item.displayOrder < current[0]!.displayOrder
      && item.answer?.answerStatus === InterviewAnswerStatus.EVALUATED
      && typeof item.answer.answerContent === 'string' && isInterviewEvaluationResponse(item.evaluation))
    .sort((left, right) => right.displayOrder - left.displayOrder || right.interviewQuestionId - left.interviewQuestionId)[0] ?? null
}

async function load() {
  const userId = auth.userId
  if (userId === null) return
  const version = ++requestVersion
  const generation = getSessionGeneration()
  const sessionId = props.sessionId
  const questionId = props.currentQuestionId
  loading.value = true
  failed.value = false
  previous.value = null
  const stillCurrent = () => mounted && open.value && version === requestVersion
    && props.sessionId === sessionId && props.currentQuestionId === questionId
    && isCurrentIdentityScope(userId, generation, auth.userId)
  try {
    const detail = await fetchInterviewDetail(sessionId)
    if (stillCurrent()) previous.value = previousFrom(detail)
  } catch {
    if (stillCurrent()) failed.value = true
  } finally {
    if (stillCurrent()) loading.value = false
  }
}

function toggle() {
  if (open.value) {
    reset()
    return
  }
  open.value = true
  void load()
}
</script>

<template>
  <div class="previous-feedback">
    <BaseButton
      variant="ghost"
      data-testid="previous-feedback-toggle"
      :aria-expanded="open"
      aria-controls="previous-feedback-panel"
      @click="toggle"
    >
      {{ open ? '收起上一题反馈' : '查看上一题反馈' }}
    </BaseButton>
    <section v-if="open" id="previous-feedback-panel" class="previous-feedback-panel" aria-label="上一题反馈">
      <p v-if="loading" role="status">正在加载上一题反馈</p>
      <template v-else-if="failed">
        <p role="alert">上一题反馈暂时无法加载，请重试。</p>
        <BaseButton variant="ghost" @click="load">重试</BaseButton>
      </template>
      <p v-else-if="!previous">暂无上一题反馈。</p>
      <template v-else>
        <h2>上一题</h2>
        <p class="previous-question">{{ previous.questionContent }}</p>
        <h3>你的回答</h3>
        <pre>{{ previous.answer?.answerContent }}</pre>
        <InterviewEvaluationPane
          v-if="previous.evaluation"
          :evaluation="previous.evaluation"
          heading-tag="h3"
          heading-id="previous-evaluation-heading"
        />
      </template>
    </section>
  </div>
</template>

<style scoped>
.previous-feedback {
  margin-bottom: 16px;
}

.previous-feedback-panel {
  display: grid;
  gap: 12px;
  margin-top: 8px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.previous-feedback-panel h2, .previous-feedback-panel h3 {
  margin: 0;
  font-size: 15px;
}

.previous-question, .previous-feedback-panel pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
}
</style>
