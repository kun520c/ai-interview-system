import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErrorKind, isApiError, type ApiError } from '@/api/apiError'
import {
  fetchInterviewDetail,
  fetchInterviewReport,
  generateInterviewReport,
} from '@/features/interview/api/interviewApi'
import {
  REPORT_CONFLICT_GENERATING,
  REPORT_CONFLICT_NOT_COMPLETED,
  REPORT_CONFLICT_STATE_CHANGED,
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
  let recoverableStatus: typeof ReportPageState.NOT_STARTED | typeof ReportPageState.FAILED | null =
    null

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

  const generateBusy = computed(() => pageState.value === ReportPageState.GENERATING_POST)

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
    if (operationSessionId === null) {
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
    await refreshReport()
  }

  async function handleGenerateFailure(
    caught: unknown,
    operationEpoch: number,
    operationSessionId: number,
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
      stopPolling()
      statusMessage.value = REPORT_COPY.serviceUnavailable
      if (recoverableStatus) {
        pageState.value = recoverableStatus
      }
      return
    }
    if (isApiError(caught) && caught.kind === ErrorKind.CONFLICT) {
      if (caught.message === REPORT_CONFLICT_NOT_COMPLETED) {
        await realignFromDetail(operationEpoch, operationSessionId)
        return
      }
      await refreshReport()
      if (!isCurrentScope(operationEpoch, operationSessionId)) {
        return
      }
      if (
        caught.message === REPORT_CONFLICT_GENERATING &&
        report.value?.reportStatus === InterviewReportStatus.GENERATING
      ) {
        startPolling()
      }
      if (
        caught.message === REPORT_CONFLICT_STATE_CHANGED ||
        caught.message === REPORT_CONFLICT_GENERATING
      ) {
        return
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
    const operationEpoch = epoch
    const operationSessionId = sessionId.value
    if (
      operationSessionId === null ||
      posting.value ||
      (pageState.value !== ReportPageState.NOT_STARTED && pageState.value !== ReportPageState.FAILED)
    ) {
      return
    }
    recoverableStatus =
      pageState.value === ReportPageState.FAILED
        ? ReportPageState.FAILED
        : ReportPageState.NOT_STARTED
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
      await handleGenerateFailure(caught, operationEpoch, operationSessionId)
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
    generateBusy,
    sessionId,
    bootstrap,
    generate,
    refreshReport,
    recoverAfterMutationFailure,
    startPolling,
    stopPolling,
  }
}
