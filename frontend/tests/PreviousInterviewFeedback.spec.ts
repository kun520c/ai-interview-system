import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { ApiError, ErrorKind } from '@/api/apiError'
import { fetchCurrentInterview, fetchInterviewDetail, submitInterviewAnswer } from '@/features/interview/api/interviewApi'
import { InterviewAnswerStatus, InterviewQuestionStatus } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import { deferred } from './helpers/deferred'
import {
  currentQuestion, currentSession, detailQuestion, evaluation, followUpDetailQuestion,
  inProgressDetail, PENDING_FUTURE_QUESTION_SECRET, submitResponse,
} from './helpers/interviewFixtures'

vi.mock('@/features/auth/api/authApi', () => ({ login: vi.fn(), registerAccount: vi.fn() }))
vi.mock('@/features/auth/api/userApi', () => ({ fetchCurrentUser: vi.fn(), changePassword: vi.fn() }))
vi.mock('@/features/interview/api/interviewApi', () => ({
  fetchCurrentInterview: vi.fn(), createInterview: vi.fn(), createOrResumeInterview: vi.fn(),
  submitInterviewAnswer: vi.fn(), fetchInterviewDetail: vi.fn(), fetchInterviewReport: vi.fn(),
  generateInterviewReport: vi.fn(),
}))
vi.mock('@/features/history/api/historyApi', () => ({ fetchInterviewHistory: vi.fn() }))

const currentId = 201

function advancedDetail() {
  return inProgressDetail({
    questions: [
      detailQuestion({ interviewQuestionId: 88, displayOrder: 1, questionContent: '上一主问题' }),
      followUpDetailQuestion({
        interviewQuestionId: 89, displayOrder: 2, questionContent: '上一道追问',
        answer: { answerContent: '已持久化的追问答案', answerStatus: InterviewAnswerStatus.EVALUATED, submittedAt: '2026-11-14T10:00:00' },
        evaluation: evaluation({ totalScore: 78, correction: '已保存的追问评价' }),
      }),
      detailQuestion({
        interviewQuestionId: currentId, displayOrder: 3, questionContent: '当前问题',
        status: InterviewQuestionStatus.WAITING_ANSWER, answer: null, evaluation: null,
      }),
      detailQuestion({
        interviewQuestionId: 301, displayOrder: 5, questionContent: PENDING_FUTURE_QUESTION_SECRET,
        status: InterviewQuestionStatus.PENDING, answer: null, evaluation: null,
      }),
    ],
  })
}

async function mountAdvanced() {
  vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession({
    currentQuestion: currentQuestion({ interviewQuestionId: currentId, content: '当前问题' }),
  }))
  const app = await mountApp('/interview', (auth) => authenticate(auth))
  await flushPromises()
  return app
}

