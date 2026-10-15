import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import {
  createAdminQuestion,
  fetchAdminQuestion,
  fetchAdminQuestions,
  updateAdminQuestion,
  updateAdminQuestionStatus,
} from '@/features/admin/questions/api/questionAdminApi'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { QuestionCategory, QuestionDifficulty, QuestionPointType, QuestionStatus, UserRole } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import { emptyHistoryPage } from './helpers/interviewFixtures'
import {
  adminQuestionDetail,
  adminQuestionPage,
  createQuestionResponse,
} from './helpers/adminFixtures'
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

vi.mock('@/features/admin/questions/api/questionAdminApi', () => ({
  fetchAdminQuestions: vi.fn(),
  fetchAdminQuestion: vi.fn(),
  createAdminQuestion: vi.fn(),
  updateAdminQuestion: vi.fn(),
  updateAdminQuestionStatus: vi.fn(),
}))

async function fillRequiredQuestionFields(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper']) {
  await wrapper.get('#question-knowledge-point').setValue('Bean 生命周期')
  await wrapper.get('#question-content').setValue('请说明 Spring Bean 的生命周期。')
  await wrapper.get('#question-reference').setValue('实例化、依赖注入、初始化、销毁。')
}

describe('Admin questions', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchAdminQuestions).mockReset()
    vi.mocked(fetchAdminQuestion).mockReset()
    vi.mocked(createAdminQuestion).mockReset()
    vi.mocked(updateAdminQuestion).mockReset()
    vi.mocked(updateAdminQuestionStatus).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
    vi.mocked(fetchAdminQuestions).mockResolvedValue(adminQuestionPage())
    vi.mocked(fetchAdminQuestion).mockResolvedValue(adminQuestionDetail())
  })

  it('sends a USER to forbidden without logging out', async () => {
    const { wrapper, router, auth } = await mountApp('/admin/questions', (store) => authenticate(store))
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(auth.accessToken).toBe('token')
    expect(fetchAdminQuestions).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('权限不足')
  })

  it('renders the ADMIN question list', async () => {
    const { wrapper } = await mountApp('/admin/questions', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    expect(wrapper.text()).toContain('题库')
    expect(wrapper.text()).toContain('Spring')
    expect(wrapper.text()).toContain('Bean 生命周期')
    expect(wrapper.text()).toContain('请说明 Spring Bean 的生命周期。')
    expect(fetchAdminQuestions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
  })

  it('canonicalizes invalid query values before calling the API', async () => {
    await mountApp(
      '/admin/questions?pageNum=0&pageSize=99&category=NOT_A_CATEGORY&keyword=' + 'k'.repeat(120),
      (store) => authenticate(store, UserRole.ADMIN),
    )
    await flushPromises()
    expect(fetchAdminQuestions).toHaveBeenCalledTimes(1)
    expect(fetchAdminQuestions).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 50,
      keyword: 'k'.repeat(100),
    })
  })

  it('resets pageNum when filters change', async () => {
    vi.mocked(fetchAdminQuestions).mockResolvedValue(
      adminQuestionPage({ pageNum: 2, totalPages: 3, total: 21 }),
    )
    const { wrapper, router } = await mountApp('/admin/questions?pageNum=2&pageSize=10', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await flushPromises()
    await wrapper.get('select').setValue(QuestionCategory.SPRING)
    await flushPromises()
    expect(router.currentRoute.value.query.pageNum).toBe('1')
    expect(router.currentRoute.value.query.category).toBe(QuestionCategory.SPRING)
  })

  it('loads detail using the content field, not questionContent', async () => {
    const { wrapper } = await mountApp('/admin/questions/7', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    expect(fetchAdminQuestion).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('请说明 Spring Bean 的生命周期。')
    expect(wrapper.text()).toContain('实例化、依赖注入、初始化、销毁。')
    expect(wrapper.text()).toContain('覆盖实例化与依赖注入')
    expect(wrapper.html()).not.toContain('questionContent')
  })

  it('does not probe APIs for an invalid question id', async () => {
    const { wrapper } = await mountApp('/admin/questions/abc', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    expect(fetchAdminQuestion).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('题目不存在')
  })

  it('shows a generic missing state for 404', async () => {
    vi.mocked(fetchAdminQuestion).mockRejectedValue(
      new ApiError({ httpStatus: 404, code: 404, message: '题目不存在', kind: ErrorKind.NOT_FOUND }),
    )
    const { wrapper } = await mountApp('/admin/questions/99', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    expect(wrapper.text()).toContain('题目不存在')
  })

  it('requires scoring-point weights to sum to 100 and keeps at least one point', async () => {
    const { wrapper } = await mountApp('/admin/questions/new', (store) => authenticate(store, UserRole.ADMIN))
    await fillRequiredQuestionFields(wrapper)
    expect(wrapper.text()).toContain('当前权重合计：1 / 100')
    const submit = wrapper.findAll('button').find((button) => button.text().includes('创建题目'))
    expect(submit?.attributes('disabled')).toBeDefined()
    await wrapper.get('.weight-input').setValue('100')
    await flushPromises()
    expect(wrapper.text()).toContain('当前权重合计：100 / 100')
    expect(wrapper.findAll('.point-card')).toHaveLength(1)
    await wrapper.findAll('button').find((button) => button.text().includes('删除评分点'))?.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.point-card')).toHaveLength(1)
    await wrapper.findAll('button').find((button) => button.text().includes('添加评分点'))?.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.point-card')).toHaveLength(2)
  })

  it('POSTs the exact create request shape', async () => {
    vi.mocked(createAdminQuestion).mockResolvedValue(createQuestionResponse())
    const { wrapper, router } = await mountApp('/admin/questions/new', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await fillRequiredQuestionFields(wrapper)
    await wrapper.findAll('textarea').at(-1)?.setValue('覆盖实例化与依赖注入')
    await wrapper.get('.weight-input').setValue('100')
    await wrapper.get('form.question-form').trigger('submit')
    await flushPromises()
    expect(createAdminQuestion).toHaveBeenCalledWith({
      category: QuestionCategory.JAVA_BASIC,
      knowledgePoint: 'Bean 生命周期',
      difficulty: QuestionDifficulty.MEDIUM,
      questionContent: '请说明 Spring Bean 的生命周期。',
      referenceAnswer: '实例化、依赖注入、初始化、销毁。',
      scoringPoints: [
        {
          pointType: QuestionPointType.CORE,
          content: '覆盖实例化与依赖注入',
          weight: 100,
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('admin-question-detail')
    expect(router.currentRoute.value.params.questionId).toBe('88')
  })

  it('does not auto-retry an uncertain create POST', async () => {
    vi.mocked(createAdminQuestion).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    const { wrapper } = await mountApp('/admin/questions/new', (store) => authenticate(store, UserRole.ADMIN))
    await fillRequiredQuestionFields(wrapper)
    await wrapper.get('#question-content').setValue('请说明 Spring Bean 的生命周期。内容')
    const textareas = wrapper.findAll('textarea')
    await textareas[1].setValue('实例化、依赖注入、初始化、销毁。')
    await wrapper.findAll('textarea').at(-1)?.setValue('覆盖实例化')
    await wrapper.get('.weight-input').setValue('100')
    await wrapper.get('form.question-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('创建结果无法确认')
    expect(wrapper.text()).toContain('仍然提交')
    expect(createAdminQuestion).toHaveBeenCalledTimes(1)
  })

  it('prefills edit from detail.content and PUTs the exact replace body', async () => {
    vi.mocked(updateAdminQuestion).mockResolvedValue(null)
    const { wrapper } = await mountApp('/admin/questions/7/edit', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await flushPromises()
    expect(fetchAdminQuestion).toHaveBeenCalledWith(7)
    expect((wrapper.get('#question-content').element as HTMLTextAreaElement).value).toBe(
      '请说明 Spring Bean 的生命周期。',
    )
    await wrapper.get('#question-knowledge-point').setValue('Bean 销毁')
    await wrapper.get('form.question-form').trigger('submit')
    await flushPromises()
    expect(updateAdminQuestion).toHaveBeenCalledWith(7, {
      category: QuestionCategory.SPRING,
      knowledgePoint: 'Bean 销毁',
      difficulty: QuestionDifficulty.MEDIUM,
      questionContent: '请说明 Spring Bean 的生命周期。',
      referenceAnswer: '实例化、依赖注入、初始化、销毁。',
      scoringPoints: [
        {
          pointType: QuestionPointType.CORE,
          content: '覆盖实例化与依赖注入',
          weight: 60,
        },
        {
          pointType: QuestionPointType.KEY,
          content: '提到销毁回调',
          weight: 40,
        },
      ],
    })
  })

  it('updates status through PUT /status with the exact body', async () => {
    vi.mocked(updateAdminQuestionStatus).mockResolvedValue(null)
    const { wrapper } = await mountApp('/admin/questions/7', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('停用'))?.trigger('click')
    await flushPromises()
    expect(updateAdminQuestionStatus).toHaveBeenCalledWith(7, { status: QuestionStatus.DISABLED })
    expect(fetchAdminQuestion).toHaveBeenCalledTimes(2)
  })

  it('keeps the session on API 403 and goes to forbidden', async () => {
    vi.mocked(fetchAdminQuestions).mockRejectedValue(
      new ApiError({ httpStatus: 403, code: 403, message: '没有权限', kind: ErrorKind.FORBIDDEN }),
    )
    const { router, auth } = await mountApp('/admin/questions', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(auth.accessToken).toBe('token')
    expect(auth.me?.role).toBe(UserRole.ADMIN)
  })

  it('uses stacked record markup for narrow layouts', async () => {
    const { wrapper } = await mountApp('/admin/questions', (store) => authenticate(store, UserRole.ADMIN))
    await flushPromises()
    expect(wrapper.get('td.content-cell').attributes('data-label')).toBe('题目内容')
  })

  it('does not route back to a stale create success after leaving the form', async () => {
    const pending = deferred<ReturnType<typeof createQuestionResponse>>()
    vi.mocked(createAdminQuestion).mockReturnValue(pending.promise)
    const { wrapper, router } = await mountApp('/admin/questions/new', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await fillRequiredQuestionFields(wrapper)
    await wrapper.findAll('textarea').at(-1)?.setValue('覆盖实例化与依赖注入')
    await wrapper.get('.weight-input').setValue('100')
    await wrapper.get('form.question-form').trigger('submit')
    await router.push('/')
    await flushPromises()
    pending.resolve(createQuestionResponse())
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('dashboard')
    expect(createAdminQuestion).toHaveBeenCalledTimes(1)
  })

  it('does not redirect to question 7 after a stale edit succeeds on question 8', async () => {
    const pending = deferred<null>()
    vi.mocked(updateAdminQuestion).mockReturnValue(pending.promise)
    vi.mocked(fetchAdminQuestion).mockImplementation(async (id) =>
      adminQuestionDetail({
        id,
        knowledgePoint: id === 8 ? 'Question 8 KP' : 'Bean 生命周期',
      }),
    )
    const { wrapper, router } = await mountApp('/admin/questions/7/edit', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await flushPromises()
    await wrapper.get('form.question-form').trigger('submit')
    await router.push('/admin/questions/8')
    await flushPromises()
    pending.resolve(null)
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('admin-question-detail')
    expect(router.currentRoute.value.params.questionId).toBe('8')
    expect(wrapper.text()).toContain('Question 8 KP')
    expect(wrapper.text()).not.toContain('Bean 生命周期')
  })

  it('does not copy a stale question 7 status error onto question 8', async () => {
    const pending = deferred<null>()
    vi.mocked(updateAdminQuestionStatus).mockReturnValue(pending.promise)
    vi.mocked(fetchAdminQuestion).mockImplementation(async (id) =>
      adminQuestionDetail({
        id,
        knowledgePoint: id === 8 ? 'Question 8 KP' : 'Bean 生命周期',
      }),
    )
    const { wrapper, router } = await mountApp('/admin/questions/7', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('停用'))?.trigger('click')
    await router.push('/admin/questions/8')
    await flushPromises()
    pending.reject(
      new ApiError({ httpStatus: 404, code: 404, message: '题目不存在', kind: ErrorKind.NOT_FOUND }),
    )
    await flushPromises()
    expect(router.currentRoute.value.params.questionId).toBe('8')
    expect(wrapper.text()).toContain('Question 8 KP')
    expect(wrapper.findAll('[role="alert"]').some((node) => node.text().includes('题目不存在'))).toBe(false)
  })

  it('does not apply a stale question 7 unknown status error to question 8', async () => {
    const pending = deferred<null>()
    vi.mocked(updateAdminQuestionStatus).mockReturnValue(pending.promise)
    vi.mocked(fetchAdminQuestion).mockImplementation(async (id) =>
      adminQuestionDetail({
        id,
        knowledgePoint: id === 8 ? 'Question 8 KP' : 'Bean 生命周期',
      }),
    )
    const { wrapper, router } = await mountApp('/admin/questions/7', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('停用'))?.trigger('click')
    await router.push('/admin/questions/8')
    await flushPromises()
    pending.reject(
      new ApiError({
        httpStatus: 500,
        code: 500,
        message: '服务器内部错误',
        kind: ErrorKind.UNEXPECTED,
      }),
    )
    await flushPromises()
    expect(wrapper.text()).toContain('Question 8 KP')
    expect(wrapper.text()).not.toContain('服务器内部错误')
  })

  it('does not navigate a later route with a stale list 403', async () => {
    const pending = deferred<ReturnType<typeof adminQuestionPage>>()
    vi.mocked(fetchAdminQuestions).mockReturnValue(pending.promise)
    const { router } = await mountApp('/admin/questions', (store) => authenticate(store, UserRole.ADMIN))
    await router.push('/')
    await flushPromises()
    pending.reject(
      new ApiError({ httpStatus: 403, code: 403, message: '没有权限', kind: ErrorKind.FORBIDDEN }),
    )
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('dashboard')
  })
})
