import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErrorKind, isApiError, type ApiError } from '@/api/apiError'
import {
  fetchInterviewDetail,
  fetchInterviewReport,
  generateInterviewReport,
} from '@/features/interview/api/interviewApi'
import {
  REPORT_CONFLICT_NOT_COMPLETED,
  REPORT_COPY,
  REPORT_SERVICE_UNAVAILABLE,
} from '@/features/interview/copy/reportCopy'
import { ReportPageState } from '@/features/interview/types/reportPageState'
import {
  isActiveInterviewStatus,
  isCompletedInterviewStatus,
} from '@/features/interview/utils/completedSession'
import { isUncertainMutationFailure } from '@/features/interview/utils/uncertainMutation'
import { parsePositiveSessionId } from '@/utils/parsePositiveSessionId'
import { InterviewReportStatus } from '@/types/enums'
import type {
  InterviewDetailQuestionResponse,
  InterviewDetailResponse,
  InterviewReportResponse,
} from '@/types/interview'

export const REPORT_POLL_INTERVAL_MS = 3000

type ReportSubmissionStatus =
  | typeof ReportPageState.NOT_STARTED
  | typeof ReportPageState.FAILED
  | typeof ReportPageState.GENERATING

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

function isReadyReportComplete(report: InterviewReportResponse): boolean {
  return (
    report.reportStatus === InterviewReportStatus.READY &&
    report.overallScore !== null &&
    report.result !== null &&
    report.summary !== null &&
    Array.isArray(report.strengths) &&
    Array.isArray(report.weaknesses) &&
    Array.isArray(report.suggestions)
  )
}

