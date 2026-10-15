import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { fetchCurrentInterview, fetchInterviewDetail, fetchInterviewReport, generateInterviewReport } from '@/features/interview/api/interviewApi'
import { authenticate, mountApp } from './helpers/mountApp'
import { currentSession, emptyHistoryPage, historyItem } from './helpers/interviewFixtures'
import { InterviewReportStatus } from '@/types/enums'

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

describe('HistoryView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchInterviewDetail).mockReset()
    vi.mocked(fetchInterviewReport).mockReset()
    vi.mocked(generateInterviewReport).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage({ page: 1, pageSize: 10 }))
  })

  it('loads history with page and pageSize from the URL', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 2,
        pageSize: 10,
        total: 12,
        totalPages: 2,
        items: [historyItem({ sessionId: 51 })],
      }),
    )
    const { wrapper } = await mountApp('/interviews?page=2&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(fetchInterviewHistory).toHaveBeenCalledWith(2, 10)
    expect(wrapper.text()).toContain('MEDIUM')
    expect(wrapper.text()).toContain('查看复盘')
  })

  it('canonicalizes invalid query without sending illegal API values', async () => {
    const { router } = await mountApp('/interviews?page=0&pageSize=99', (auth) => authenticate(auth))
    await flushPromises()
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ page: '1', pageSize: '50' })
    expect(fetchInterviewHistory).toHaveBeenCalledWith(1, 50)
    expect(fetchInterviewHistory).not.toHaveBeenCalledWith(0, 99)
    expect(fetchInterviewHistory).not.toHaveBeenCalledWith(0, 10)
  })

  it('shows an honest empty state', async () => {
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('还没有完成的面试')
    expect(wrapper.text()).toContain('完成第一场后，这里会保存你的面试记录和报告入口。')
    expect(wrapper.text()).toContain('开始面试')
  })

  it('uses continue CTA when current interview exists', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('继续当前面试')
  })

  it('shows a READY score and report navigation only', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 1,
        pageSize: 10,
        total: 1,
        totalPages: 1,
        items: [historyItem({ totalScore: 82.5, reportStatus: InterviewReportStatus.READY })],
      }),
    )
    const { wrapper, router } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('82.5')
    const report = wrapper.get('a[href="/interviews/41/report"]')
    expect(report.text()).toBe('查看报告')
    await report.trigger('click')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-report')
    })
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('does not display a null totalScore as 0 for NOT_STARTED', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 1,
        pageSize: 10,
        total: 1,
        totalPages: 1,
        items: [
          historyItem({
            totalScore: null,
            reportStatus: InterviewReportStatus.NOT_STARTED,
          }),
        ],
      }),
    )
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('报告未生成')
    expect(wrapper.text()).toContain('生成报告')
    expect(wrapper.text()).not.toMatch(/(^|\s)0(\s|$)/)
    expect(wrapper.text()).not.toContain('0.0')
    expect(wrapper.text()).not.toContain('--')
  })

  it('shows GENERATING without a fake score', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 1,
        pageSize: 10,
        total: 1,
        totalPages: 1,
        items: [historyItem({ totalScore: null, reportStatus: InterviewReportStatus.GENERATING })],
      }),
    )
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('报告生成中')
    expect(wrapper.text()).toContain('查看状态')
  })

  it('shows FAILED retry copy and navigates to report', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 1,
        pageSize: 10,
        total: 1,
        totalPages: 1,
        items: [historyItem({ totalScore: null, reportStatus: InterviewReportStatus.FAILED })],
      }),
    )
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('报告生成失败')
    expect(wrapper.text()).toContain('重新生成报告')
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('updates the URL when pagination changes', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 1,
        pageSize: 10,
        total: 12,
        totalPages: 2,
        items: [historyItem()],
      }),
    )
    const { wrapper, router } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    const next = wrapper.findAll('button').find((button) => button.text() === '下一页')
    await next?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ page: '2', pageSize: '10' })
  })

  it('restores the previous page on browser back', async () => {
    vi.mocked(fetchInterviewHistory).mockImplementation(async (page) =>
      emptyHistoryPage({
        page,
        pageSize: 10,
        total: 12,
        totalPages: 2,
        items: [historyItem({ sessionId: page === 1 ? 41 : 52 })],
      }),
    )
    const { wrapper, router } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    const next = wrapper.findAll('button').find((button) => button.text() === '下一页')
    await next?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')
    router.back()
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('1')
    expect(fetchInterviewHistory).toHaveBeenCalledWith(1, 10)
  })

  it('keeps 401 handling global', async () => {
    vi.mocked(fetchInterviewHistory).mockRejectedValue(
      new ApiError({
        httpStatus: 401,
        code: 401,
        message: '未认证或访问令牌无效',
        kind: ErrorKind.UNAUTHENTICATED,
      }),
    )
    await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    await flushPromises()
  })

  it('keeps a 500 as a local retry', async () => {
    vi.mocked(fetchInterviewHistory).mockRejectedValue(
      new ApiError({
        httpStatus: 500,
        code: 500,
        message: '系统异常，请稍后重试',
        kind: ErrorKind.UNEXPECTED,
      }),
    )
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('历史记录暂时无法加载')
    expect(wrapper.text()).toContain('重试')
  })

  it('shows READY with a null score as abnormal, never 未生成', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        page: 1,
        pageSize: 10,
        total: 1,
        totalPages: 1,
        items: [historyItem({ totalScore: null, reportStatus: InterviewReportStatus.READY })],
      }),
    )
    const { wrapper } = await mountApp('/interviews?page=1&pageSize=10', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('报告数据暂时异常')
    expect(wrapper.text()).not.toContain('报告未生成')
  })

  it('does not canonicalize a stale history query after leaving the route', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
    const { router } = await mountApp('/profile', (auth) => authenticate(auth))
    await flushPromises()
    void router.push('/interviews?page=0&pageSize=99')
    await router.push('/profile')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('profile')
    expect(router.currentRoute.value.fullPath).toBe('/profile')
  })
})
