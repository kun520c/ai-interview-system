<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { ErrorKind, isApiError } from '@/api/apiError'
import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import BaseButton from '@/components/ui/BaseButton.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import DifficultyCard from '@/features/interview/components/DifficultyCard.vue'
import InterviewProgress from '@/features/interview/components/InterviewProgress.vue'
import { useCurrentInterview } from '@/features/interview/composables/useCurrentInterview'
import { createInterview } from '@/features/interview/api/interviewApi'
import { CREATE_INTERVIEW_FLOW } from '@/features/interview/copy/interviewFlow'
import { displayCategory } from '@/features/interview/copy/categoryDisplay'
import { PEER_DIFFICULTIES } from '@/features/interview/copy/interviewPlan'
import {
  armInterviewResumeNotice,
  interviewResumeNoticeState,
} from '@/features/interview/navigation/sessionNavigationContext'
import { isUncertainMutationFailure } from '@/features/interview/utils/uncertainMutation'
import type { QuestionDifficulty as Difficulty } from '@/types/enums'

interface CreateOperationScope {
  userId: number
  generation: number
  viewGeneration: number
}

const router = useRouter()
const auth = useAuthStore()
const {
  session,
  loaded,
  loading,
  error,
  refresh,
  adoptCreatedSession,
} = useCurrentInterview()
let mounted = true
let viewGeneration = 0

const selectedDifficulty = ref<Difficulty | null>(null)
const submitting = ref(false)
const verifying = ref(false)
const validationError = ref<string | null>(null)
const uncertainNoSession = ref(false)
const verificationFailed = ref(false)

const currentBusy = computed(() => (loading.value && !loaded.value) || (!loaded.value && !error.value && !verificationFailed.value && !verifying.value))
const pageError = computed(() => Boolean(error.value) && !session.value && !verifying.value && !verificationFailed.value)
const showDifficultyCards = computed(
  () =>
    loaded.value &&
    !session.value &&
    !error.value &&
    !verifying.value &&
    !verificationFailed.value,
)
const showResume = computed(() => Boolean(session.value) && !verifying.value && !submitting.value)
const startDisabled = computed(
  () => !selectedDifficulty.value || submitting.value || verifying.value,
)

function captureScope(): CreateOperationScope | null {
  const userId = auth.userId
  if (userId === null) {
    return null
  }
  return {
    userId,
    generation: getSessionGeneration(),
    viewGeneration,
  }
}

function isLiveScope(scope: CreateOperationScope | null | undefined): scope is CreateOperationScope {
  return (
    mounted &&
    scope != null &&
    scope.viewGeneration === viewGeneration &&
    isCurrentIdentityScope(scope.userId, scope.generation, auth.userId)
  )
}

onMounted(() => {
  void refresh().catch(() => {
    // Region-local error is stored on the current-interview owner.
  })
})

onUnmounted(() => {
  mounted = false
  viewGeneration += 1
})

function selectDifficulty(difficulty: Difficulty): void {
  if (submitting.value || verifying.value) {
    return
  }
  selectedDifficulty.value = difficulty
  validationError.value = null
  uncertainNoSession.value = false
}

async function startInterview(): Promise<void> {
  if (!selectedDifficulty.value || submitting.value || verifying.value || session.value) {
    return
  }
  const scope = captureScope()
  if (!scope) {
    return
  }
  submitting.value = true
  validationError.value = null
  uncertainNoSession.value = false
  verificationFailed.value = false
  try {
    const created = await createInterview({ difficulty: selectedDifficulty.value })
    if (!isLiveScope(scope)) {
      return
    }
    if (created.difficulty !== selectedDifficulty.value) {
      armInterviewResumeNotice(scope.userId)
    }
    adoptCreatedSession(created)
    await router.push({
      name: 'interview-session',
      state:
        created.difficulty !== selectedDifficulty.value
          ? interviewResumeNoticeState(scope.userId)
          : {},
    })
  } catch (caught) {
    if (!isLiveScope(scope)) {
      return
    }
    if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
      return
    }
    if (isApiError(caught) && caught.kind === ErrorKind.VALIDATION) {
      validationError.value = caught.message
      return
    }
    if (isUncertainMutationFailure(caught)) {
      await verifyAfterUncertainPost(scope)
      return
    }
    if (isApiError(caught)) {
      validationError.value = caught.message
    }
    return
  } finally {
    if (mounted) {
      submitting.value = false
    }
  }
}

