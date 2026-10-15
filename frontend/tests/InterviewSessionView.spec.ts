import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { persistSubmitRecord } from '@/features/interview/storage/submitRecordStorage'
import { readEvaluationGate, writeEvaluationGate } from '@/features/interview/storage/evaluationGateStorage'
import { fetchCurrentInterview, fetchInterviewDetail, submitInterviewAnswer, generateInterviewReport } from '@/features/interview/api/interviewApi'
import { useCurrentInterviewStore } from '@/features/interview/stores/currentInterview'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { armInterviewResumeNotice, resetInterviewResumeNoticeForTests } from '@/features/interview/navigation/sessionNavigationContext'
import { SESSION_COPY } from '@/features/interview/copy/sessionCopy'
import { ANSWER_CONFLICT_MESSAGE } from '@/features/interview/utils/submissionRecovery'
import { RecoveryBlockedReason } from '@/features/interview/types/submitRecord'
import { DecisionAction, EvaluationPhase, InterviewSessionStatus, UserRole } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import {
  currentSession,
  emptyHistoryPage,
  evaluation,
  evaluationGate,
  followUpQuestion,
  submitResponse,
} from './helpers/interviewFixtures'
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

const EXACT_ANSWER = '  HashMap 不是线程安全的。\nConcurrentHashMap 才是。  '

async function mountSession() {
  vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
  const app = await mountApp('/interview', (auth) => authenticate(auth))
  await flushPromises()
  return app
}

async function typeAnswer(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper'], value: string) {
  await wrapper.get('#interview-answer').setValue(value)
}

function submitButton(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper']) {
  return wrapper.findAll('button').find((button) => button.text().includes('提交答案'))
}

