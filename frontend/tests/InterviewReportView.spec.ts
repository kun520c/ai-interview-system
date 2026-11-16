import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { AxiosError } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { httpClient } from '@/api/httpClient'
import { resetUnauthorizedGate, setUnauthorizedHandler } from '@/api/unauthorized'
import { bindUnauthorizedNavigation } from '@/app/router/unauthorizedNavigation'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { login as loginRequest } from '@/features/auth/api/authApi'
import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import {
  fetchCurrentInterview,
  fetchInterviewDetail,
  fetchInterviewReport,
  generateInterviewReport,
} from '@/features/interview/api/interviewApi'
import { REPORT_POLL_INTERVAL_MS } from '@/features/interview/composables/useInterviewReport'
import { REPORT_COPY, REPORT_SERVICE_DISABLED, REPORT_SERVICE_NOT_ACCEPTING } from '@/features/interview/copy/reportCopy'
import { authenticate, makeUser, mountApp } from './helpers/mountApp'
import { deferred } from './helpers/deferred'
import {
  completedDetail,
  createdDetail,
  currentSession,
  emptyHistoryPage,
  inProgressDetail,
  PENDING_FUTURE_QUESTION_SECRET,
  cancelledDetail,
  CANCELLED_QUESTION_SECRET,
  reportResponse,
  statusReport,
} from './helpers/interviewFixtures'
import { InterviewReportStatus, InterviewSessionStatus } from '@/types/enums'
import type { InterviewDetailResponse, InterviewReportResponse } from '@/types/interview'

enableAutoUnmount(afterEach)

vi.mock('@/features/auth/api/authApi', () => ({
  login: vi.fn(),
  registerAccount: vi.fn(),
}))

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

vi.mock('@/features/interview/api/interviewApi', () => ({
  fetchCurrentInterview: vi.fn(),
  createInterview: vi.fn(),
  createOrResumeInterview: vi.fn(),
  submitInterviewAnswer: vi.fn(),
  fetchInterviewDetail: vi.fn(),
  fetchInterviewReport: vi.fn(),
  generateInterviewReport: vi.fn(),
}))

vi.mock('@/features/history/api/historyApi', () => ({
  fetchInterviewHistory: vi.fn(),
}))

async function mountReport(path = '/interviews/41/report') {
  const app = await mountApp(path, (auth) => authenticate(auth))
  await flushPromises()
  return app
}

function generateButton(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper']) {
  return wrapper.findAll('button').find((button) => button.text().includes('生成报告') || button.text().includes('重新生成'))
}

function recoveryButton(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper']) {
  return wrapper.findAll('button').find((button) => button.text() === REPORT_COPY.recoverGenerating)
}

