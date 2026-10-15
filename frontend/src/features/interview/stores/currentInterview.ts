import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { subscribeSessionScope } from '@/app/session/sessionScope'
import type { ApiError } from '@/api/apiError'
import { isApiError } from '@/api/apiError'
import type { InterviewSessionResponse } from '@/types/interview'

export const useCurrentInterviewStore = defineStore('currentInterview', () => {
  const session = ref<InterviewSessionResponse | null>(null)
  const loaded = ref(false)
  const loading = ref(false)
  const error = ref<ApiError | null>(null)
  const epoch = ref(0)
  let inflightRefresh: Promise<InterviewSessionResponse | null> | null = null

  const hasActiveInterview = computed(() => session.value !== null)

  function invalidate(): void {
    epoch.value += 1
    inflightRefresh = null
    session.value = null
    loaded.value = false
    loading.value = false
    error.value = null
  }

  function adoptCreatedSession(nextSession: InterviewSessionResponse): void {
    epoch.value += 1
    inflightRefresh = null
    session.value = nextSession
    loaded.value = true
    loading.value = false
    error.value = null
  }

  subscribeSessionScope(() => {
    invalidate()
  })

  async function refresh(): Promise<InterviewSessionResponse | null> {
    if (inflightRefresh) {
      return inflightRefresh
    }

    const requestEpoch = epoch.value
    loading.value = true
    error.value = null

    const pending = (async () => {
      try {
        const nextSession = await fetchCurrentInterview()
        if (requestEpoch !== epoch.value) {
          return null
        }
        session.value = nextSession
        loaded.value = true
        return session.value
      } catch (caught) {
        if (requestEpoch !== epoch.value) {
          return null
        }
        if (isApiError(caught)) {
          error.value = caught
        }
        throw caught
      } finally {
        if (requestEpoch === epoch.value) {
          loading.value = false
        }
      }
    })()

    inflightRefresh = pending
    try {
      return await pending
    } finally {
      if (inflightRefresh === pending) {
        inflightRefresh = null
      }
    }
  }

  function clear(): void {
    invalidate()
  }

  return {
    session,
    loaded,
    loading,
    error,
    epoch,
    hasActiveInterview,
    refresh,
    clear,
    invalidate,
    adoptCreatedSession,
  }
})

export function ensureCurrentInterviewSessionBinding(): void {
  useCurrentInterviewStore()
}
