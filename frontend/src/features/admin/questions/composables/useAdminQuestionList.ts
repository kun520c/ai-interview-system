import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErrorKind, isApiError, type ApiError } from '@/api/apiError'
import { fetchAdminQuestions } from '@/features/admin/questions/api/questionAdminApi'
import {
  adminQuestionQueryRecord,
  parseAdminQuestionQuery,
  toAdminQuestionApiParams,
  type AdminQuestionListQuery,
} from '@/features/admin/questions/utils/questionQuery'
import { QuestionCategory, QuestionDifficulty, QuestionStatus } from '@/types/enums'
import type { AdminQuestionPageResponse } from '@/types/question'

export function useAdminQuestionList() {
  const route = useRoute()
  const router = useRouter()
  const data = ref<AdminQuestionPageResponse | null>(null)
  const loading = ref(false)
  const error = ref<ApiError | null>(null)
  const keywordDraft = ref('')
  let epoch = 0
  let mounted = false

  const parsed = computed(() => parseAdminQuestionQuery(route.query))

  async function canonicalizeQuery(): Promise<boolean> {
    if (!mounted) {
      return false
    }
    const next = parseAdminQuestionQuery(route.query)
    if (next.canonical) {
      return true
    }
    if (!mounted || route.name !== 'admin-questions') {
      return false
    }
    await router.replace({
      name: 'admin-questions',
      query: adminQuestionQueryRecord(next),
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
    const query = parseAdminQuestionQuery(route.query)
    keywordDraft.value = query.keyword ?? ''
    loading.value = true
    error.value = null
    try {
      const page = await fetchAdminQuestions(toAdminQuestionApiParams(query))
      if (current !== epoch) {
        return
      }
      data.value = page
    } catch (caught) {
      if (current !== epoch) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        throw caught
      }
      if (isApiError(caught) && caught.kind === ErrorKind.FORBIDDEN) {
        if (!mounted || current !== epoch || route.name !== 'admin-questions') {
          return
        }
        await router.replace({ name: 'forbidden' })
        return
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

  async function replaceQuery(patch: Partial<AdminQuestionListQuery>, resetPage = false): Promise<void> {
    const current = parseAdminQuestionQuery(route.query)
    const next: AdminQuestionListQuery = {
      ...current,
      ...patch,
      pageNum: resetPage ? 1 : (patch.pageNum ?? current.pageNum),
      canonical: true,
    }
    await router.replace({
      name: 'admin-questions',
      query: adminQuestionQueryRecord(next),
    })
  }

  async function applyKeyword(): Promise<void> {
    const keyword = keywordDraft.value.trim().slice(0, 100)
    await replaceQuery({ keyword: keyword.length > 0 ? keyword : null }, true)
  }

  async function setCategory(value: string): Promise<void> {
    await replaceQuery(
      {
        category: (Object.values(QuestionCategory) as string[]).includes(value)
          ? (value as QuestionCategory)
          : null,
      },
      true,
    )
  }

  async function setDifficulty(value: string): Promise<void> {
    await replaceQuery(
      {
        difficulty: (Object.values(QuestionDifficulty) as string[]).includes(value)
          ? (value as QuestionDifficulty)
          : null,
      },
      true,
    )
  }

  async function setStatus(value: string): Promise<void> {
    await replaceQuery(
      {
        status: (Object.values(QuestionStatus) as string[]).includes(value)
          ? (value as QuestionStatus)
          : null,
      },
      true,
    )
  }

  async function goToPage(pageNum: number): Promise<void> {
    if (pageNum < 1) {
      return
    }
    await replaceQuery({ pageNum })
  }

  onMounted(() => {
    mounted = true
    void load().catch(() => {
      // 401 is handled by global auth navigation.
    })
  })

  watch(
    () => route.query,
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
    parsed,
    keywordDraft,
    load,
    retry: load,
    applyKeyword,
    setCategory,
    setDifficulty,
    setStatus,
    goToPage,
  }
}