export function useInterviewReport() {
  const route = useRoute()
  const router = useRouter()
  const pageState = ref<ReportPageState>(ReportPageState.GUARDING_SESSION)
  const detail = ref<InterviewDetailResponse | null>(null)
  const report = ref<InterviewReportResponse | null>(null)
  const error = ref<ApiError | null>(null)
  const statusMessage = ref<string | null>(null)
  const posting = ref(false)
  const readyAbnormal = ref(false)
  let epoch = 0
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let refreshEpoch: number | null = null
  let refreshSessionId: number | null = null

  const sessionId = computed(() => parsePositiveSessionId(route.params.sessionId))

  const timeline = computed(() => {
    if (!detail.value || !isCompletedInterviewStatus(detail.value.status)) {
      return []
    }
    return sortTimeline(detail.value.questions)
  })

  const canGenerate = computed(() => {
    return (
      (pageState.value === ReportPageState.NOT_STARTED || pageState.value === ReportPageState.FAILED) &&
      !posting.value
    )
  })

  const canRecoverGenerating = computed(
    () => pageState.value === ReportPageState.GENERATING && !posting.value,
  )
  const generateBusy = computed(() => posting.value)

  function isCurrentScope(operationEpoch: number, operationSessionId: number | null): boolean {
    return (
      operationEpoch === epoch &&
      operationSessionId !== null &&
      operationSessionId === sessionId.value
    )
  }

  function stopPolling(): void {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function startPolling(): void {
    if (pollTimer !== null) {
      return
    }
    pollTimer = setInterval(() => {
      void refreshReport().catch(() => {
        // 401 is handled by global auth navigation. The timer must not surface an unhandled rejection.
      })
    }, REPORT_POLL_INTERVAL_MS)
  }

  function adoptReport(next: InterviewReportResponse): void {
    report.value = next
    readyAbnormal.value = false
    statusMessage.value = null

    if (next.reportStatus === InterviewReportStatus.READY) {
      stopPolling()
      readyAbnormal.value = !isReadyReportComplete(next)
      pageState.value = ReportPageState.READY
      return
    }
    if (next.reportStatus === InterviewReportStatus.GENERATING) {
      pageState.value = ReportPageState.GENERATING
      startPolling()
      return
    }
    stopPolling()
    if (next.reportStatus === InterviewReportStatus.FAILED) {
      pageState.value = ReportPageState.FAILED
      return
    }
    pageState.value = ReportPageState.NOT_STARTED
  }

  function markUnavailable(): void {
    stopPolling()
    detail.value = null
    report.value = null
    pageState.value = ReportPageState.UNAVAILABLE
  }

  async function refreshReport(): Promise<void> {
    const operationEpoch = epoch
    const operationSessionId = sessionId.value
    if (operationSessionId === null || posting.value) {
      return
    }
    if (refreshEpoch === operationEpoch && refreshSessionId === operationSessionId) {
      return
    }
    refreshEpoch = operationEpoch
    refreshSessionId = operationSessionId
    try {
      const next = await fetchInterviewReport(operationSessionId)
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      adoptReport(next)
    } catch (caught) {
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      if (isApiError(caught) && caught.kind === 'not_found') {
        stopPolling()
        pageState.value = ReportPageState.NOT_FOUND
        return
      }
      if (isApiError(caught)) {
        error.value = caught
        statusMessage.value = REPORT_COPY.pollNetworkHint
        if (pageState.value === ReportPageState.RECOVERY_UNKNOWN) {
          return
        }
        return
      }
      throw caught
    } finally {
      if (refreshEpoch === operationEpoch && refreshSessionId === operationSessionId) {
        refreshEpoch = null
        refreshSessionId = null
      }
    }
  }

  async function recoverAfterMutationFailure(
    operationEpoch: number,
    operationSessionId: number,
  ): Promise<void> {
    try {
      const next = await fetchInterviewReport(operationSessionId)
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      adoptReport(next)
    } catch (caught) {
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      stopPolling()
      if (isApiError(caught) && caught.kind === 'not_found') {
        pageState.value = ReportPageState.NOT_FOUND
        return
      }
      pageState.value = ReportPageState.RECOVERY_UNKNOWN
      statusMessage.value = null
      if (isApiError(caught)) {
        error.value = caught
      }
    }
  }

  async function realignFromDetail(
    operationEpoch: number,
    operationSessionId: number,
  ): Promise<void> {
    const response = await fetchInterviewDetail(operationSessionId)
    if (!isCurrentScope(operationEpoch, operationSessionId)) {
      return
    }
    if (isActiveInterviewStatus(response.status)) {
      detail.value = null
      report.value = null
      stopPolling()
      pageState.value = ReportPageState.REDIRECTING
      await router.replace({ name: 'interview-session' })
      return
    }
    if (!isCompletedInterviewStatus(response.status)) {
      markUnavailable()
      return
    }
    detail.value = response
    pageState.value = ReportPageState.LOADING_REPORT
    await recoverAfterMutationFailure(operationEpoch, operationSessionId)
  }

  async function handleGenerateFailure(
    caught: unknown,
    operationEpoch: number,
    operationSessionId: number,
    recoverableStatus: ReportSubmissionStatus,
  ): Promise<void> {
    if (!isCurrentScope(operationEpoch, operationSessionId)) {
      return
    }
    if (isApiError(caught) && caught.kind === 'unauthenticated') {
      throw caught
    }
    if (isApiError(caught) && caught.kind === 'not_found') {
      stopPolling()
      pageState.value = ReportPageState.NOT_FOUND
      return
    }
    if (isApiError(caught) && caught.message === REPORT_SERVICE_UNAVAILABLE) {
      if (recoverableStatus === ReportPageState.GENERATING) {
        // A stale claim may already have changed the durable state before the service check.
        await recoverAfterMutationFailure(operationEpoch, operationSessionId)
        if (
          isCurrentScope(operationEpoch, operationSessionId) &&
          (pageState.value === ReportPageState.GENERATING || pageState.value === ReportPageState.FAILED)
        ) {
          statusMessage.value = REPORT_COPY.serviceUnavailable
        }
        return
      }
      stopPolling()
      statusMessage.value = REPORT_COPY.serviceUnavailable
      pageState.value = recoverableStatus
      return
    }
    if (isApiError(caught) && caught.kind === ErrorKind.CONFLICT) {
      if (caught.message === REPORT_CONFLICT_NOT_COMPLETED) {
        await realignFromDetail(operationEpoch, operationSessionId)
        return
      }
      await recoverAfterMutationFailure(operationEpoch, operationSessionId)
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      if (pageState.value === ReportPageState.GENERATING) {
        statusMessage.value = REPORT_COPY.stillGenerating
      }
      return
    }
    if (isUncertainMutationFailure(caught)) {
      if (isApiError(caught)) {
        error.value = caught
      }
      await recoverAfterMutationFailure(operationEpoch, operationSessionId)
      return
    }
    stopPolling()
    pageState.value = ReportPageState.ERROR
    if (isApiError(caught)) {
      error.value = caught
    }
  }

  async function generate(): Promise<void> {
    if (pageState.value === ReportPageState.NOT_STARTED || pageState.value === ReportPageState.FAILED) {
      await submitReport(pageState.value)
    }
  }

  async function recoverGenerating(): Promise<void> {
    await submitReport(ReportPageState.GENERATING)
  }

  async function submitReport(recoverableStatus: ReportSubmissionStatus): Promise<void> {
    const operationSessionId = sessionId.value
    if (
      operationSessionId === null ||
      posting.value ||
      pageState.value !== recoverableStatus
    ) {
      return
    }
    // Invalidate any pre-submit GET, including one that returns after POST has reached READY.
    const operationEpoch = ++epoch
    stopPolling()
    posting.value = true
    pageState.value = ReportPageState.GENERATING_POST
    statusMessage.value = null
    error.value = null
    try {
      const response = await generateInterviewReport(operationSessionId)
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      adoptReport(response)
    } catch (caught) {
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      await handleGenerateFailure(caught, operationEpoch, operationSessionId, recoverableStatus)
    } finally {
      if (isCurrentScope(operationEpoch, operationSessionId)) {
        posting.value = false
      }
    }
  }

  async function bootstrap(): Promise<void> {
    const operationEpoch = ++epoch
    stopPolling()
    refreshEpoch = null
    refreshSessionId = null
    detail.value = null
    report.value = null
    error.value = null
    statusMessage.value = null
    readyAbnormal.value = false
    posting.value = false

    const operationSessionId = sessionId.value
    if (operationSessionId === null) {
      pageState.value = ReportPageState.INVALID_ID
      return
    }

    pageState.value = ReportPageState.GUARDING_SESSION
    let response
    try {
      response = await fetchInterviewDetail(operationSessionId)
    } catch (caught) {
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      if (isApiError(caught) && caught.kind === 'not_found') {
        pageState.value = ReportPageState.NOT_FOUND
        return
      }
      if (isApiError(caught)) {
        error.value = caught
        pageState.value = ReportPageState.ERROR
        return
      }
      throw caught
    }
    if (!isCurrentScope(operationEpoch, operationSessionId)) {
      return
    }
    if (isActiveInterviewStatus(response.status)) {
      pageState.value = ReportPageState.REDIRECTING
      await router.replace({ name: 'interview-session' })
      return
    }
    if (!isCompletedInterviewStatus(response.status)) {
      markUnavailable()
      return
    }
    detail.value = response
    pageState.value = ReportPageState.LOADING_REPORT
    try {
      const next = await fetchInterviewReport(operationSessionId)
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      adoptReport(next)
    } catch (caught) {
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      if (isApiError(caught) && caught.kind === 'unauthenticated') {
        throw caught
      }
      if (isApiError(caught) && caught.kind === 'not_found') {
        pageState.value = ReportPageState.NOT_FOUND
        return
      }
      if (isApiError(caught)) {
        error.value = caught
        pageState.value = ReportPageState.ERROR
        return
      }
      throw caught
    }
  }

  watch(
    () => route.params.sessionId,
    () => {
      void bootstrap().catch(() => {
        // 401 is handled by global auth navigation.
      })
    },
    { immediate: true },
  )

  onUnmounted(() => {
    epoch += 1
    stopPolling()
  })

  return {
    pageState,
    detail,
    report,
    timeline,
    error,
    statusMessage,
    readyAbnormal,
    canGenerate,
    canRecoverGenerating,
    generateBusy,
    sessionId,
    bootstrap,
    generate,
    recoverGenerating,
    refreshReport,
    recoverAfterMutationFailure,
    startPolling,
    stopPolling,
  }
}
