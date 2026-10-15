import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErrorKind, isApiError, type ApiError } from '@/api/apiError'
import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import {
  fetchAdminQuestion,
  updateAdminQuestionStatus,
} from '@/features/admin/questions/api/questionAdminApi'
import { isUncertainMutationFailure } from '@/features/interview/utils/uncertainMutation'
import { parsePositiveSessionId } from '@/utils/parsePositiveSessionId'
import { QuestionStatus } from '@/types/enums'
import type { AdminQuestionDetailResponse } from '@/types/question'

export const AdminQuestionDetailState = {
  INVALID_ID: 'INVALID_ID',
  LOADING: 'LOADING',
  READY: 'READY',
  NOT_FOUND: 'NOT_FOUND',
  ERROR: 'ERROR',
} as const

export type AdminQuestionDetailState =
  (typeof AdminQuestionDetailState)[keyof typeof AdminQuestionDetailState]

export function useAdminQuestionDetail() {
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()
  const pageState = ref<AdminQuestionDetailState>(AdminQuestionDetailState.LOADING)
  const detail = ref<AdminQuestionDetailResponse | null>(null)
  const error = ref<ApiError | null>(null)
  const statusBusy = ref(false)
  const statusMessage = ref<string | null>(null)
  const statusUncertain = ref(false)
  let epoch = 0
  let mounted = true

  const questionId = computed(() => parsePositiveSessionId(route.params.questionId))

  function isCurrentStatusScope(
    operationEpoch: number,
    operationQuestionId: number,
    operationUserId: number | null,
    operationGeneration: number,
  ): boolean {
    return (
      mounted &&
      operationEpoch === epoch &&
      operationQuestionId === questionId.value &&
      isCurrentIdentityScope(operationUserId, operationGeneration, auth.userId)
    )
  }

  async function load(): Promise<void> {
    const current = ++epoch
    const id = questionId.value
    detail.value = null
    error.value = null
    statusBusy.value = false
    statusMessage.value = null
    statusUncertain.value = false
    if (id === null) {
      pageState.value = AdminQuestionDetailState.INVALID_ID
      return
    }
    pageState.value = AdminQuestionDetailState.LOADING
    try {
      const response = await fetchAdminQuestion(id)
      if (current !== epoch) {
        return
      }
      detail.value = response
      pageState.value = AdminQuestionDetailState.READY
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
        pageState.value = AdminQuestionDetailState.NOT_FOUND
        return
      }
      if (isApiError(caught)) {
        error.value = caught
        pageState.value = AdminQuestionDetailState.ERROR
        return
      }
      throw caught
    }
  }

  async function updateStatus(status: QuestionStatus): Promise<void> {
    const id = questionId.value
    if (id === null || statusBusy.value || !detail.value || detail.value.status === status) {
      return
    }
    statusBusy.value = true
    statusMessage.value = null
    statusUncertain.value = false
    const operationEpoch = epoch
    const operationUserId = auth.userId
    const operationGeneration = getSessionGeneration()
    try {
      await updateAdminQuestionStatus(id, { status })
      if (!isCurrentStatusScope(operationEpoch, id, operationUserId, operationGeneration)) {
        return
      }
      await load()
    } catch (caught) {
      if (!isCurrentStatusScope(operationEpoch, id, operationUserId, operationGeneration)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        throw caught
      }
      if (isApiError(caught) && caught.kind === ErrorKind.FORBIDDEN) {
        await router.replace({ name: 'forbidden' })
        return
      }
      if (isUncertainMutationFailure(caught)) {
        statusUncertain.value = true
        statusMessage.value = '状态更新结果暂时无法确认。'
        return
      }
      if (isApiError(caught)) {
        statusMessage.value = caught.message
        return
      }
      throw caught
    } finally {
      if (isCurrentStatusScope(operationEpoch, id, operationUserId, operationGeneration)) {
        statusBusy.value = false
      }
    }
  }

  watch(
    () => route.params.questionId,
    () => {
      void load().catch(() => {
        // 401 is handled by global auth navigation.
      })
    },
    { immediate: true },
  )

  onUnmounted(() => {
    mounted = false
    epoch += 1
  })

  return {
    pageState,
    detail,
    error,
    questionId,
    statusBusy,
    statusMessage,
    statusUncertain,
    load,
    retry: load,
    updateStatus,
  }
}
