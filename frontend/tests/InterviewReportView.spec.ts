import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import {
  fetchCurrentInterview,
  fetchInterviewDetail,
  fetchInterviewReport,
  generateInterviewReport,
} from '@/features/interview/api/interviewApi'
import { REPORT_POLL_INTERVAL_MS } from '@/features/interview/composables/useInterviewReport'
import { authenticate, mountApp } from './helpers/mountApp'
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

describe('InterviewReportView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
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
    await generateButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(generateInterviewReport).toHaveBeenCalledTimes(1)
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
