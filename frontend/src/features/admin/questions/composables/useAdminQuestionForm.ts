import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErrorKind, isApiError, type ApiError } from '@/api/apiError'
import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import {
  createAdminQuestion,
  fetchAdminQuestion,
  updateAdminQuestion,
} from '@/features/admin/questions/api/questionAdminApi'
import { QUESTION_ADMIN_COPY } from '@/features/admin/questions/copy/questionAdminCopy'
import {
  emptyScoringPoint,
  scoringWeightSum,
  validateKnowledgePoint,
  validateQuestionText,
  validateScoringPoints,
} from '@/features/admin/questions/utils/questionFormRules'
import { isUncertainMutationFailure } from '@/features/interview/utils/uncertainMutation'
import { parsePositiveSessionId } from '@/utils/parsePositiveSessionId'
import { QuestionCategory, QuestionDifficulty } from '@/types/enums'
import type { CreateQuestionRequest, ScoringPointRequest } from '@/types/question'

export const AdminQuestionFormMode = {
  CREATE: 'CREATE',
  EDIT: 'EDIT',
} as const

export type AdminQuestionFormMode = (typeof AdminQuestionFormMode)[keyof typeof AdminQuestionFormMode]

export const AdminQuestionFormState = {
  INVALID_ID: 'INVALID_ID',
  LOADING: 'LOADING',
  READY: 'READY',
  NOT_FOUND: 'NOT_FOUND',
  ERROR: 'ERROR',
} as const

export type AdminQuestionFormState =
  (typeof AdminQuestionFormState)[keyof typeof AdminQuestionFormState]

function blankRequest(): CreateQuestionRequest {
  return {
    category: QuestionCategory.JAVA_BASIC,
    knowledgePoint: '',
    difficulty: QuestionDifficulty.MEDIUM,
    questionContent: '',
    referenceAnswer: '',
    scoringPoints: [emptyScoringPoint()],
  }
}