describe('InterviewSessionView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    resetInterviewResumeNoticeForTests()
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(submitInterviewAnswer).mockReset()
    vi.mocked(fetchInterviewDetail).mockReset()
    vi.mocked(generateInterviewReport).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
  })

  it('shows the current question split workspace', async () => {
    const { wrapper } = await mountSession()
    expect(wrapper.text()).toContain('请说明 Spring Bean 的生命周期。')
    expect(wrapper.text()).toContain('第 3 / 7 题')
    expect(wrapper.text()).toContain('主问题')
    expect(wrapper.text()).toContain('你的回答')
    expect(wrapper.get('#interview-answer').element.tagName).toBe('TEXTAREA')
    expect(fetchInterviewDetail).not.toHaveBeenCalled()
  })

  it('persists the durable record before POST and keeps exact content', async () => {
    const order: string[] = []
    const originalSetItem = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key, value) {
      if (String(key).startsWith('interview.submit')) {
        order.push('persist')
      }
      return originalSetItem.call(this, key, value)
    })
    vi.mocked(submitInterviewAnswer).mockImplementation(async (_sessionId, request) => {
      order.push('post')
      expect(request.answerContent).toBe(EXACT_ANSWER)
      return submitResponse()
    })
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(order[0]).toBe('persist')
    expect(order.indexOf('persist')).toBeLessThan(order.indexOf('post'))
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain(EXACT_ANSWER)
    expect((wrapper.get('pre').element as HTMLElement).textContent).toBe(EXACT_ANSWER)
  })

  it('does not POST when persist fails', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((key) => {
      if (String(key).startsWith('interview.submit')) {
        throw new Error('quota')
      }
    })
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain(SESSION_COPY.persistUnavailable)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(false)
  })

  it('does not persist or POST a blank or oversized answer', async () => {
    const { wrapper } = await mountSession()
    const blankSubmit = submitButton(wrapper)
    expect(blankSubmit).toBeDefined()
    expect((blankSubmit!.element as HTMLButtonElement).disabled).toBe(true)
    await blankSubmit!.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).not.toHaveBeenCalled()
    expect(localStorage.getItem('interview.submit.3.21.88')).toBeNull()

    await typeAnswer(wrapper, 'a'.repeat(65_536))
    const oversizeSubmit = submitButton(wrapper)!
    expect((oversizeSubmit.element as HTMLButtonElement).disabled).toBe(true)
    await oversizeSubmit.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).not.toHaveBeenCalled()
    expect(localStorage.getItem('interview.submit.3.21.88')).toBeNull()
  })

  it('uses exactly one requestId for a rapid double click', async () => {
    const pending = deferred<ReturnType<typeof submitResponse>>()
    vi.mocked(submitInterviewAnswer).mockReturnValue(pending.promise)
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    const button = submitButton(wrapper)!
    await button.trigger('click')
    await button.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
    const requestId = vi.mocked(submitInterviewAnswer).mock.calls[0][1].requestId
    pending.resolve(submitResponse())
    await flushPromises()
    expect(requestId).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('retries network and 502 with the same pair', async () => {
    vi.mocked(submitInterviewAnswer)
      .mockRejectedValueOnce(
        new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
      )
      .mockRejectedValueOnce(
        new ApiError({ httpStatus: 502, code: 502, message: '外部服务暂时不可用', kind: ErrorKind.EXTERNAL_SERVICE }),
      )
      .mockResolvedValueOnce(submitResponse())
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.pendingTitle)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    const retry = wrapper.findAll('button').find((button) => button.text().includes('按原答案重新获取评价'))
    await retry!.trigger('click')
    await flushPromises()
    await retry!.trigger('click')
    await flushPromises()
    const calls = vi.mocked(submitInterviewAnswer).mock.calls
    expect(calls).toHaveLength(3)
    expect(calls[0][1]).toEqual(calls[1][1])
    expect(calls[1][1]).toEqual(calls[2][1])
    expect(calls[0][1].answerContent).toBe(EXACT_ANSWER)
  })

  it('retries 409 evaluating with the same pair and keeps the snapshot locked', async () => {
    vi.mocked(submitInterviewAnswer)
      .mockRejectedValueOnce(
        new ApiError({
          httpStatus: 409,
          code: 409,
          message: ANSWER_CONFLICT_MESSAGE.EVALUATING,
          kind: ErrorKind.CONFLICT,
        }),
      )
      .mockResolvedValueOnce(submitResponse())
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.evaluatingHint)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    await wrapper.findAll('button').find((button) => button.text().includes('按原答案重新获取评价'))!.trigger('click')
    await flushPromises()
    const calls = vi.mocked(submitInterviewAnswer).mock.calls
    expect(calls).toHaveLength(2)
    expect(calls[0][1]).toEqual(calls[1][1])
  })

  it('shows NEXT_MAIN CTA after evaluation', async () => {
    vi.mocked(submitInterviewAnswer).mockResolvedValue(
      submitResponse({ nextAction: DecisionAction.NEXT_MAIN, nextQuestion: followUpQuestion({ questionType: 'MAIN', content: '下一题泄漏' }) }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('下一题')
    expect(wrapper.text()).not.toContain('下一题泄漏')
    expect(wrapper.text()).not.toContain('继续追问')
  })

  it('keeps the dispatched pair when evaluation service is unavailable', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '面试评价服务当前不可用',
        kind: ErrorKind.VALIDATION,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.serviceUnavailable)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(JSON.parse(localStorage.getItem('interview.submit.3.21.88')!).requestId).toBeTruthy()
  })

  it('shows submitting copy while the POST is in flight', async () => {
    const pending = deferred<ReturnType<typeof submitResponse>>()
    vi.mocked(submitInterviewAnswer).mockReturnValue(pending.promise)
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.submittingStatus)
    expect(wrapper.text()).toContain(SESSION_COPY.submittingHint)
    pending.resolve(submitResponse())
    await flushPromises()
  })

  it('locks recovery when the server already has another requestId', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.recoveryBlockedTitle)
    expect(wrapper.text()).toContain(SESSION_COPY.recoveryBlockedHint)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text() === SESSION_COPY.submit && (button.element as HTMLButtonElement).disabled)).toBe(true)
    const firstId = vi.mocked(submitInterviewAnswer).mock.calls[0][1].requestId
    await typeAnswer(wrapper, 'another answer that must not submit')
    const enabledSubmit = wrapper.findAll('button').find((button) => button.text() === SESSION_COPY.submit && !(button.element as HTMLButtonElement).disabled)
    expect(enabledSubmit).toBeUndefined()
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
    expect(firstId).toBeTruthy()
    const stored = JSON.parse(localStorage.getItem('interview.submit.3.21.88')!)
    expect(stored.requestId).toBe(firstId)
    expect(stored.answerContent).toBe(EXACT_ANSWER)
    expect(stored.recoveryBlockedReason).toBe(RecoveryBlockedReason.OTHER_REQUEST_ID)
    expect(wrapper.findAll('button').some((button) => button.text().includes('按原答案重新获取评价'))).toBe(false)
  })

  it('keeps the answered question on 200 and does not render nextQuestion', async () => {
    vi.mocked(submitInterviewAnswer).mockResolvedValue(
      submitResponse({
        nextQuestion: followUpQuestion({ content: '下一题内容禁止泄漏' }),
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('请说明 Spring Bean 的生命周期。')
    expect(wrapper.text()).toContain(EXACT_ANSWER)
    expect(wrapper.text()).toContain('本题反馈')
    expect(wrapper.text()).toContain('70')
    expect(wrapper.text()).toContain('一般')
    expect(wrapper.text()).toContain('做得好的')
    expect(wrapper.text()).toContain('需要补充')
    expect(wrapper.text()).toContain('参考修正')
    expect(wrapper.text()).toContain('继续追问')
    expect(wrapper.text()).not.toContain('下一题内容禁止泄漏')
    expect(fetchCurrentInterview).toHaveBeenCalledTimes(1)
  })

  it('continues FOLLOW_UP with GET current and a fresh draft', async () => {
    vi.mocked(submitInterviewAnswer).mockResolvedValue(submitResponse())
    const { wrapper } = await mountSession()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(
      currentSession({
        currentQuestion: followUpQuestion(),
      }),
    )
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '继续追问')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('初始化回调失败时容器会怎样处理？')
    expect(wrapper.text()).toContain('追问')
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).value).toBe('')
    expect(localStorage.getItem('interview.submit.3.21.88')).toBeNull()
    expect(wrapper.text()).not.toContain(EXACT_ANSWER)
  })

  it('navigates FINISH to the report placeholder without generating a report', async () => {
    vi.mocked(submitInterviewAnswer).mockResolvedValue(
      submitResponse({
        nextAction: DecisionAction.FINISH,
        sessionStatus: InterviewSessionStatus.COMPLETED,
        nextQuestion: null,
        evaluation: evaluation({ evaluationPhase: EvaluationPhase.FINAL, totalScore: 82, level: 'GOOD' as const }),
      }),
    )
    const { wrapper, router } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('完成本场并查看报告')
    await wrapper.findAll('button').find((button) => button.text() === '完成本场并查看报告')!.trigger('click')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-report')
    })
    expect(generateInterviewReport).not.toHaveBeenCalled()
  })

  it('restores the evaluation gate after refresh when current is still the answered question', async () => {
    writeEvaluationGate(21, evaluationGate({ answerContent: EXACT_ANSWER }))
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('本题反馈')
    expect(wrapper.text()).toContain(EXACT_ANSWER)
    expect(wrapper.text()).toContain('继续追问')
  })

  it('ignores a stale gate after current moved forward', async () => {
    writeEvaluationGate(21, evaluationGate({ answerContent: EXACT_ANSWER }))
    vi.mocked(fetchCurrentInterview).mockResolvedValue(
      currentSession({ currentQuestion: followUpQuestion() }),
    )
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.staleEvaluationNotice)
    expect(wrapper.text()).toContain('初始化回调失败时容器会怎样处理？')
    expect(wrapper.text()).not.toContain(EXACT_ANSWER)
    expect(sessionStorage.getItem('interview.evaluationGate.21')).toBeNull()
  })

  it('restores a FINISH gate when current is null', async () => {
    writeEvaluationGate(
      21,
      evaluationGate({
        nextAction: DecisionAction.FINISH,
        nextQuestion: null,
        sessionStatus: InterviewSessionStatus.COMPLETED,
        answerContent: EXACT_ANSWER,
      }),
    )
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('本题反馈')
    expect(wrapper.text()).toContain(EXACT_ANSWER)
    expect(wrapper.text()).toContain('完成本场并查看报告')
  })

  it('restores a dispatched pending lock after refresh', async () => {
    persistSubmitRecord({
      userId: 3,
      sessionId: 21,
      interviewQuestionId: 88,
      requestId: '11111111-2222-4333-8444-555555555555',
      answerContent: EXACT_ANSWER,
      createdAt: '2026-09-21T01:00:00.000Z',
      updatedAt: '2026-09-21T01:01:00.000Z',
      dispatchedAt: '2026-09-21T01:01:00.000Z',
    })
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.pendingTitle)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).value).toBe(EXACT_ANSWER)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    vi.mocked(submitInterviewAnswer).mockResolvedValue(submitResponse())
    await wrapper.findAll('button').find((button) => button.text().includes('按原答案重新获取评价'))!.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).toHaveBeenCalledWith(21, {
      interviewQuestionId: 88,
      answerContent: EXACT_ANSWER,
      requestId: '11111111-2222-4333-8444-555555555555',
    })
  })

  it('never applies a Q1 record onto Q2', async () => {
    persistSubmitRecord({
      userId: 3,
      sessionId: 21,
      interviewQuestionId: 88,
      requestId: '11111111-2222-4333-8444-555555555555',
      answerContent: EXACT_ANSWER,
      createdAt: '2026-09-21T01:00:00.000Z',
      updatedAt: '2026-09-21T01:00:00.000Z',
    })
    vi.mocked(fetchCurrentInterview).mockResolvedValue(
      currentSession({ currentQuestion: followUpQuestion() }),
    )
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).value).toBe('')
    expect(wrapper.text()).not.toContain(EXACT_ANSWER)
  })

  it('drops a corrupt gate and uses server current', async () => {
    sessionStorage.setItem('interview.evaluationGate.21', '{bad')
    sessionStorage.setItem('interview.evaluationGate.latestSessionId', '21')
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('请说明 Spring Bean 的生命周期。')
    expect(wrapper.text()).not.toContain('本题反馈')
    expect(sessionStorage.getItem('interview.evaluationGate.21')).toBeNull()
  })

  it('shows a one-shot resume notice from Create and not from Dashboard continue', async () => {
    armInterviewResumeNotice(3)
    const first = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(first.wrapper.text()).toContain(SESSION_COPY.resumeNotice)
    first.wrapper.unmount()
    const second = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(second.wrapper.text()).not.toContain(SESSION_COPY.resumeNotice)
  })

  it('redirects to the dashboard when there is no active session', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    const { router } = await mountApp('/interview', (auth) => authenticate(auth))
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('dashboard')
    })
  })

  it('restores a same-user FINISH gate when current is null', async () => {
    writeEvaluationGate(
      21,
      evaluationGate({
        userId: 3,
        nextAction: DecisionAction.FINISH,
        nextQuestion: null,
        sessionStatus: InterviewSessionStatus.COMPLETED,
        answerContent: EXACT_ANSWER,
      }),
    )
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain('本题反馈')
    expect(wrapper.text()).toContain(EXACT_ANSWER)
    expect(wrapper.text()).toContain('完成本场并查看报告')
  })

  it('does not restore another user FINISH gate when current is null', async () => {
    writeEvaluationGate(
      21,
      evaluationGate({
        userId: 3,
        nextAction: DecisionAction.FINISH,
        nextQuestion: null,
        sessionStatus: InterviewSessionStatus.COMPLETED,
        answerContent: EXACT_ANSWER,
      }),
    )
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    const { wrapper, router } = await mountApp('/interview', (auth) =>
      authenticate(auth, UserRole.USER, {
        userId: 11,
        account: 'bob',
        username: '鲍勃',
        email: 'bob@example.com',
      }),
    )
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('dashboard')
    })
    expect(wrapper.text()).not.toContain('请说明 Spring Bean 的生命周期。')
    expect(wrapper.text()).not.toContain(EXACT_ANSWER)
    expect(wrapper.text()).not.toContain('本题反馈')
    expect(wrapper.text()).not.toContain('完成本场并查看报告')
  })

  it('keeps RECOVERY_BLOCKED after checking current for the same question', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    const requestId = vi.mocked(submitInterviewAnswer).mock.calls[0][1].requestId
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    await wrapper.findAll('button').find((button) => button.text() === SESSION_COPY.recoveryBlockedRefresh)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.recoveryBlockedTitle)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text().includes('按原答案重新获取评价'))).toBe(false)
    expect(JSON.parse(localStorage.getItem('interview.submit.3.21.88')!).requestId).toBe(requestId)
    expect(submitInterviewAnswer).toHaveBeenCalledTimes(1)
  })

  it('restores RECOVERY_BLOCKED after a full reload of the same question', async () => {
    persistSubmitRecord({
      userId: 3,
      sessionId: 21,
      interviewQuestionId: 88,
      requestId: '11111111-2222-4333-8444-555555555555',
      answerContent: EXACT_ANSWER,
      createdAt: '2026-09-21T01:00:00.000Z',
      updatedAt: '2026-09-21T01:02:00.000Z',
      dispatchedAt: '2026-09-21T01:01:00.000Z',
      recoveryBlockedReason: RecoveryBlockedReason.OTHER_REQUEST_ID,
      recoveryBlockedAt: '2026-09-21T01:02:00.000Z',
    })
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.recoveryBlockedTitle)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).value).toBe(EXACT_ANSWER)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text().includes('按原答案重新获取评价'))).toBe(false)
    expect(JSON.parse(localStorage.getItem('interview.submit.3.21.88')!).requestId).toBe(
      '11111111-2222-4333-8444-555555555555',
    )
  })

  it('does not copy a blocked Q1 answer onto Q2 when current moves', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(
      currentSession({ currentQuestion: followUpQuestion() }),
    )
    await wrapper.findAll('button').find((button) => button.text() === SESSION_COPY.recoveryBlockedRefresh)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('初始化回调失败时容器会怎样处理？')
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).value).toBe('')
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(false)
    expect(wrapper.text()).not.toContain(EXACT_ANSWER)
    expect(wrapper.text()).not.toContain(SESSION_COPY.recoveryBlockedTitle)
    expect(JSON.parse(localStorage.getItem('interview.submit.3.21.88')!).recoveryBlockedReason).toBe(
      RecoveryBlockedReason.OTHER_REQUEST_ID,
    )
  })

  it('durably blocks a different-content conflict on the same current question', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.DIFFERENT_CONTENT,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.conflictLocked)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text().includes('按原答案重新获取评价'))).toBe(false)
    expect(JSON.parse(localStorage.getItem('interview.submit.3.21.88')!).recoveryBlockedReason).toBe(
      RecoveryBlockedReason.DIFFERENT_CONTENT,
    )
  })

  it('durably blocks a requestId-other-question conflict on the same current question', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.REQUEST_ID_OTHER_QUESTION,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.conflictLocked)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(JSON.parse(localStorage.getItem('interview.submit.3.21.88')!).recoveryBlockedReason).toBe(
      RecoveryBlockedReason.REQUEST_ID_OTHER_QUESTION,
    )
  })

  it('stays RECOVERY_BLOCKED in memory when the blocked marker cannot be persisted', async () => {
    let submitWrites = 0
    const originalSetItem = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key, value) {
      if (String(key).startsWith('interview.submit')) {
        submitWrites += 1
        if (submitWrites > 2) {
          throw new Error('quota')
        }
      }
      return originalSetItem.call(this, key, value)
    })
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(SESSION_COPY.recoveryBlockedTitle)
    expect((wrapper.get('#interview-answer').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect(wrapper.text()).not.toContain(SESSION_COPY.pendingTitle)
    expect(wrapper.findAll('button').some((button) => button.text() === SESSION_COPY.submit && !(button.element as HTMLButtonElement).disabled)).toBe(false)
  })

  it('refuses retry while check current is in flight and refuses check while retrying', async () => {
    persistSubmitRecord({
      userId: 3,
      sessionId: 21,
      interviewQuestionId: 88,
      requestId: '11111111-2222-4333-8444-555555555555',
      answerContent: EXACT_ANSWER,
      createdAt: '2026-09-21T01:00:00.000Z',
      updatedAt: '2026-09-21T01:01:00.000Z',
      dispatchedAt: '2026-09-21T01:01:00.000Z',
    })
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    const pendingCheck = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pendingCheck.promise)
    const retry = wrapper.findAll('button').find((button) => button.text().includes('按原答案重新获取评价'))!
    const check = wrapper.findAll('button').find((button) => button.text().includes('检查当前面试状态'))!
    await check.trigger('click')
    await flushPromises()
    expect((retry.element as HTMLButtonElement).disabled).toBe(true)
    await retry.trigger('click')
    await flushPromises()
    expect(submitInterviewAnswer).not.toHaveBeenCalled()
    pendingCheck.resolve(currentSession())
    await flushPromises()

    const pendingRetry = deferred<ReturnType<typeof submitResponse>>()
    vi.mocked(submitInterviewAnswer).mockReturnValue(pendingRetry.promise)
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    await retry.trigger('click')
    await flushPromises()
    const checkCallsBefore = vi.mocked(fetchCurrentInterview).mock.calls.length
    await check.trigger('click')
    await flushPromises()
    expect(vi.mocked(fetchCurrentInterview).mock.calls.length).toBe(checkCallsBefore)
    pendingRetry.resolve(submitResponse())
    await flushPromises()
  })

  it('does not stamp a Q1 evaluation onto Q2 after current advanced during Q1 retry', async () => {
    persistSubmitRecord({
      userId: 3,
      sessionId: 21,
      interviewQuestionId: 88,
      requestId: '11111111-2222-4333-8444-555555555555',
      answerContent: EXACT_ANSWER,
      createdAt: '2026-09-21T01:00:00.000Z',
      updatedAt: '2026-09-21T01:01:00.000Z',
      dispatchedAt: '2026-09-21T01:01:00.000Z',
    })
    const q2 = currentSession({ currentQuestion: followUpQuestion() })
    const pendingRetry = deferred<ReturnType<typeof submitResponse>>()
    vi.mocked(submitInterviewAnswer).mockReturnValue(pendingRetry.promise)
    const { wrapper } = await mountApp('/interview', (auth) => authenticate(auth))
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('按原答案重新获取评价'))!.trigger('click')
    await flushPromises()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(q2)
    useCurrentInterviewStore().adoptCreatedSession(q2)
    pendingRetry.resolve(submitResponse())
    await flushPromises()
    expect(wrapper.text()).toContain('初始化回调失败时容器会怎样处理？')
    expect(wrapper.text()).not.toContain('本题反馈')
    expect(wrapper.text()).not.toContain('做得好的')
    expect(useCurrentInterviewStore().session?.currentQuestion?.interviewQuestionId).toBe(89)
  })

  it('does not apply user A evaluation onto user B question or A gate storage', async () => {
    const pendingPost = deferred<ReturnType<typeof submitResponse>>()
    vi.mocked(submitInterviewAnswer).mockReturnValue(pendingPost.promise)
    const { wrapper, router, auth } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    const bSession = currentSession({
      sessionId: 77,
      currentQuestion: followUpQuestion({ interviewQuestionId: 909, content: 'B 的当前题' }),
    })
    vi.mocked(fetchCurrentInterview).mockResolvedValue(bSession)
    await router.push('/profile')
    await flushPromises()
    pendingPost.resolve(submitResponse())
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('profile')
    expect(wrapper.text()).not.toContain('本题反馈')
    expect(wrapper.text()).not.toContain('B 的当前题')
    const aGate = readEvaluationGate(21, 3)
    expect(aGate?.answeredQuestion.interviewQuestionId).toBe(88)
    expect(aGate?.answeredQuestion.content).toBe('请说明 Spring Bean 的生命周期。')
    expect(aGate?.answeredQuestion.content).not.toContain('B 的当前题')
    expect(readEvaluationGate(21, 11)).toBeNull()
    expect(readEvaluationGate(77, 11)).toBeNull()
  })

  it('keeps the evaluation-gate answeredQuestion id equal to the SubmitRecord interviewQuestionId', async () => {
    vi.mocked(submitInterviewAnswer).mockResolvedValue(submitResponse())
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    const stored = JSON.parse(localStorage.getItem('interview.submit.3.21.88')!) as { interviewQuestionId: number }
    const gate = readEvaluationGate(21, 3)
    expect(stored.interviewQuestionId).toBe(88)
    expect(gate?.answeredQuestion.interviewQuestionId).toBe(stored.interviewQuestionId)
  })

  it('ignores a stale bootstrap null after logout and does not hijack user B', async () => {
    const pendingCurrent = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pendingCurrent.promise)
    const { router, auth } = await mountApp('/interview', (store) => authenticate(store))
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    await router.push('/profile')
    await flushPromises()
    pendingCurrent.resolve(null)
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('profile')
  })

  it('does not issue a secondary dashboard redirect when /current 401 clears identity', async () => {
    const pendingCurrent = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pendingCurrent.promise)
    const { router, auth } = await mountApp('/interview', (store) => authenticate(store))
    auth.logout()
    pendingCurrent.reject(
      new ApiError({
        httpStatus: 401,
        code: 401,
        message: '未认证或访问令牌无效',
        kind: ErrorKind.UNAUTHENTICATED,
      }),
    )
    await flushPromises()
    expect(router.currentRoute.value.name).not.toBe('dashboard')
  })

  it('does not erase a newer adopted session when an older bootstrap refresh returns stale null', async () => {
    const pendingCurrent = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pendingCurrent.promise)
    const { wrapper, router } = await mountApp('/interview', (store) => authenticate(store))
    const newer = currentSession({
      sessionId: 55,
      currentQuestion: followUpQuestion({ content: '新的权威当前题' }),
    })
    useCurrentInterviewStore().adoptCreatedSession(newer)
    pendingCurrent.resolve(null)
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('interview-session')
    expect(wrapper.text()).toContain('新的权威当前题')
    expect(wrapper.text()).not.toContain('本题反馈')
  })

  it('does not navigate after the session view unmounts during bootstrap', async () => {
    const pendingCurrent = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pendingCurrent.promise)
    const { wrapper, router } = await mountApp('/interview', (store) => authenticate(store))
    const replaceSpy = vi.spyOn(router, 'replace')
    const pushSpy = vi.spyOn(router, 'push')
    wrapper.unmount()
    pendingCurrent.resolve(null)
    await flushPromises()
    expect(replaceSpy).not.toHaveBeenCalled()
    expect(pushSpy).not.toHaveBeenCalled()
  })

  it('uses warning status semantics for EVALUATION_PENDING', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValueOnce(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.EVALUATING,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    const pendingNotice = wrapper.findAll('.status-notice').find((node) => node.text().includes(SESSION_COPY.evaluatingHint))
    expect(pendingNotice?.classes()).toContain('is-warning')
    expect(pendingNotice?.attributes('role')).toBe('status')
  })

  it('uses danger alert semantics for RECOVERY_BLOCKED', async () => {
    vi.mocked(submitInterviewAnswer).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID,
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountSession()
    await typeAnswer(wrapper, EXACT_ANSWER)
    await submitButton(wrapper)!.trigger('click')
    await flushPromises()
    const blockedNotice = wrapper
      .findAll('.status-notice')
      .find((node) => node.text().includes(SESSION_COPY.recoveryBlockedTitle))
    expect(blockedNotice?.classes()).toContain('is-danger')
    expect(blockedNotice?.attributes('role')).toBe('alert')
  })
})
