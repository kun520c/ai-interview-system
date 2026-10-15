<script setup lang="ts">
import { computed, onMounted } from 'vue'

import BaseButton from '@/components/ui/BaseButton.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import SessionSplitWorkspace from '@/components/shell/SessionSplitWorkspace.vue'
import InterviewAnswerPane from '@/features/interview/components/InterviewAnswerPane.vue'
import InterviewEvaluationPane from '@/features/interview/components/InterviewEvaluationPane.vue'
import InterviewQuestionPane from '@/features/interview/components/InterviewQuestionPane.vue'
import InterviewSessionDock from '@/features/interview/components/InterviewSessionDock.vue'
import { useInterviewSession } from '@/features/interview/composables/useInterviewSession'
import { SESSION_COPY } from '@/features/interview/copy/sessionCopy'
import { SessionPhase } from '@/features/interview/types/sessionPhase'

const session = useInterviewSession()

const answerLabel = computed(() => {
  if (session.phase.value === SessionPhase.EVALUATION_GATE) {
    return SESSION_COPY.feedbackPane
  }
  return SESSION_COPY.answerPane
})

const showAnswerPane = computed(() => {
  return (
    session.phase.value === SessionPhase.ANSWERING ||
    session.phase.value === SessionPhase.SUBMITTING ||
    session.phase.value === SessionPhase.EVALUATION_PENDING ||
    session.phase.value === SessionPhase.RECOVERY_BLOCKED
  )
})

const submittedOnLeft = computed(() => session.phase.value === SessionPhase.EVALUATION_GATE)

const describedBy = computed(() => {
  const ids: string[] = []
  if (session.inlineError.value) {
    ids.push('answer-inline-error')
  }
  if (session.answerLocked.value) {
    ids.push('answer-lock-hint')
  }
  return ids.length ? ids.join(' ') : undefined
})

onMounted(() => {
  void session.bootstrap()
})
</script>

<template>
  <section class="interview-session" :aria-busy="session.phase.value === SessionPhase.BOOTSTRAP || session.loading.value">
    <div v-if="session.phase.value === SessionPhase.BOOTSTRAP || (session.loading.value && !session.loaded.value)" class="session-skeleton" aria-busy="true" aria-label="正在加载当前面试">
      <span class="skeleton-line is-title"></span>
      <span class="skeleton-line"></span>
      <span class="skeleton-line is-short"></span>
    </div>

    <template v-else>
      <StatusNotice v-if="session.resumeNotice.value" tone="info">
        {{ SESSION_COPY.resumeNotice }}
      </StatusNotice>
      <StatusNotice v-if="session.banner.value" tone="info">{{ session.banner.value }}</StatusNotice>
      <StatusNotice
        v-if="session.alertMessage.value"
        :tone="session.phase.value === SessionPhase.EVALUATION_PENDING ? 'warning' : 'danger'"
        :role="session.phase.value === SessionPhase.EVALUATION_PENDING ? 'status' : 'alert'"
      >
        <p>{{ session.alertMessage.value }}</p>
        <p v-if="session.phase.value === SessionPhase.RECOVERY_BLOCKED" class="notice-copy">
          {{ SESSION_COPY.recoveryBlockedHint }}
        </p>
        <p v-else-if="session.phase.value === SessionPhase.EVALUATION_PENDING" class="notice-copy">
          {{ SESSION_COPY.pendingHint }}
        </p>
        <p v-else-if="session.alertMessage.value === SESSION_COPY.recoveringQuestion" class="notice-copy">
          {{ SESSION_COPY.recoveringQuestionHint }}
        </p>
        <BaseButton
          v-if="session.alertMessage.value === SESSION_COPY.recoveringQuestion || session.phase.value === SessionPhase.ERROR"
          variant="ghost"
          @click="session.retryBootstrap()"
        >
          {{ SESSION_COPY.retryCurrent }}
        </BaseButton>
      </StatusNotice>
      <StatusNotice v-if="session.inlineError.value" id="answer-inline-error" tone="danger" role="alert">
        {{ session.inlineError.value }}
      </StatusNotice>
      <p
        v-if="session.phase.value === SessionPhase.SUBMITTING"
        class="submit-status"
        role="status"
      >
        {{ SESSION_COPY.submittingHint }}
      </p>
      <p
        v-if="session.phase.value === SessionPhase.EVALUATION_PENDING"
        class="submit-status"
        role="status"
      >
        {{ SESSION_COPY.pendingTitle }}
      </p>

      <SessionSplitWorkspace
        v-if="session.workspaceQuestion.value || session.gate.value"
        :question-label="SESSION_COPY.questionPane"
        :answer-label="answerLabel"
      >
        <template #question>
          <InterviewQuestionPane
            :session="session.session.value"
            :question="session.workspaceQuestion.value"
            :submitted-answer="submittedOnLeft ? session.submittedAnswer.value : undefined"
          />
        </template>
        <template #answer>
          <InterviewEvaluationPane
            v-if="session.phase.value === SessionPhase.EVALUATION_GATE && session.gate.value"
            :evaluation="session.gate.value.evaluation"
          />
          <InterviewAnswerPane
            v-else-if="showAnswerPane"
            :draft="session.draft.value"
            :locked="session.answerLocked.value"
            :invalid="Boolean(session.inlineError.value)"
            :described-by="describedBy"
            @update:draft="session.setDraft"
          />
        </template>
        <template #dock>
          <InterviewSessionDock
            :phase="session.phase.value"
            :can-submit="session.canSubmit.value"
            :submit-locked="session.submitLocked.value"
            :checking-current="session.checkingCurrent.value"
            :next-action="session.gate.value?.nextAction"
            @submit="session.submitAnswer()"
            @retry="session.retrySamePair()"
            @check="session.checkCurrentStatus()"
            @advance="session.continueAfterEvaluation()"
            @refresh="session.checkCurrentStatus()"
          />
        </template>
      </SessionSplitWorkspace>
    </template>
  </section>
</template>

<style scoped>
.interview-session {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-width: 0;
  min-height: 0;
}

.interview-session :deep(.session-workspace) {
  flex: 1;
  min-height: 0;
}

.session-skeleton {
  padding: 24px;
}

.skeleton-line {
  display: block;
  height: 14px;
  margin-bottom: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-line.is-title {
  height: 24px;
  width: 40%;
}

.skeleton-line.is-short {
  width: 28%;
}

.submit-status {
  font-size: 14px;
  color: var(--text-2);
}

.notice-copy {
  margin: 8px 0 12px;
  font-size: 14px;
}

@media (prefers-reduced-motion: no-preference) {
  .skeleton-line {
    animation: pulse 1.6s ease-in-out infinite;
  }
}

@keyframes pulse {
  50% {
    opacity: 0.65;
  }
}
</style>
