import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { persistSubmitRecord, readSubmitRecord } from '@/features/interview/storage/submitRecordStorage'
import { useCurrentInterviewStore } from '@/features/interview/stores/currentInterview'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { AUTH_STORAGE_KEY, writeAuthCredential } from '@/features/auth/storage/authStorage'
import { IdentityStatus } from '@/types/auth'
import { InterviewSessionStatus, QuestionDifficulty, UserRole } from '@/types/enums'
import { authenticate, makeUser, mountApp } from './helpers/mountApp'
import { emptyHistoryPage } from './helpers/interviewFixtures'

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

const submitParts = { userId: 3, sessionId: 21, interviewQuestionId: 88 }

describe('ProductShell', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentUser).mockReset()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
  })

  it('does not show admin tools for a resolved USER', async () => {
    const { wrapper } = await mountApp('/', (auth) => {
      authenticate(auth, UserRole.USER)
    })
    expect(wrapper.text()).toContain('工作台')
    expect(wrapper.text()).toContain('开始面试')
    expect(wrapper.text()).toContain('历史')
    expect(wrapper.text()).toContain('账号')
    expect(wrapper.text()).toContain('爱丽丝')
    expect(wrapper.text()).not.toContain('题库')
    expect(wrapper.text()).not.toContain('知识文档')
    expect(wrapper.text()).not.toContain('管理')
  })

  it('shows admin tools only after identity is resolved ADMIN', async () => {
    const { wrapper } = await mountApp('/', (auth) => {
      authenticate(auth, UserRole.ADMIN)
    })
    expect(wrapper.text()).toContain('题库')
    expect(wrapper.text()).toContain('知识文档')
    expect(wrapper.text()).toContain('管理')
    expect(wrapper.text()).toContain('管理员甲')
  })

  it('does not pretend USER or ADMIN when identity is unavailable', async () => {
    writeAuthCredential({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresAt: Date.now() + 3_600_000,
      userId: 9,
    })
    const { wrapper, auth } = await mountApp('/', (store) => {
      store.accessToken = 'token'
      store.userId = 9
      store.me = null
      store.identityStatus = IdentityStatus.UNAVAILABLE
      store.seedRole = UserRole.ADMIN
    })
    expect(auth.isAdmin).toBe(false)
    expect(wrapper.text()).toContain('用户信息暂时无法加载')
    expect(wrapper.text()).toContain('重试')
    expect(wrapper.text()).not.toContain('题库')
    expect(wrapper.text()).not.toContain('知识文档')
    expect(wrapper.text()).not.toContain('林可')
    expect(wrapper.text()).not.toContain('demo')
    expect(wrapper.text()).not.toContain('管理员甲')
  })

  it('resolves identity after retry', async () => {
    const admin = makeUser(UserRole.ADMIN)
    vi.mocked(fetchCurrentUser).mockResolvedValue(admin)
    const { wrapper, auth } = await mountApp('/', (store) => {
      store.accessToken = 'token'
      store.userId = 9
      store.me = null
      store.identityStatus = IdentityStatus.UNAVAILABLE
    })
    expect(wrapper.text()).not.toContain('题库')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()
    expect(auth.identityStatus).toBe(IdentityStatus.RESOLVED)
    expect(auth.isAdmin).toBe(true)
    expect(wrapper.text()).toContain('题库')
    expect(wrapper.text()).toContain('管理员甲')
    expect(wrapper.text()).not.toContain('用户信息暂时无法加载')
  })

  it('clears the credential on logout, invalidates current interview, and keeps submit records', async () => {
    persistSubmitRecord({
      userId: submitParts.userId,
      sessionId: submitParts.sessionId,
      interviewQuestionId: submitParts.interviewQuestionId,
      requestId: '11111111-2222-4333-8444-555555555555',
      answerContent: 'ConcurrentHashMap',
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    })
    const { wrapper, router, auth } = await mountApp('/', (store) => {
      authenticate(store, UserRole.USER)
    })
    const interview = useCurrentInterviewStore()
    interview.session = {
      sessionId: 21,
      difficulty: QuestionDifficulty.EASY,
      status: InterviewSessionStatus.IN_PROGRESS,
      plannedQuestionCount: 5,
      completedQuestionCount: 0,
      currentQuestion: {
        interviewQuestionId: 88,
        category: 'JAVA_BASIC',
        knowledgePoint: '集合',
        questionType: 'MAIN',
        content: '当前题',
      },
    }
    interview.loaded = true
    const logout = wrapper.findAll('button').find((button) => button.text().includes('退出登录'))
    expect(logout).toBeDefined()
    await logout!.trigger('click')
    await vi.waitFor(() => {
      expect(auth.accessToken).toBeNull()
      expect(router.currentRoute.value.name).toBe('login')
    })
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(interview.session).toBeNull()
    expect(readSubmitRecord(submitParts)).not.toBeNull()
  })

  it('opens and closes the mobile menu, including Escape and route selection', async () => {
    const { wrapper, router } = await mountApp('/', (auth) => {
      authenticate(auth, UserRole.USER)
    })
    expect(wrapper.find('#product-mobile-menu').exists()).toBe(false)
    await wrapper.get('[aria-label="打开菜单"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('#product-mobile-menu').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(wrapper.find('#product-mobile-menu').exists()).toBe(false)

    await wrapper.get('[aria-label="打开菜单"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('#product-mobile-menu').exists()).toBe(true)
    await router.push('/profile')
    await flushPromises()
    expect(wrapper.find('#product-mobile-menu').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('profile')
  })

  it('closes the mobile menu when a route link is chosen', async () => {
    const { wrapper, router } = await mountApp('/', (auth) => {
      authenticate(auth, UserRole.USER)
    })
    await wrapper.get('[aria-label="打开菜单"]').trigger('click')
    await flushPromises()
    const historyLink = wrapper
      .get('#product-mobile-menu')
      .findAll('a')
      .find((link) => link.text().includes('历史'))
    expect(historyLink).toBeDefined()
    await historyLink!.trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('interview-history')
    })
    expect(wrapper.find('#product-mobile-menu').exists()).toBe(false)
  })

  it('skips to the product workspace rather than the sidebar wrapper', async () => {
    const { wrapper } = await mountApp('/', (auth) => authenticate(auth, UserRole.USER))
    const skip = wrapper.find('a.skip-link')
    expect(skip.attributes('href')).toBe('#product-main')
    expect(wrapper.find('#product-main').exists()).toBe(true)
    expect(wrapper.find('#app-main').exists()).toBe(false)
    expect(wrapper.find('#product-main').classes()).toContain('product-workspace')
  })
})
