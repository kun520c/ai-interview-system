import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { useCurrentInterviewStore } from '@/features/interview/stores/currentInterview'
import { createInterview, fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { resetInterviewResumeNoticeForTests } from '@/features/interview/navigation/sessionNavigationContext'
import { QuestionDifficulty, UserRole } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import { currentSession, emptyHistoryPage } from './helpers/interviewFixtures'
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

describe('CreateInterviewView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    resetInterviewResumeNoticeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(createInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
  })

  it('does not flash difficulty cards while current is loading', async () => {
    const pending = deferred<null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pending.promise)
    const { wrapper } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    expect(wrapper.text()).toContain('正在确认当前面试状态')
    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('EASY')
    pending.resolve(null)
    await flushPromises()
    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(true)
  })

  it('shows three unselected peer options and a disabled start button', async () => {
    const { wrapper } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios).toHaveLength(3)
    expect(radios.map((radio) => (radio.element as HTMLInputElement).value)).toEqual([
      'EASY',
      'MEDIUM',
      'HARD',
    ])
    expect(radios.every((radio) => !(radio.element as HTMLInputElement).checked)).toBe(true)
    expect(wrapper.text()).toContain('基础覆盖')
    expect(wrapper.text()).toContain('范围更完整')
    expect(wrapper.text()).toContain('覆盖全部核心领域与项目场景')
    expect(wrapper.text()).not.toContain('推荐')
    expect(wrapper.text()).not.toContain('最适合校招')
    expect(wrapper.text()).not.toContain('默认')
    const start = wrapper.findAll('button').find((button) => button.text().includes('开始面试'))
    expect(start).toBeDefined()
    expect((start!.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('posts only the selected difficulty and adopts the session', async () => {
    const created = currentSession({ difficulty: QuestionDifficulty.MEDIUM, completedQuestionCount: 0 })
    vi.mocked(createInterview).mockImplementation(async () => {
      vi.mocked(fetchCurrentInterview).mockResolvedValue(created)
      return created
    })
    const { wrapper, router } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const medium = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'MEDIUM')
    await medium!.setValue(true)
    await medium!.trigger('change')
    const start = wrapper.findAll('button').find((button) => button.text().includes('开始面试'))
    expect((start!.element as HTMLButtonElement).disabled).toBe(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createInterview).toHaveBeenCalledTimes(1)
    expect(createInterview).toHaveBeenCalledWith({ difficulty: 'MEDIUM' })
    expect(useCurrentInterviewStore().session?.sessionId).toBe(created.sessionId)
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
  })

  it('shows resume instead of difficulty cards when current is active', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(currentSession())
    const { wrapper, router } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    expect(wrapper.find('[role="radiogroup"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('你有一场未完成的面试')
    expect(wrapper.text()).toContain('为什么不能开始第二场？')
    expect(wrapper.text()).not.toContain('取消')
    expect(wrapper.text()).not.toContain('放弃')
    expect(wrapper.text()).not.toContain('Cancel')
    const resume = wrapper.get('.resume-cta')
    expect(resume.text()).toBe('继续当前面试')
    expect(resume.attributes('href')).toBe('/interview')
    await resume.trigger('click')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
  })

  it('treats a POST that returns a different difficulty as resume, not an error', async () => {
    const resumed = currentSession({ difficulty: QuestionDifficulty.HARD, completedQuestionCount: 1 })
    vi.mocked(createInterview).mockImplementation(async () => {
      vi.mocked(fetchCurrentInterview).mockResolvedValue(resumed)
      return resumed
    })
    const { wrapper, router } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const easy = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'EASY')
    await easy!.setValue(true)
    await easy!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).not.toContain('创建失败')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
    expect(useCurrentInterviewStore().session?.difficulty).toBe(QuestionDifficulty.HARD)
    expect(wrapper.text()).toContain('已为你继续未完成的面试')
  })

  it('verifies GET current after an uncertain POST and resumes if active', async () => {
    vi.mocked(createInterview).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchCurrentInterview)
      .mockResolvedValueOnce(null)
      .mockResolvedValue(currentSession({ difficulty: QuestionDifficulty.EASY }))
    const { wrapper, router } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const easy = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'EASY')
    await easy!.setValue(true)
    await easy!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createInterview).toHaveBeenCalledTimes(1)
    expect(fetchCurrentInterview).toHaveBeenCalledTimes(3)
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-session')
    })
  })

  it('allows an explicit retry only after verification returns null', async () => {
    vi.mocked(createInterview).mockRejectedValue(
      new ApiError({ httpStatus: 500, code: 500, message: '系统异常，请稍后重试', kind: ErrorKind.UNEXPECTED }),
    )
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    const { wrapper } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const medium = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'MEDIUM')
    await medium!.setValue(true)
    await medium!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('未检测到进行中的面试，可以重新尝试开始。')
    expect(createInterview).toHaveBeenCalledTimes(1)
    const retryStart = wrapper.findAll('button').find((button) => button.text().includes('开始面试'))
    expect(retryStart).toBeDefined()
    expect((retryStart!.element as HTMLButtonElement).disabled).toBe(false)
  })

  it('offers Retry Check only when verification GET fails, without a second POST', async () => {
    vi.mocked(createInterview).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    vi.mocked(fetchCurrentInterview)
      .mockResolvedValueOnce(null)
      .mockRejectedValueOnce(
        new ApiError({ httpStatus: 500, code: 500, message: '系统异常，请稍后重试', kind: ErrorKind.UNEXPECTED }),
      )
    const { wrapper } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const hard = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'HARD')
    await hard!.setValue(true)
    await hard!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('无法确认面试是否已创建')
    expect(wrapper.text()).toContain('重新检查')
    expect(wrapper.text()).toContain('返回工作台')
    expect(wrapper.findAll('button').some((button) => button.text().includes('开始面试'))).toBe(false)
    expect(createInterview).toHaveBeenCalledTimes(1)

    vi.mocked(fetchCurrentInterview).mockRejectedValueOnce(
      new ApiError({ httpStatus: 500, code: 500, message: '系统异常，请稍后重试', kind: ErrorKind.UNEXPECTED }),
    )
    const retryCheck = wrapper.findAll('button').find((button) => button.text() === '重新检查')
    await retryCheck!.trigger('click')
    await flushPromises()
    expect(createInterview).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('无法确认面试是否已创建')
  })

  it('shows preparing copy and disables cards while the POST is in flight', async () => {
    const pending = deferred<ReturnType<typeof currentSession>>()
    vi.mocked(createInterview).mockReturnValue(pending.promise)
    const { wrapper } = await mountApp('/interviews/new', (auth) => authenticate(auth))
    await flushPromises()
    const medium = wrapper
      .findAll('input[type="radio"]')
      .find((radio) => (radio.element as HTMLInputElement).value === 'MEDIUM')
    await medium!.setValue(true)
    await medium!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('正在准备本场题目')
    expect(
      wrapper.findAll('input[type="radio"]').every((radio) => (radio.element as HTMLInputElement).disabled),
    ).toBe(true)
    pending.resolve(currentSession({ difficulty: QuestionDifficulty.MEDIUM, completedQuestionCount: 0 }))
    await flushPromises()
  })

  it('does not adopt user A session into user B store or kill B GET', async () => {
    const pendingCreate = deferred<ReturnType<typeof currentSession>>()
    vi.mocked(createInterview).mockReturnValue(pendingCreate.promise)
    const createdA = currentSession({ sessionId: 21, difficulty: QuestionDifficulty.MEDIUM, completedQuestionCount: 0 })
    const { wrapper, router, auth } = await mountApp('/interviews/new', (store) => authenticate(store))
    await flushPromises()
    const medium = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'MEDIUM')
    await medium!.setValue(true)
    await medium!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    const pendingBCurrent = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pendingBCurrent.promise)
    await router.push('/')
    await flushPromises()
    pendingCreate.resolve(createdA)
    await flushPromises()
    expect(useCurrentInterviewStore().session).toBeNull()
    expect(router.currentRoute.value.name).toBe('dashboard')
    const bSession = currentSession({ sessionId: 77, difficulty: QuestionDifficulty.EASY })
    pendingBCurrent.resolve(bSession)
    await flushPromises()
    expect(useCurrentInterviewStore().session?.sessionId).toBe(77)
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('does not navigate user B after user A uncertain POST verification', async () => {
    vi.mocked(createInterview).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    const pendingVerify = deferred<ReturnType<typeof currentSession> | null>()
    vi.mocked(fetchCurrentInterview)
      .mockResolvedValueOnce(null)
      .mockReturnValue(pendingVerify.promise)
    const { wrapper, router, auth } = await mountApp('/interviews/new', (store) => authenticate(store))
    await flushPromises()
    const easy = wrapper.findAll('input[type="radio"]').find((radio) => (radio.element as HTMLInputElement).value === 'EASY')
    await easy!.setValue(true)
    await easy!.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    await router.push('/profile')
    await flushPromises()
    pendingVerify.resolve(currentSession({ sessionId: 21 }))
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('profile')
    expect(useCurrentInterviewStore().session).toBeNull()
  })
})