export function useAdminQuestionForm() {
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()
  const mode = computed(() =>
    route.name === 'admin-question-edit' ? AdminQuestionFormMode.EDIT : AdminQuestionFormMode.CREATE,
  )
  const questionId = computed(() => parsePositiveSessionId(route.params.questionId))
  const pageState = ref<AdminQuestionFormState>(AdminQuestionFormState.READY)
  const error = ref<ApiError | null>(null)
  const form = ref<CreateQuestionRequest>(blankRequest())
  const fieldErrors = ref<Record<string, string>>({})
  const submitting = ref(false)
  const mutationUncertain = ref(false)
  const formMessage = ref<string | null>(null)
  let epoch = 0
  let mutationEpoch = 0
  let mounted = true

  const weightSum = computed(() => scoringWeightSum(form.value.scoringPoints))
  const weightValid = computed(() => weightSum.value === 100)
  const canRemovePoint = computed(() => form.value.scoringPoints.length > 1)
  const canSubmit = computed(
    () => !submitting.value && weightValid.value && form.value.scoringPoints.length > 0,
  )

  function isCurrentMutationScope(operation: {
    mutationEpoch: number
    mode: AdminQuestionFormMode
    questionId: number | null
    routeName: string | symbol | null | undefined
    userId: number | null
    generation: number
  }): boolean {
    return (
      mounted &&
      operation.mutationEpoch === mutationEpoch &&
      operation.mode === mode.value &&
      operation.questionId === questionId.value &&
      operation.routeName === route.name &&
      isCurrentIdentityScope(operation.userId, operation.generation, auth.userId)
    )
  }

  function buildRequest(): CreateQuestionRequest {
    return {
      category: form.value.category,
      knowledgePoint: form.value.knowledgePoint.trim(),
      difficulty: form.value.difficulty,
      questionContent: form.value.questionContent,
      referenceAnswer: form.value.referenceAnswer,
      scoringPoints: form.value.scoringPoints.map((point) => ({
        pointType: point.pointType,
        content: point.content,
        weight: point.weight,
      })),
    }
  }

  function validate(): boolean {
    const next: Record<string, string> = {}
    const knowledgeError = validateKnowledgePoint(form.value.knowledgePoint)
    if (knowledgeError) {
      next.knowledgePoint = knowledgeError
    }
    const contentError = validateQuestionText(form.value.questionContent, '题目内容')
    if (contentError) {
      next.questionContent = contentError
    }
    const referenceError = validateQuestionText(form.value.referenceAnswer, '参考答案')
    if (referenceError) {
      next.referenceAnswer = referenceError
    }
    const pointsError = validateScoringPoints(form.value.scoringPoints)
    if (pointsError) {
      next.scoringPoints = pointsError
    }
    fieldErrors.value = next
    return Object.keys(next).length === 0
  }

  function addPoint(): void {
    form.value.scoringPoints.push(emptyScoringPoint())
  }

  function removePoint(index: number): void {
    if (form.value.scoringPoints.length <= 1) {
      return
    }
    form.value.scoringPoints.splice(index, 1)
  }

  async function bootstrap(): Promise<void> {
    mutationEpoch += 1
    const current = ++epoch
    submitting.value = false
    mutationUncertain.value = false
    formMessage.value = null
    error.value = null
    fieldErrors.value = {}
    if (mode.value === AdminQuestionFormMode.CREATE) {
      form.value = blankRequest()
      pageState.value = AdminQuestionFormState.READY
      return
    }
    const id = questionId.value
    if (id === null) {
      pageState.value = AdminQuestionFormState.INVALID_ID
      return
    }
    pageState.value = AdminQuestionFormState.LOADING
    try {
      const detail = await fetchAdminQuestion(id)
      if (current !== epoch) {
        return
      }
      form.value = {
        category: detail.category,
        knowledgePoint: detail.knowledgePoint,
        difficulty: detail.difficulty,
        questionContent: detail.content,
        referenceAnswer: detail.referenceAnswer,
        scoringPoints:
          detail.scoringPoints.length > 0
            ? detail.scoringPoints.map((point) => ({
                pointType: point.pointType,
                content: point.content,
                weight: point.weight,
              }))
            : [emptyScoringPoint()],
      }
      pageState.value = AdminQuestionFormState.READY
    } catch (caught) {
      if (current !== epoch) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        throw caught
      }
      if (isApiError(caught) && caught.kind === ErrorKind.FORBIDDEN) {
        await router.replace({ name: 'forbidden' })
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.NOT_FOUND) {
        pageState.value = AdminQuestionFormState.NOT_FOUND
        return
      }
      if (isApiError(caught)) {
        error.value = caught
        pageState.value = AdminQuestionFormState.ERROR
        return
      }
      throw caught
    }
  }

  async function submit(): Promise<void> {
    if (submitting.value) {
      return
    }
    formMessage.value = null
    mutationUncertain.value = false
    if (!validate()) {
      return
    }
    submitting.value = true
    const request = buildRequest()
    const operation = {
      mutationEpoch,
      mode: mode.value,
      questionId: questionId.value,
      routeName: route.name,
      userId: auth.userId,
      generation: getSessionGeneration(),
    }
    try {
      if (mode.value === AdminQuestionFormMode.CREATE) {
        const created = await createAdminQuestion(request)
        if (!isCurrentMutationScope(operation)) {
          return
        }
        await router.replace({
          name: 'admin-question-detail',
          params: { questionId: String(created.questionId) },
        })
        return
      }
      const id = questionId.value
      if (id === null) {
        return
      }
      await updateAdminQuestion(id, request)
      if (!isCurrentMutationScope(operation)) {
        return
      }
      await router.replace({
        name: 'admin-question-detail',
        params: { questionId: String(id) },
      })
    } catch (caught) {
      if (!isCurrentMutationScope(operation)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        throw caught
      }
      if (isApiError(caught) && caught.kind === ErrorKind.FORBIDDEN) {
        await router.replace({ name: 'forbidden' })
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.VALIDATION) {
        formMessage.value = caught.message
        return
      }
      if (isUncertainMutationFailure(caught)) {
        mutationUncertain.value = true
        formMessage.value =
          mode.value === AdminQuestionFormMode.CREATE
            ? QUESTION_ADMIN_COPY.createUncertain
            : QUESTION_ADMIN_COPY.editUncertain
        return
      }
      if (isApiError(caught)) {
        formMessage.value = caught.message
        return
      }
      throw caught
    } finally {
      if (operation.mutationEpoch === mutationEpoch && mounted) {
        submitting.value = false
      }
    }
  }

  watch(
    () => [route.name, route.params.questionId],
    () => {
      void bootstrap().catch(() => {
        // 401 is handled by global auth navigation.
      })
    },
    { immediate: true },
  )

  onUnmounted(() => {
    mounted = false
    mutationEpoch += 1
    epoch += 1
  })

  return {
    mode,
    questionId,
    pageState,
    error,
    form,
    fieldErrors,
    submitting,
    mutationUncertain,
    formMessage,
    weightSum,
    weightValid,
    canRemovePoint,
    canSubmit,
    addPoint,
    removePoint,
    submit,
    retry: bootstrap,
  }
}

export type { ScoringPointRequest }
