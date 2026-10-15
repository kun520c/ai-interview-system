import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { isApiError, type ApiError } from '@/api/apiError'
import { fetchInterviewDetail } from '@/features/interview/api/interviewApi'
import {
  isActiveInterviewStatus,
  isCompletedInterviewStatus,
} from '@/features/interview/utils/completedSession'
import { parsePositiveSessionId } from '@/utils/parsePositiveSessionId'
import type { InterviewDetailQuestionResponse, InterviewDetailResponse } from '@/types/interview'

export const DetailPageState = {
  INVALID_ID: 'INVALID_ID',
  LOADING: 'LOADING',
  REDIRECTING: 'REDIRECTING',
  READY: 'READY',
  NOT_FOUND: 'NOT_FOUND',
  UNAVAILABLE: 'UNAVAILABLE',
  EMPTY_TIMELINE: 'EMPTY_TIMELINE',
  ERROR: 'ERROR',
} as const

export type DetailPageState = (typeof DetailPageState)[keyof typeof DetailPageState]

function sortTimeline(
  questions: InterviewDetailQuestionResponse[],
): InterviewDetailQuestionResponse[] {
  return [...questions].sort((left, right) => {
    if (left.displayOrder !== right.displayOrder) {
      return left.displayOrder - right.displayOrder
    }
    return left.interviewQuestionId - right.interviewQuestionId
  })
}

export function useInterviewDetail() {
  const route = useRoute()
  const router = useRouter()
  const pageState = ref<DetailPageState>(DetailPageState.LOADING)
  const detail = ref<InterviewDetailResponse | null>(null)
  const error = ref<ApiError | null>(null)
  let epoch = 0
  let mounted = true

  const sessionId = computed(() => parsePositiveSessionId(route.params.sessionId))

  const timeline = computed(() => {
    if (!detail.value || !isCompletedInterviewStatus(detail.value.status)) {
      return []
    }
    return sortTimeline(detail.value.questions)
  })

  const hasIncompleteItems = computed(() => {
    if (timeline.value.length === 0) {
      return false
    }
    return timeline.value.some((item) => item.answer === null || item.evaluation === null)
  })

  async function load(): Promise<void> {
    const current = ++epoch
    const id = sessionId.value
    detail.value = null
    error.value = null

    if (id === null) {
      pageState.value = DetailPageState.INVALID_ID
      return
    }

    pageState.value = DetailPageState.LOADING
    let response
    try {
      response = await fetchInterviewDetail(id)
    } catch (caught) {
      if (current !== epoch) {
        return
      }
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      if (isApiError(caught) && caught.kind === 'not_found') {
        pageState.value = DetailPageState.NOT_FOUND
        return
      }
      if (isApiError(caught)) {
        error.value = caught
        pageState.value = DetailPageState.ERROR
        return
      }
      throw caught
    }
    if (current !== epoch) {
      return
    }
    if (isActiveInterviewStatus(response.status)) {
      if (!mounted || current !== epoch || route.name !== 'interview-detail') {
        return
      }
      pageState.value = DetailPageState.REDIRECTING
      await router.replace({ name: 'interview-session' })
      return
    }
    if (!isCompletedInterviewStatus(response.status)) {
      pageState.value = DetailPageState.UNAVAILABLE
      return
    }
    if (response.questions.length === 0) {
      pageState.value = DetailPageState.EMPTY_TIMELINE
      return
    }
    detail.value = response
    pageState.value = DetailPageState.READY
  }

  watch(
    () => route.params.sessionId,
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
    timeline,
    hasIncompleteItems,
    error,
    sessionId,
    load,
    retry: load,
  }
}
