import { storeToRefs } from 'pinia'

import { useCurrentInterviewStore } from '@/features/interview/stores/currentInterview'

export function useCurrentInterview() {
  const store = useCurrentInterviewStore()
  const { session, loaded, loading, error, hasActiveInterview } = storeToRefs(store)

  return {
    session,
    loaded,
    loading,
    error,
    hasActiveInterview,
    refresh: store.refresh,
    clear: store.clear,
    invalidate: store.invalidate,
    adoptCreatedSession: store.adoptCreatedSession,
  }
}
