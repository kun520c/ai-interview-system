import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { isApiError, type ApiError } from '@/api/apiError'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import {
  historyQueryRecord,
  parseHistoryQuery,
} from '@/features/history/utils/historyQuery'
import type { InterviewHistoryPageResponse } from '@/types/interview'

export function useInterviewHistory() {
  const route = useRoute()
  const router = useRouter()
  const data = ref<InterviewHistoryPageResponse | null>(null)
  const loading = ref(false)
  const error = ref<ApiError | null>(null)
  let epoch = 0
  let mounted = false

  const pagination = computed(() => parseHistoryQuery(route.query))

  async function canonicalizeQuery(): Promise<boolean> {
    if (!mounted) {
      return false
    }
    const parsed = parseHistoryQuery(route.query)
    if (parsed.canonical) {
      return true
    }
    if (!mounted || route.name !== 'interview-history') {
      return false
    }
    await router.replace({
      name: 'interview-history',
      query: historyQueryRecord(parsed.page, parsed.pageSize),
    })
    return false
  }

  async function load(): Promise<void> {
    const current = ++epoch
    if (!(await canonicalizeQuery())) {
      return
    }
    if (!mounted || current !== epoch) {
      return
    }

    const parsed = parseHistoryQuery(route.query)
    loading.value = true
    error.value = null
    try {
      const page = await fetchInterviewHistory(parsed.page, parsed.pageSize)
      if (current !== epoch) {
        return
      }
      data.value = page
    } catch (caught) {
      if (current !== epoch) {
        return
      }
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      if (isApiError(caught)) {
        error.value = caught
        return
      }
      throw caught
    } finally {
      if (current === epoch) {
        loading.value = false
      }
    }
  }

  function retry(): Promise<void> {
    return load()
  }

  async function goToPage(page: number): Promise<void> {
    const parsed = parseHistoryQuery(route.query)
    if (page < 1 || page === parsed.page) {
      return
    }
    await router.push({
      name: 'interview-history',
      query: historyQueryRecord(page, parsed.pageSize),
    })
  }

  onMounted(() => {
    mounted = true
    void load().catch(() => {
      // 401 is handled by global auth navigation.
    })
  })

  watch(
    () => [route.query.page, route.query.pageSize],
    () => {
      if (!mounted) {
        return
      }
      void load().catch(() => {
        // 401 is handled by global auth navigation.
      })
    },
  )

  onUnmounted(() => {
    mounted = false
    epoch += 1
  })

  return {
    data,
    loading,
    error,
    pagination,
    load,
    retry,
    goToPage,
  }
}
