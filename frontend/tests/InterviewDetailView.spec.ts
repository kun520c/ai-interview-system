import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import {
  fetchCurrentInterview,
  fetchInterviewDetail,
  fetchInterviewReport,
  generateInterviewReport,
} from '@/features/interview/api/interviewApi'
import { authenticate, mountApp } from './helpers/mountApp'
import { deferred } from './helpers/deferred'
import {
  completedDetail,
  createdDetail,
  currentSession,
  detailQuestion,
  emptyHistoryPage,
  followUpDetailQuestion,
  inProgressDetail,
  cancelledDetail,
  CANCELLED_QUESTION_SECRET,
  PENDING_FUTURE_QUESTION_SECRET,
} from './helpers/interviewFixtures'
import { InterviewQuestionStatus, InterviewSessionStatus } from '@/types/enums'

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

describe('InterviewDetailView', () => {
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
  })

  it('renders a completed timeline ordered by displayOrder', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(
      completedDetail({
        questions: [
          followUpDetailQuestion(),
          detailQuestion({ displayOrder: 1, questionContent: 'MAIN_QUESTION_BODY' }),
        ],
      }),
    )
    const { wrapper } = await mountApp('/interviews/41', (auth) => authenticate(auth))
    await flushPromises()
    const text = wrapper.text()
    expect(text.indexOf('MAIN_QUESTION_BODY')).toBeGreaterThan(-1)
    expect(text.indexOf('MAIN_QUESTION_BODY')).toBeLessThan(text.indexOf('初始化回调失败时容器会怎样处理？'))
    expect(text).toContain('主问题')
    expect(text).toContain('追问')
    expect(text).toContain('先实例化，再注入依赖，然后初始化。')
    expect(text).toContain('说明了实例化与依赖注入的顺序。')
    expect(text).not.toContain('requestId')
    expect(text).not.toContain('rawResult')
    expect(text).not.toContain('retrievalBatchId')
    expect(text).not.toContain('followUpRecommended')
    expect(text).not.toContain('errorCode')
  })

  it('redirects IN_PROGRESS before rendering pending question content', async () => {
    const pending = deferred<ReturnType<typeof inProgressDetail>>()
    vi.mocked(fetchInterviewDetail).mockReturnValue(pending.promise)
    const { wrapper, router } = await mountApp('/interviews/21', (auth) => authenticate(auth))
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
    pending.resolve(inProgressDetail())
    await flushPromises()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
    expect(wrapper.text()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })

  it('redirects CREATED without rendering the timeline', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(
      createdDetail({
        questions: [
          detailQuestion({
            status: InterviewQuestionStatus.PENDING,
            questionContent: PENDING_FUTURE_QUESTION_SECRET,
            answer: null,
            evaluation: null,
          }),
        ],
      }),
    )
    const { wrapper, router } = await mountApp('/interviews/31', (auth) => authenticate(auth))
    await flushPromises()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
    expect(wrapper.html()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
  })

  it('does not render a CANCELLED timeline', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(cancelledDetail())
    const { wrapper, router } = await mountApp('/interviews/51', (auth) => authenticate(auth))
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('interview-detail')
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
    expect(wrapper.html()).not.toContain(CANCELLED_QUESTION_SECRET)
    expect(wrapper.find('.timeline-item').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('查看报告')
  })

  it('does not request the API for an invalid sessionId', async () => {
    const { wrapper } = await mountApp('/interviews/abc', (auth) => authenticate(auth))
    await flushPromises()
    expect(fetchInterviewDetail).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
  })

  it.each(['0', '-1', '1.2'])('rejects %s without calling the API', async (sessionId) => {
    const { wrapper } = await mountApp(`/interviews/${sessionId}`, (auth) => authenticate(auth))
    await flushPromises()
    expect(fetchInterviewDetail).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
  })

  it('uses a generic missing copy for 404', async () => {
    vi.mocked(fetchInterviewDetail).mockRejectedValue(
      new ApiError({
        httpStatus: 404,
        code: 404,
        message: '面试会话不存在',
        kind: ErrorKind.NOT_FOUND,
      }),
    )
    const { wrapper } = await mountApp('/interviews/99', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('面试记录不存在或无权查看')
    expect(wrapper.text()).toContain('返回历史')
  })

  it('does not treat COMPLETED as a live session', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(
      completedDetail({ status: InterviewSessionStatus.COMPLETED }),
    )
    const { router } = await mountApp('/interviews/41', (auth) => authenticate(auth))
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('interview-detail')
  })

  it('does not render a completed empty timeline as a ready page', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(completedDetail({ questions: [] }))
    const { wrapper } = await mountApp('/interviews/41', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('这场已完成面试缺少题目记录，无法展示复盘。')
    expect(wrapper.text()).toContain('重试')
    expect(wrapper.text()).toContain('返回历史')
    expect(wrapper.find('.timeline-item').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('面试复盘')
  })

  it('does not redirect to the live session after leaving detail', async () => {
    const pending = deferred<ReturnType<typeof inProgressDetail>>()
    vi.mocked(fetchInterviewDetail).mockReturnValue(pending.promise)
    const { router } = await mountApp('/interviews/21', (auth) => authenticate(auth))
    await router.push('/interviews?page=1&pageSize=10')
    await flushPromises()
    pending.resolve(inProgressDetail())
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('interview-history')
  })
})