describe('InterviewReportView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(loginRequest).mockReset()
    vi.mocked(fetchCurrentUser).mockReset()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchInterviewDetail).mockReset()
    vi.mocked(fetchInterviewReport).mockReset()
    vi.mocked(generateInterviewReport).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
    vi.mocked(fetchInterviewDetail).mockResolvedValue(completedDetail())
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.NOT_STARTED, 41))
  })

  afterEach(() => {
    vi.useRealTimers()
    setUnauthorizedHandler(null)
    resetUnauthorizedGate()
  })

  it('loads Detail first, then Report, for a completed session', async () => {
    const order: string[] = []
    vi.mocked(fetchInterviewDetail).mockImplementation(async () => {
      order.push('detail')
      return completedDetail()
    })
    vi.mocked(fetchInterviewReport).mockImplementation(async () => {
      order.push('report')
      return statusReport(InterviewReportStatus.NOT_STARTED, 41)
    })
    await mountReport()
    expect(order).toEqual(['detail', 'report'])
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('redirects IN_PROGRESS without fetching or generating a report', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(inProgressDetail())
    const { wrapper, router } = await mountReport('/interviews/21/report')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
    expect(fetchInterviewReport).not.toHaveBeenCalled()
    expect(generateInterviewReport).not.toHaveBeenCalled()
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })

  it('redirects CREATED without fetching report', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(createdDetail())
    const { wrapper, router } = await mountReport('/interviews/31/report')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
    expect(fetchInterviewReport).not.toHaveBeenCalled()
    expect(generateInterviewReport).not.toHaveBeenCalled()
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })

  it('does not fetch or generate a report for CANCELLED sessions', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(cancelledDetail())
    const { wrapper, router } = await mountReport('/interviews/51/report')
    expect(router.currentRoute.value.name).toBe('interview-report')
    expect(fetchInterviewReport).not.toHaveBeenCalled()
    expect(generateInterviewReport).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
    expect(wrapper.html()).not.toContain(CANCELLED_QUESTION_SECRET)
    expect(wrapper.text()).not.toContain('尚未生成报告')
    expect(wrapper.text()).not.toContain('生成报告')
  })

  it('does not request APIs for an invalid sessionId', async () => {
    const { wrapper } = await mountReport('/interviews/abc/report')
    expect(fetchInterviewDetail).not.toHaveBeenCalled()
    expect(fetchInterviewReport).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
  })

  it('shows generate CTA for NOT_STARTED and does not auto POST', async () => {
    const { wrapper } = await mountReport()
    expect(wrapper.text()).toContain('尚未生成报告')
    expect(generateButton(wrapper)?.text()).toContain('生成报告')
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it.each([InterviewReportStatus.NOT_STARTED, InterviewReportStatus.GENERATING, InterviewReportStatus.FAILED])(
    'shows saved results during %s without claiming a complete report or making another detail request', async (status) => {
      vi.useFakeTimers()
      vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(status, 41))
      const { wrapper } = await mountReport()
      expect(wrapper.text()).toContain(REPORT_COPY.savedEvidenceHint)
      expect(wrapper.find('.saved-final-scores').text()).toContain('/ 100')
      expect(wrapper.find('.report-summary').exists()).toBe(false)
      expect(wrapper.findAll('.timeline')).toHaveLength(1)
      await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
      expect(fetchInterviewDetail).toHaveBeenCalledTimes(1)
      expect(generateInterviewReport).not.toHaveBeenCalled()
    },
  )

  it('shows saved results while the report GET is pending, then keeps only one timeline on READY', async () => {
    const pending = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewReport).mockReturnValue(pending.promise)
    const { wrapper } = await mountReport()
    expect(wrapper.find('.saved-final-scores').exists()).toBe(true)
    pending.resolve(reportResponse())
    await flushPromises()
    expect(wrapper.find('.saved-final-scores').exists()).toBe(false)
    expect(wrapper.findAll('.timeline')).toHaveLength(1)
    expect(fetchInterviewDetail).toHaveBeenCalledTimes(1)
  })

  it('hides saved results when a later report query rejects ownership', async () => {
    vi.mocked(fetchInterviewReport).mockRejectedValue(new ApiError({
      httpStatus: 404, code: 404, message: '面试会话不存在', kind: ErrorKind.NOT_FOUND,
    }))
    const { wrapper } = await mountReport()
    expect(wrapper.find('.report-evidence').exists()).toBe(false)
    expect(wrapper.text()).not.toContain(completedDetail().questions[0]!.questionContent)
  })

  it('reads saved results after FAILED, a page remount, and a fresh login without POST', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.FAILED, 41))
    const first = await mountReport()
    expect(first.wrapper.find('.saved-final-scores').exists()).toBe(true)
    first.wrapper.unmount()

    const refreshed = await mountReport()
    expect(refreshed.wrapper.text()).toContain(REPORT_COPY.failedTitle)
    expect(refreshed.wrapper.find('.saved-final-scores').exists()).toBe(true)
    refreshed.auth.logout()
    await refreshed.router.push('/login')
    await flushPromises()
    expect(refreshed.wrapper.find('.report-evidence').exists()).toBe(false)

    const me = makeUser()
    vi.mocked(loginRequest).mockResolvedValue({
      ...me, accessToken: 'new-login-token', tokenType: 'Bearer', expiresInSeconds: 3600,
    })
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    await refreshed.auth.login({ account: me.account, password: 'test-password' })
    await refreshed.router.push('/interviews/41/report')
    await flushPromises()
    expect(refreshed.wrapper.text()).toContain(REPORT_COPY.failedTitle)
    expect(refreshed.wrapper.find('.saved-final-scores').exists()).toBe(true)
    expect(refreshed.wrapper.find('.report-summary').exists()).toBe(false)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewDetail).toHaveBeenCalledTimes(3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(3)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('does not reuse saved results after another account logs in and detail denies ownership', async () => {
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.FAILED, 41))
    const { wrapper, auth, router } = await mountReport()
    expect(wrapper.find('.saved-final-scores').exists()).toBe(true)
    auth.logout()
    await router.push('/login')
    await flushPromises()
    const me = { ...makeUser(), userId: 77, account: 'other-user' }
    vi.mocked(loginRequest).mockResolvedValue({
      ...me, accessToken: 'other-user-token', tokenType: 'Bearer', expiresInSeconds: 3600,
    })
    vi.mocked(fetchCurrentUser).mockResolvedValue(me)
    vi.mocked(fetchInterviewDetail).mockRejectedValue(new ApiError({
      httpStatus: 404, code: 404, message: '面试会话不存在', kind: ErrorKind.NOT_FOUND,
    }))
    await auth.login({ account: me.account, password: 'test-password' })
    await router.push('/interviews/41/report')
    await flushPromises()
    expect(wrapper.find('.report-evidence').exists()).toBe(false)
    expect(wrapper.text()).not.toContain(completedDetail().questions[0]!.questionContent)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('polls GET only while GENERATING and stops on READY', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport)
      .mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
      .mockResolvedValueOnce(reportResponse())
    const { wrapper } = await mountReport()
    expect(wrapper.text()).toContain('报告正在生成')
    expect(generateInterviewReport).not.toHaveBeenCalled()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
    await flushPromises()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('本场说明')
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    await flushPromises()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
  })

  it('keeps long-running GENERATING polling read-only and offers explicit recovery', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    try {
      // Advancing page time cannot establish database staleness or reclaim a backend task.
      await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 220)
      await flushPromises()
      expect(fetchInterviewReport).toHaveBeenCalledTimes(221)
      expect(generateInterviewReport).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('报告正在生成')
      expect(wrapper.text()).not.toContain('上次报告生成未完成')
      expect(wrapper.text()).not.toContain('本场说明')
      expect(generateButton(wrapper)).toBeUndefined()
      expect(recoveryButton(wrapper)).toBeDefined()
      expect(wrapper.text()).toContain(REPORT_COPY.recoveryHint)
    } finally {
      wrapper.unmount()
    }
  })

  it('queries once per manual check and adopts READY without POST', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    const pending = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewReport).mockReturnValueOnce(pending.promise)
    const button = recoveryButton(wrapper)!
    await Promise.all([button.trigger('click'), button.trigger('click')])
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(button.attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    pending.resolve(reportResponse())
    await flushPromises()
    expect(wrapper.text()).toContain('本场说明')
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it.each([
    new ApiError({ httpStatus: 409, code: 409, message: '状态变化', kind: ErrorKind.CONFLICT }),
    new ApiError({ httpStatus: 0, code: 0, message: '超时', kind: ErrorKind.NETWORK }),
    new ApiError({ httpStatus: 400, code: 400, message: '报告生成服务当前不可用', kind: ErrorKind.VALIDATION }),
    new ApiError({ httpStatus: 502, code: 502, message: '外部服务暂时不可用', kind: ErrorKind.EXTERNAL_SERVICE }),
  ])('keeps durable GENERATING on query failure $httpStatus and recovers with GET', async (failure) => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    vi.mocked(fetchInterviewReport).mockRejectedValueOnce(failure)
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(REPORT_COPY.generatingTitle)
    expect(wrapper.text()).toContain(REPORT_COPY.pollNetworkHint)
    expect(generateButton(wrapper)).toBeUndefined()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(reportResponse({ summary: 'ALREADY_COMPLETED_REPORT' }))
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('ALREADY_COMPLETED_REPORT')
    expect(wrapper.text()).not.toContain(REPORT_COPY.pollNetworkHint)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(3)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it.each([
    { status: InterviewReportStatus.GENERATING, expected: REPORT_COPY.generatingTitle },
    { status: InterviewReportStatus.FAILED, expected: REPORT_COPY.failedTitle },
    { status: InterviewReportStatus.READY, expected: '本场说明' },
    { status: InterviewReportStatus.NOT_STARTED, expected: REPORT_COPY.notStartedTitle },
  ])('uses the actual queried $status', async ({ status, expected }) => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    vi.mocked(fetchInterviewReport).mockResolvedValue(status === InterviewReportStatus.READY ? reportResponse() : statusReport(status, 41))
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(expected)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(status === InterviewReportStatus.GENERATING ? 3 : 2)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('only POSTs a new attempt after query returns FAILED and user explicitly retries', async () => {
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.FAILED, 41))
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(REPORT_COPY.failedTitle)
    expect(generateInterviewReport).not.toHaveBeenCalled()
    vi.mocked(generateInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    await generateButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(REPORT_COPY.generatingTitle)
    expect(generateInterviewReport).toHaveBeenCalledExactlyOnceWith(41)
  })

  it.each(['READY', 'FAILED', '404'] as const)('shares an in-flight poll with manual checks until %s, then stops', async (outcome) => {
    vi.useFakeTimers()
    const pending = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
      .mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountReport()
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(recoveryButton(wrapper)!.attributes('disabled')).toBeDefined()
    await recoveryButton(wrapper)!.trigger('click')
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    if (outcome === 'READY') pending.resolve(reportResponse())
    else if (outcome === 'FAILED') pending.resolve(statusReport(InterviewReportStatus.FAILED, 41))
    else pending.reject(new ApiError({ httpStatus: 404, code: 404, message: '不存在', kind: ErrorKind.NOT_FOUND }))
    await flushPromises()
    expect(wrapper.text()).toContain(outcome === 'READY' ? '本场说明' : outcome === 'FAILED' ? REPORT_COPY.failedTitle : '面试记录不存在或无权查看')
    expect(wrapper.find('.report-evidence').exists()).toBe(outcome !== '404')
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it.each(['resolve', 'reject'] as const)('ignores old status query that %ss after route change', async (outcome) => {
    const pending = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewDetail).mockImplementation(async (sessionId) => completedDetail({ sessionId }))
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper, router } = await mountReport()
    vi.mocked(fetchInterviewReport).mockReturnValueOnce(pending.promise)
    await recoveryButton(wrapper)!.trigger('click')
    vi.mocked(fetchInterviewReport).mockResolvedValue(reportResponse({ sessionId: 42, summary: 'SESSION_42_REPORT' }))
    await router.push('/interviews/42/report')
    await flushPromises()
    if (outcome === 'resolve') pending.resolve(statusReport(InterviewReportStatus.GENERATING, 41))
    else pending.reject(new ApiError({ httpStatus: 404, code: 404, message: '不存在', kind: ErrorKind.NOT_FOUND }))
    await flushPromises()
    expect(wrapper.text()).toContain('SESSION_42_REPORT')
    expect(recoveryButton(wrapper)).toBeUndefined()
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it.each(['resolve', 'reject'] as const)('does not restart polling after unmounted query %ss', async (outcome) => {
    vi.useFakeTimers()
    const pending = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    vi.mocked(fetchInterviewReport).mockReturnValueOnce(pending.promise)
    await recoveryButton(wrapper)!.trigger('click')
    wrapper.unmount()
    if (outcome === 'resolve') pending.resolve(statusReport(InterviewReportStatus.GENERATING, 41))
    else pending.reject(new ApiError({ httpStatus: 0, code: 0, message: '超时', kind: ErrorKind.NETWORK }))
    await flushPromises()
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('shows missing-record page and stops polling after query 404', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    vi.mocked(fetchInterviewReport).mockRejectedValue(new ApiError({ httpStatus: 404, code: 404, message: '不存在', kind: ErrorKind.NOT_FOUND }))
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(REPORT_COPY.missingRecord)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('keeps manual query 401 on the global authentication path', async () => {
    const actualApi = await vi.importActual<typeof import('@/features/interview/api/interviewApi')>('@/features/interview/api/interviewApi')
    const previousAdapter = httpClient.defaults.adapter
    httpClient.defaults.adapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
        status: 401, statusText: 'Unauthorized', data: { code: 401, message: '未认证', data: null }, headers: {}, config,
      })
    }
    try {
      vi.mocked(fetchInterviewReport).mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
      const { wrapper, router, auth } = await mountReport()
      bindUnauthorizedNavigation(router)
      const vueErrors = vi.fn()
      wrapper.vm.$.appContext.config.errorHandler = vueErrors
      vi.mocked(fetchInterviewReport).mockImplementation(actualApi.fetchInterviewReport)
      await recoveryButton(wrapper)!.trigger('click')
      await flushPromises()
      await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
      expect(router.currentRoute.value.query.redirect).toBe('/interviews/41/report')
      expect(auth.accessToken).toBeNull()
      expect(generateInterviewReport).not.toHaveBeenCalled()
      for (const [error] of vueErrors.mock.calls) expect(error.kind).toBe(ErrorKind.UNAUTHENTICATED)
    } finally { httpClient.defaults.adapter = previousAdapter }
  })


  it('stops polling on FAILED', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport)
      .mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
      .mockResolvedValueOnce(statusReport(InterviewReportStatus.FAILED, 41))
    const { wrapper } = await mountReport()
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
    await flushPromises()
    expect(wrapper.text()).toContain('上次报告生成未完成')
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 2)
    await flushPromises()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('stops polling on unmount', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 4)
    await flushPromises()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('stops the old poll when the route sessionId changes', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockImplementation(async (sessionId) =>
      statusReport(InterviewReportStatus.GENERATING, sessionId),
    )
    vi.mocked(fetchInterviewDetail).mockImplementation(async (sessionId) =>
      completedDetail({ sessionId, status: InterviewSessionStatus.COMPLETED }),
    )
    const { router } = await mountReport('/interviews/41/report')
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
    await router.push('/interviews/42/report')
    await flushPromises()
    const afterSwitch = vi.mocked(fetchInterviewReport).mock.calls.length
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
    await flushPromises()
    const calls = vi.mocked(fetchInterviewReport).mock.calls
    expect(calls.length).toBe(afterSwitch + 1)
    expect(calls.at(-1)?.[0]).toBe(42)
  })

  it('does not let an old report GET overwrite the new session after a route change', async () => {
    const pendingFortyOne = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewDetail).mockImplementation(async (sessionId) =>
      completedDetail({ sessionId, status: InterviewSessionStatus.COMPLETED }),
    )
    vi.mocked(fetchInterviewReport).mockImplementation((sessionId) => {
      if (sessionId === 41) {
        return pendingFortyOne.promise
      }
      return Promise.resolve(
        reportResponse({
          sessionId,
          summary: `SESSION_${sessionId}_SUMMARY`,
          overallScore: 71,
        }),
      )
    })
    const { wrapper, router } = await mountApp('/interviews/41/report', (auth) => authenticate(auth))
    expect(wrapper.text()).not.toContain('SESSION_41_SUMMARY')
    await router.push('/interviews/42/report')
    await flushPromises()
    expect(wrapper.text()).toContain('SESSION_42_SUMMARY')
    pendingFortyOne.resolve(
      reportResponse({
        sessionId: 41,
        summary: 'SESSION_41_SUMMARY',
        overallScore: 99,
      }),
    )
    await flushPromises()
    expect(router.currentRoute.value.params.sessionId).toBe('42')
    expect(wrapper.text()).toContain('SESSION_42_SUMMARY')
    expect(wrapper.text()).not.toContain('SESSION_41_SUMMARY')
    expect(wrapper.text()).not.toContain('99')
  })

  it('does not let an old generate POST overwrite the new session after a route change', async () => {
    const pendingPost = deferred<InterviewReportResponse>()
    vi.mocked(fetchInterviewDetail).mockImplementation(async (sessionId) =>
      completedDetail({ sessionId, status: InterviewSessionStatus.COMPLETED }),
    )
    vi.mocked(fetchInterviewReport).mockImplementation(async (sessionId) => {
      if (sessionId === 41) {
        return statusReport(InterviewReportStatus.NOT_STARTED, 41)
      }
      return reportResponse({
        sessionId,
        summary: `SESSION_${sessionId}_SUMMARY`,
        overallScore: 71,
      })
    })
    vi.mocked(generateInterviewReport).mockReturnValue(pendingPost.promise)
    const { wrapper, router } = await mountReport('/interviews/41/report')
    const button = generateButton(wrapper)!
    await Promise.all([button.trigger('click'), button.trigger('click')])
    expect(generateInterviewReport).toHaveBeenCalledExactlyOnceWith(41)
    expect(wrapper.get('[role="status"][aria-busy="true"]').text()).toContain(REPORT_COPY.generatingBusy)
    expect(wrapper.text()).not.toContain('本场说明')
    await router.push('/interviews/42/report')
    await flushPromises()
    expect(wrapper.text()).toContain('SESSION_42_SUMMARY')
    pendingPost.resolve(
      reportResponse({
        sessionId: 41,
        summary: 'SESSION_41_POST',
        overallScore: 99,
      }),
    )
    await flushPromises()
    expect(router.currentRoute.value.params.sessionId).toBe('42')
    expect(wrapper.text()).toContain('SESSION_42_SUMMARY')
    expect(wrapper.text()).not.toContain('SESSION_41_POST')
    expect(wrapper.text()).not.toContain('99')
  })

  it('does not redirect the new session when an old 409 realign returns', async () => {
    const pendingRealign = deferred<InterviewDetailResponse>()
    vi.mocked(fetchInterviewDetail).mockImplementation(async (sessionId) =>
      completedDetail({ sessionId, status: InterviewSessionStatus.COMPLETED }),
    )
    vi.mocked(fetchInterviewReport).mockImplementation(async (sessionId) => {
      if (sessionId === 41) {
        return statusReport(InterviewReportStatus.NOT_STARTED, 41)
      }
      return reportResponse({
        sessionId,
        summary: `SESSION_${sessionId}_SUMMARY`,
        overallScore: 71,
      })
    })
    const { wrapper, router } = await mountReport('/interviews/41/report')
    vi.mocked(fetchInterviewDetail).mockImplementation(async (sessionId) => {
      if (sessionId === 41) {
        return pendingRealign.promise
      }
      return completedDetail({ sessionId, status: InterviewSessionStatus.COMPLETED })
    })
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: '面试尚未完成，无法生成报告',
        kind: ErrorKind.CONFLICT,
      }),
    )
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    await router.push('/interviews/42/report')
    await flushPromises()
    expect(wrapper.text()).toContain('SESSION_42_SUMMARY')
    pendingRealign.resolve(inProgressDetail())
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('interview-report')
    expect(router.currentRoute.value.params.sessionId).toBe('42')
    expect(wrapper.text()).toContain('SESSION_42_SUMMARY')
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })

  it('shows RECOVERY_UNKNOWN when an uncertain POST is followed by a failed recovery GET', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('报告生成结果暂时无法确认。')
    expect(wrapper.text()).toContain('刷新报告状态')
    expect(wrapper.text()).not.toContain('正在生成报告')
    expect(wrapper.text()).not.toContain('报告正在生成')
  })

  it.each([
    {
      name: 'READY',
      report: reportResponse(),
      expected: '本场说明',
    },
    {
      name: 'GENERATING',
      report: statusReport(InterviewReportStatus.GENERATING, 41),
      expected: '报告正在生成',
    },
    {
      name: 'FAILED',
      report: statusReport(InterviewReportStatus.FAILED, 41),
      expected: '重新生成',
    },
    {
      name: 'NOT_STARTED',
      report: statusReport(InterviewReportStatus.NOT_STARTED, 41),
      expected: '生成报告',
    },
  ])('recovers RECOVERY_UNKNOWN with GET $name and does not POST', async ({ report, expected }) => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchInterviewReport).mockRejectedValueOnce(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('报告生成结果暂时无法确认。')
    vi.mocked(fetchInterviewReport).mockResolvedValueOnce(report)
    const refresh = wrapper.findAll('button').find((button) => button.text().includes('刷新报告状态'))
    await refresh?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(expected)
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('keeps RECOVERY_UNKNOWN when refresh GET fails again and still does not POST', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    const refresh = wrapper.findAll('button').find((button) => button.text().includes('刷新报告状态'))
    await refresh?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('报告生成结果暂时无法确认。')
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('consumes poll 401 without an unhandled rejection', async () => {
    vi.useFakeTimers()
    const unhandled: unknown[] = []
    const onUnhandled = (reason: unknown) => {
      unhandled.push(reason)
    }
    process.on('unhandledRejection', onUnhandled)
    vi.mocked(fetchInterviewReport)
      .mockResolvedValueOnce(statusReport(InterviewReportStatus.GENERATING, 41))
      .mockRejectedValue(
        new ApiError({
          httpStatus: 401,
          code: 401,
          message: '未认证',
          kind: ErrorKind.UNAUTHENTICATED,
        }),
      )
    try {
      await mountReport()
      await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
      await flushPromises()
      await Promise.resolve()
      expect(unhandled).toEqual([])
    } finally {
      process.off('unhandledRejection', onUnhandled)
    }
  })

  it('renders READY in Outcome → Meaning → Strength → Gap → Action → Evidence order', async () => {
    vi.mocked(fetchInterviewReport).mockResolvedValue(reportResponse())
    const { wrapper } = await mountReport()
    const text = wrapper.text()
    const outcome = text.indexOf('82.5')
    const meaning = text.indexOf('本场说明')
    const strength = text.indexOf('优势')
    const gap = text.indexOf('薄弱点')
    const action = text.indexOf('建议')
    const evidence = text.indexOf('过程证据')
    expect(outcome).toBeGreaterThan(-1)
    expect(meaning).toBeGreaterThan(outcome)
    expect(strength).toBeGreaterThan(meaning)
    expect(gap).toBeGreaterThan(strength)
    expect(action).toBeGreaterThan(gap)
    expect(evidence).toBeGreaterThan(action)
    expect(text).toContain('良好')
    expect(text).toContain('请说明 Spring Bean 的生命周期。')
    expect(text).not.toContain('radar')
  })

  it('does not render empty cards for READY with null fields', async () => {
    vi.mocked(fetchInterviewReport).mockResolvedValue(
      reportResponse({
        overallScore: null,
        result: null,
        summary: null,
        strengths: null,
        weaknesses: null,
        suggestions: null,
      }),
    )
    const { wrapper } = await mountReport()
    expect(wrapper.text()).toContain('报告数据暂时异常')
    expect(wrapper.text()).not.toContain('本场说明')
    expect(wrapper.find('.score-number').exists()).toBe(false)
  })

  it('POSTs once on generate success and adopts READY without a second POST', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockResolvedValue(reportResponse())
    await generateButton(wrapper)?.trigger('click')
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('本场说明')
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('recovers an uncertain POST by GET first and does not POST again', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchInterviewReport).mockResolvedValue(reportResponse())
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('本场说明')
  })

  it('treats 502 as GET-first recovery', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({
        httpStatus: 502,
        code: 502,
        message: '外部服务暂时不可用',
        kind: ErrorKind.EXTERNAL_SERVICE,
      }),
    )
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.GENERATING, 41))
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('报告正在生成')
  })

  it('turns 409 generating into GET polling', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: '报告正在生成中，请稍后重试',
        kind: ErrorKind.CONFLICT,
      }),
    )
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.GENERATING, 41))
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('报告正在生成')
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS)
    await flushPromises()
    expect(fetchInterviewReport.mock.calls.length).toBeGreaterThan(1)
  })

  it('realigns 409 not-completed by refetching Detail', async () => {
    const { wrapper, router } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: '面试尚未完成，无法生成报告',
        kind: ErrorKind.CONFLICT,
      }),
    )
    vi.mocked(fetchInterviewDetail).mockResolvedValue(inProgressDetail())
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('interview-session')
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })

  it('keeps FAILED after recovery GET and does not auto POST', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.FAILED, 41))
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('重新生成')
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('keeps NOT_STARTED after recovery GET and does not auto POST', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.NOT_STARTED, 41))
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('生成报告')
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('shows a service-unavailable message without pretending to generate', async () => {
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '报告生成服务当前不可用',
        kind: ErrorKind.VALIDATION,
      }),
    )
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('报告生成服务暂时不可用')
    expect(wrapper.text()).toContain('尚未生成报告')
    expect(wrapper.text()).not.toContain('报告正在生成')
  })

  it.each([
    { initial: InterviewReportStatus.NOT_STARTED, message: REPORT_SERVICE_DISABLED },
    { initial: InterviewReportStatus.FAILED, message: REPORT_SERVICE_DISABLED },
    { initial: InterviewReportStatus.NOT_STARTED, message: REPORT_SERVICE_NOT_ACCEPTING },
    { initial: InterviewReportStatus.FAILED, message: REPORT_SERVICE_NOT_ACCEPTING },
  ])('shows known 503 refusal from $initial without success or automatic retry: $message', async ({ initial, message }) => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(initial, 41))
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(new ApiError({ httpStatus: 503, code: 503, message }))
    await generateButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(message)
    expect(wrapper.text()).toContain(initial === InterviewReportStatus.FAILED ? REPORT_COPY.failedTitle : REPORT_COPY.notStartedTitle)
    expect(wrapper.text()).not.toContain(REPORT_COPY.generatingTitle)
    expect(wrapper.find('.report-summary').exists()).toBe(false)
    expect(wrapper.find('.saved-final-scores').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    expect(fetchInterviewReport).toHaveBeenCalledTimes(1)
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
  })

  it('keeps unknown 503 on GET-first recovery rather than assuming refusal', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountReport()
    vi.mocked(generateInterviewReport).mockRejectedValue(new ApiError({ httpStatus: 503, code: 503, message: 'gateway unavailable' }))
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.GENERATING, 41))
    await generateButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(fetchInterviewReport).toHaveBeenCalledTimes(2)
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain(REPORT_COPY.generatingTitle)
    expect(wrapper.find('.report-summary').exists()).toBe(false)
  })

  it('offers manual diagnosis for persistent GENERATING without claiming a running worker or reposting', async () => {
    vi.useFakeTimers()
    vi.mocked(fetchInterviewReport).mockResolvedValue(statusReport(InterviewReportStatus.GENERATING, 41))
    const { wrapper } = await mountReport()
    await vi.advanceTimersByTimeAsync(REPORT_POLL_INTERVAL_MS * 3)
    await recoveryButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('若长时间没有变化，请联系管理员核对')
    expect(wrapper.text()).not.toContain('系统正在后台生成')
    expect(wrapper.find('.report-summary').exists()).toBe(false)
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('uses generic missing copy for report 404', async () => {
    vi.mocked(fetchInterviewDetail).mockRejectedValue(
      new ApiError({
        httpStatus: 404,
        code: 404,
        message: '面试会话不存在',
        kind: ErrorKind.NOT_FOUND,
      }),
    )
    const { wrapper } = await mountReport('/interviews/99/report')
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
    expect(fetchInterviewReport).not.toHaveBeenCalled()
  })

  it('does not flash pending questions while Detail is still loading', async () => {
    const pending = deferred<ReturnType<typeof inProgressDetail>>()
    vi.mocked(fetchInterviewDetail).mockReturnValue(pending.promise)
    const { wrapper } = await mountApp('/interviews/21/report', (auth) => authenticate(auth))
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
    pending.resolve(inProgressDetail())
    await flushPromises()
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })
})