describe('previous feedback after an unconfirmed response', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewDetail).mockReset()
    vi.mocked(submitInterviewAnswer).mockReset()
  })

  it(
    'loads the last saved evaluation without posting or needing local state', async () => {
      vi.mocked(fetchInterviewDetail).mockResolvedValue(advancedDetail())
      const { wrapper } = await mountAdvanced()
      await wrapper.get('#interview-answer').setValue('下一题尚未提交的草稿')
      expect(fetchInterviewDetail).not.toHaveBeenCalled()
      await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
      await flushPromises()
      expect(fetchInterviewDetail).toHaveBeenCalledTimes(1)
      expect(fetchInterviewDetail).toHaveBeenCalledWith(21)
      expect(wrapper.text()).toContain('上一道追问')
      expect(wrapper.text()).toContain('已持久化的追问答案')
      expect(wrapper.text()).toContain('已保存的追问评价')
      expect(wrapper.text()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
      expect(submitInterviewAnswer).not.toHaveBeenCalled()
      await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
      expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).value).toBe('下一题尚未提交的草稿')
      wrapper.unmount()
    },
  )

  it('recovers after a lost response and remount with an advanced server session', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    vi.mocked(submitInterviewAnswer).mockRejectedValue(new ApiError({
      httpStatus: 0, code: 0, kind: ErrorKind.NETWORK, message: 'response lost',
    }))
    const first = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    await first.wrapper.get('#interview-answer').setValue('已提交的回答')
    const submit = first.wrapper.findAll('button').find(button => button.text().includes('提交答案'))!
    await submit.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
    expect(first.wrapper.text()).toContain('本次提交结果暂时无法确认')
    first.wrapper.unmount()

    // Model a newly opened browser with no saved submit record or feedback gate.
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(fetchInterviewDetail).mockResolvedValue(inProgressDetail({ questions: [
      detailQuestion({ interviewQuestionId: 88, displayOrder: 1, questionContent: '已经评价的题目',
        answer: { answerContent: '已提交的回答', answerStatus: InterviewAnswerStatus.EVALUATED, submittedAt: '2026-11-14T10:00:00' },
        evaluation: evaluation({ correction: '已保存的评价' }) }),
      detailQuestion({ interviewQuestionId: currentId, displayOrder: 3, questionContent: '当前问题',
        status: InterviewQuestionStatus.WAITING_ANSWER, answer: null, evaluation: null }),
    ] }))
    const resumed = await mountAdvanced()
    await resumed.wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
    await flushPromises()
    expect(resumed.wrapper.text()).toContain('已经评价的题目')
    expect(resumed.wrapper.text()).toContain('已提交的回答')
    expect(resumed.wrapper.text()).toContain('已保存的评价')
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
  })

  it('ignores incomplete and future items and tells the user when no previous evaluation exists', async () => {
    const detail = advancedDetail()
    detail.questions = detail.questions.filter(item => item.interviewQuestionId >= currentId)
    vi.mocked(fetchInterviewDetail).mockResolvedValue(detail)
    const { wrapper } = await mountAdvanced()
    await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无上一题反馈')
    expect(wrapper.text()).not.toContain(PENDING_FUTURE_QUESTION_SECRET)
    expect(submitInterviewAnswer).not.toHaveBeenCalled()
  })

  it('shows the main answer INITIAL evaluation when the current question is its follow-up', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession({
      currentQuestion: currentQuestion({ interviewQuestionId: 89, questionType: 'FOLLOW_UP', content: '当前追问' }),
    }))
    vi.mocked(fetchInterviewDetail).mockResolvedValue(inProgressDetail({ questions: [
      detailQuestion({ interviewQuestionId: 88, displayOrder: 1, questionContent: '已经完成的主问题',
        evaluation: evaluation({ correction: '已保存的阶段评价' }) }),
      followUpDetailQuestion({ interviewQuestionId: 89, displayOrder: 2,
        status: InterviewQuestionStatus.WAITING_ANSWER, answer: null, evaluation: null }),
    ] }))
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已经完成的主问题')
    expect(wrapper.text()).toContain('阶段性评价')
    expect(wrapper.text()).toContain('已保存的阶段评价')
    expect(submitInterviewAnswer).not.toHaveBeenCalled()
  })

  it('fails closed when the returned session or current question does not match', async () => {
    vi.mocked(fetchInterviewDetail).mockResolvedValue(advancedDetail())
    const { wrapper } = await mountAdvanced()
    vi.mocked(fetchInterviewDetail).mockResolvedValueOnce(inProgressDetail({ sessionId: 999, questions: advancedDetail().questions }))
    await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('上一题反馈暂时无法加载')
    expect(wrapper.text()).not.toContain('已持久化的追问答案')
  })

  it('discards a response that arrives after the signed-in user changes', async () => {
    const waiting = deferred<ReturnType<typeof advancedDetail>>()
    vi.mocked(fetchInterviewDetail).mockReturnValue(waiting.promise)
    const { wrapper, auth } = await mountAdvanced()
    await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
    auth.userId = 999
    await flushPromises()
    waiting.resolve(advancedDetail())
    await flushPromises()
    expect(wrapper.text()).not.toContain('已持久化的追问答案')
    expect(wrapper.text()).not.toContain('已保存的追问评价')
  })

  it('discards an earlier detail response when normal submission opens a new evaluation', async () => {
    const waiting = deferred<ReturnType<typeof advancedDetail>>()
    vi.mocked(fetchInterviewDetail).mockReturnValue(waiting.promise)
    vi.mocked(submitInterviewAnswer).mockResolvedValue(submitResponse())
    const { wrapper } = await mountAdvanced()
    await wrapper.get('[data-testid="previous-feedback-toggle"]').trigger('click')
    await wrapper.get('#interview-answer').setValue('当前题目的答案')
    await wrapper.findAll('button').find(button => button.text().includes('提交答案'))!.trigger('click')
    await flushPromises()
    waiting.resolve(advancedDetail())
    await flushPromises()
    expect(wrapper.text()).not.toContain('已持久化的追问答案')
    expect(wrapper.text()).not.toContain('已保存的追问评价')
    expect(wrapper.text()).toContain('本题反馈')
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
  })
})