async function verifyAfterUncertainPost(scope: CreateOperationScope | null = captureScope()): Promise<void> {
  if (!scope) {
    return
  }
  verifying.value = true
  verificationFailed.value = false
  try {
    const current = await refresh()
    if (!isLiveScope(scope)) {
      return
    }
    if (current) {
      armInterviewResumeNotice(scope.userId)
      verifying.value = false
      await router.push({
        name: 'interview-session',
        state: interviewResumeNoticeState(scope.userId),
      })
      return
    }
    uncertainNoSession.value = true
  } catch {
    if (!isLiveScope(scope)) {
      return
    }
    verificationFailed.value = true
  } finally {
    if (mounted) {
      verifying.value = false
    }
  }
}

async function retryCheck(): Promise<void> {
  if (verifying.value) {
    return
  }
  await verifyAfterUncertainPost()
}

async function retryCurrent(): Promise<void> {
  try {
    await refresh()
  } catch {
    // Keep region error.
  }
}
</script>

<template>
  <section class="create-page">
    <header class="create-header">
      <h1>开始面试</h1>
      <p class="u-muted">选择难度后开始一场连续模拟面试。同一时间只能有一场进行中的面试。</p>
    </header>

    <div
      v-if="currentBusy && !verifying"
      class="create-skeleton"
      aria-busy="true"
      aria-label="正在确认当前面试状态"
    >
      <p class="u-muted">正在确认当前面试状态</p>
      <span class="skeleton-line is-title"></span>
      <span class="skeleton-line"></span>
      <div class="skeleton-cards">
        <span class="skeleton-card"></span>
        <span class="skeleton-card"></span>
        <span class="skeleton-card"></span>
      </div>
    </div>

    <StatusNotice v-else-if="pageError" tone="danger">
      <p>当前面试状态暂时无法加载</p>
      <p class="notice-copy">确认是否已有进行中的面试后再开始。</p>
      <BaseButton variant="ghost" @click="retryCurrent">重试</BaseButton>
    </StatusNotice>

    <div v-else-if="verifying" class="create-verifying" aria-busy="true" role="status">
      <h2>正在确认面试是否已创建</h2>
      <p class="u-muted">不会再次提交开始请求。</p>
    </div>

    <div v-else-if="verificationFailed" class="create-uncertain" role="alert">
      <h2>无法确认面试是否已创建</h2>
      <p class="u-muted">网络或服务器状态不明确。请先检查当前面试，不要重复创建。</p>
      <div class="uncertain-actions">
        <BaseButton @click="retryCheck">重新检查</BaseButton>
        <RouterLink class="back-link" :to="{ name: 'dashboard' }">返回工作台</RouterLink>
      </div>
    </div>

    <div v-else-if="showResume && session" class="resume-layout">
      <section class="resume-facts" aria-labelledby="resume-title">
        <h2 id="resume-title">你有一场未完成的面试</h2>
        <InterviewProgress :session="session" />
        <dl v-if="session.currentQuestion" class="resume-dl">
          <div>
            <dt>当前领域</dt>
            <dd>{{ displayCategory(session.currentQuestion.category) }}</dd>
          </div>
          <div>
            <dt>当前知识点</dt>
            <dd>{{ session.currentQuestion.knowledgePoint }}</dd>
          </div>
        </dl>
        <RouterLink class="resume-cta" :to="{ name: 'interview-session' }">
          继续当前面试
        </RouterLink>
      </section>
      <aside class="resume-explain" aria-labelledby="resume-why-title">
        <h2 id="resume-why-title">为什么不能开始第二场？</h2>
        <p>
          当前系统同一用户只保留一场 active interview。完成当前面试后即可开始新的训练。
        </p>
      </aside>
    </div>

    <form v-else-if="showDifficultyCards" class="create-form" @submit.prevent="startInterview">
      <StatusNotice v-if="uncertainNoSession" tone="warning">
        未检测到进行中的面试，可以重新尝试开始。
      </StatusNotice>
      <StatusNotice v-if="validationError" tone="danger">{{ validationError }}</StatusNotice>

      <fieldset class="difficulty-fieldset" :disabled="submitting">
        <legend>选择难度</legend>
        <div class="difficulty-grid" role="radiogroup" aria-label="选择面试难度">
          <DifficultyCard
            v-for="difficulty in PEER_DIFFICULTIES"
            :key="difficulty"
            :difficulty="difficulty"
            :selected="selectedDifficulty === difficulty"
            :disabled="submitting"
            @select="selectDifficulty"
          />
        </div>
      </fieldset>

      <BaseButton
        class="start-button"
        type="submit"
        :disabled="startDisabled"
        :loading="submitting"
      >
        {{ submitting ? '正在准备本场题目' : '开始面试' }}
      </BaseButton>

      <section class="create-flow" aria-labelledby="create-flow-title">
        <h2 id="create-flow-title">接下来会发生什么</h2>
        <ol>
          <li v-for="item in CREATE_INTERVIEW_FLOW" :key="item.step">
            <span class="flow-step">{{ item.step }}</span>
            <span>{{ item.label }}</span>
          </li>
        </ol>
      </section>
    </form>
  </section>
