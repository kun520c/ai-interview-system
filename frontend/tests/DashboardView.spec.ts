import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { IdentityStatus } from '@/types/auth'
import { InterviewQuestionType, InterviewReportStatus } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import { currentSession, emptyHistoryPage, historyItem } from './helpers/interviewFixtures'
import { deferred } from './helpers/deferred'

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

describe('DashboardView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
  })

  it('shows the start CTA when there is no active interview', async () => {
    const { wrapper, router } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('开始一次模拟面试')
    expect(wrapper.text()).toContain('选择难度后，系统会进入连续面试流程。')
    const start = wrapper.get('.training-cta-link')
    expect(start.text()).toBe('开始面试')
    expect(start.attributes('href')).toBe('/interviews/new')
    await start.trigger('click')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-create')
    })
  })

  it('shows continue with the derived main number and progress', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({ total: 1, totalPages: 1, items: [historyItem()] }),
    )
    const { wrapper, router } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('继续当前面试')
    expect(wrapper.text()).toContain('第 3 / 7 题')
    expect(wrapper.text()).toContain('Spring')
    expect(wrapper.text()).toContain('Bean 生命周期')
    const bar = wrapper.get('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('2')
    expect(bar.attributes('aria-valuemin')).toBe('0')
    expect(bar.attributes('aria-valuemax')).toBe('7')
    const continueLink = wrapper.get('.training-cta-link')
    expect(continueLink.text()).toBe('继续面试')
    expect(continueLink.attributes('href')).toBe('/interview')
    await continueLink.trigger('click')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
    expect(wrapper.text()).not.toContain('已为你继续未完成的面试')
  })

  it('does not increment the main number for FOLLOW_UP', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(
      currentSession({
        completedQuestionCount: 2,
        currentQuestion: {
          interviewQuestionId: 89,
          category: 'SPRING',
          knowledgePoint: 'AOP',
          questionType: InterviewQuestionType.FOLLOW_UP,
          content: '追问内容',
        },
      }),
    )
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('第 3 / 7 题')
    expect(wrapper.text()).toContain('追问')
    expect(wrapper.text()).not.toContain('第 4 / 7 题')
    const bar = wrapper.get('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('2')
  })

  it('keeps history visible when current fails', async () => {
    vi.mocked(fetchCurrentInterview).mockRejectedValue(
      new ApiError({ httpStatus: 500, code: 500, message: '系统异常，请稍后重试', kind: ErrorKind.UNEXPECTED }),
    )
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({ total: 1, totalPages: 1, items: [historyItem()] }),
    )
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('当前面试状态暂时无法加载')
    expect(wrapper.text()).toContain('最近完成的面试')
    expect(wrapper.text()).toContain('MEDIUM')
    expect(wrapper.text()).toContain('82.5')
  })

  it('keeps current usable when history fails', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockRejectedValue(
      new ApiError({ httpStatus: 500, code: 500, message: '系统异常，请稍后重试', kind: ErrorKind.UNEXPECTED }),
    )
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('开始一次模拟面试')
    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).not.toContain('当前面试状态暂时无法加载')
  })

  it('shows an honest empty history state', async () => {
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('还没有完成的面试')
    expect(wrapper.text()).toContain('完成第一场后，这里会保存你的面试记录和报告入口。')
    expect(wrapper.text()).toContain('开始第一次面试')
    expect(wrapper.text()).not.toContain('Alice')
    expect(wrapper.text()).not.toContain('林可')
  })

  it('shows a READY score and report action', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        total: 1,
        totalPages: 1,
        items: [historyItem({ totalScore: 90, reportStatus: InterviewReportStatus.READY })],
      }),
    )
    const { wrapper, router } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('90')
    expect(wrapper.text()).toContain('优秀')
    expect(wrapper.text()).toContain('已生成')
    const action = wrapper.get('.recent-action')
    expect(action.text()).toBe('查看报告')
    expect(action.attributes('aria-label')).toContain('查看报告')
    expect(action.attributes('href')).toBe('/interviews/41/report')
    await action.trigger('click')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-report')
    })
  })

  it('does not display a null totalScore as 0', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
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
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('报告未生成')
    expect(wrapper.text()).toContain('生成报告')
    expect(wrapper.get('.recent-score').text()).not.toMatch(/^0$/)
  })

  it('consumes a rejected history load so 401 does not become unhandled', async () => {
    vi.mocked(fetchInterviewHistory).mockRejectedValue(
      new ApiError({
        httpStatus: 401,
        code: 401,
        message: '未认证或访问令牌无效',
        kind: ErrorKind.UNAUTHENTICATED,
      }),
    )
    await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    await flushPromises()
  })

  it('does not invent a username when identity is unavailable', async () => {
    const pending = deferred<null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pending.promise)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
    const { wrapper } = await mountApp('/', (auth) => {
      auth.ready = true
      auth.accessToken = 'token'
      auth.userId = 3
      auth.me = null
      auth.identityStatus = IdentityStatus.UNAVAILABLE
    })
    pending.resolve(null)
    await flushPromises()
    expect(wrapper.text()).toContain('工作台')
    expect(wrapper.text()).not.toContain('你好，')
    expect(wrapper.text()).not.toContain('林可')
    expect(wrapper.text()).not.toContain('你好，用户')
    expect(wrapper.text()).toContain('开始一次模拟面试')
  })

  it('shows READY with a null score as abnormal, never 未生成', async () => {
    vi.mocked(fetchInterviewHistory).mockResolvedValue(
      emptyHistoryPage({
        total: 1,
        totalPages: 1,
        items: [historyItem({ totalScore: null, reportStatus: InterviewReportStatus.READY })],
      }),
    )
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('报告数据暂时异常')
    expect(wrapper.text()).not.toContain('报告未生成')
  })
})
