import { ref } from 'vue'

import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { isApiError } from '@/api/apiError'
import type { ApiError } from '@/api/apiError'
import type { InterviewHistoryPageResponse } from '@/types/interview'

const DASHBOARD_HISTORY_PAGE = 1
const DASHBOARD_HISTORY_PAGE_SIZE = 5

export function useDashboardHistory() {
  const data = ref<InterviewHistoryPageResponse | null>(null)
  const loading = ref(false)
  const error = ref<ApiError | null>(null)

  async function load(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      data.value = await fetchInterviewHistory(DASHBOARD_HISTORY_PAGE, DASHBOARD_HISTORY_PAGE_SIZE)
    } catch (caught) {
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      if (isApiError(caught)) {
        error.value = caught
      } else {
        throw caught
      }
    } finally {
      loading.value = false
    }
  }

  function retry(): Promise<void> {
    return load()
  }

  return {
    data,
    loading,
    error,
    load,
    retry,
  }
}