</template>

<style scoped>
.create-page {
  display: grid;
  gap: 24px;
  min-width: 0;
}

.create-header h1 {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
}

.create-header p {
  margin-top: 8px;
  font-size: 14px;
}

.create-skeleton, .create-verifying, .create-uncertain, .resume-facts, .resume-explain {
  min-width: 0;
}

.create-skeleton, .resume-facts, .create-verifying, .create-uncertain {
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.resume-layout {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: 24px;
  align-items: start;
}

.resume-facts h2, .create-verifying h2, .create-uncertain h2 {
  margin-bottom: 16px;
  font-size: 20px;
  font-weight: 650;
}

.resume-dl {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.resume-dl dt {
  font-size: 12px;
  color: var(--text-2);
}

.resume-dl dd {
  font-size: 14px;
}

.resume-cta, .start-button {
  margin-top: 24px;
}

.resume-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 8px 16px;
  border-radius: var(--radius);
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.resume-cta:hover {
  background: var(--primary-hover);
}

.resume-explain {
  padding: 20px;
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.resume-explain h2 {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 650;
}

.resume-explain p, .notice-copy, .create-verifying p, .create-uncertain p {
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.6;
}

.notice-copy {
  margin: 8px 0 12px;
}

.uncertain-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  margin-top: 16px;
}

.back-link {
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
}

.back-link:hover {
  color: var(--text);
}

.create-form {
  display: grid;
  gap: 24px;
}

.difficulty-fieldset {
  min-width: 0;
  border: 0;
  padding: 0;
}

.difficulty-fieldset legend {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 650;
}

.difficulty-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.start-button {
  justify-self: start;
  min-width: 160px;
}

.create-flow {
  padding: 16px 20px;
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.create-flow h2 {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 650;
}

.create-flow ol {
  display: grid;
  gap: 8px;
}

.create-flow li {
  display: flex;
  gap: 12px;
  align-items: baseline;
  font-size: 14px;
}

.flow-step {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--text-2);
  min-width: 1.6em;
}

.skeleton-line, .skeleton-card {
  display: block;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-line {
  height: 14px;
  margin-bottom: 12px;
}

.skeleton-line.is-title {
  height: 24px;
  width: 40%;
}

.skeleton-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.skeleton-card {
  height: 180px;
}

@media (prefers-reduced-motion: no-preference) {
  .skeleton-line, .skeleton-card {
    animation: pulse 1.6s ease-in-out infinite;
  }

}

@keyframes pulse {
  50% {
    opacity: 0.65;
  }

}

@media (max-width: 1024px) {
  .resume-layout {
    grid-template-columns: minmax(0, 1fr);
  }

}

@media (max-width: 720px) {
  .difficulty-grid, .skeleton-cards {
    grid-template-columns: minmax(0, 1fr);
  }

}

.create-header h1 {
  font-size: 28px;
  letter-spacing: -.035em;
}

.difficulty-fieldset legend {
  margin-bottom: 20px;
  font-size: 14px;
}

.start-button {
  margin-top: 0;
}

.create-flow {
  margin-top: 8px;
  background: transparent;
  border: 1px solid var(--border);
  padding: 22px;
}

.create-flow ol {
  grid-template-columns: repeat(3,minmax(0,1fr));
  gap: 20px;
}

.create-flow li {
  font-size: 12px;
}

.resume-facts {
  box-shadow: var(--shadow-panel);
  border-color: var(--primary-border);
}

@media (max-width:720px) {
  .create-flow ol {
    grid-template-columns: minmax(0,1fr);
    gap: 12px;
  }

  .create-header h1 {
    font-size: 25px;
  }

  .start-button {
    width: 100%;
  }

}
</style>
