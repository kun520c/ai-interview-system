import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { resetPasswordChangedNoticeForTests } from '@/features/auth/navigation/passwordChangedNotice'
import { changePassword, fetchCurrentUser } from '@/features/auth/api/userApi'
import { persistSubmitRecord, readSubmitRecord } from '@/features/interview/storage/submitRecordStorage'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { IdentityStatus } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import { emptyHistoryPage } from './helpers/interviewFixtures'
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

const submitParts = { userId: 3, sessionId: 21, interviewQuestionId: 88 }

describe('ProfileView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    resetPasswordChangedNoticeForTests()
    localStorage.clear()
    vi.mocked(changePassword).mockReset()
    vi.mocked(fetchCurrentUser).mockReset()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
  })

  it('renders identity fields from auth.me and has no editable username or email', async () => {
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('爱丽丝')
    expect(wrapper.text()).toContain('alice@example.com')
    expect(wrapper.text()).toContain('用户')
    expect(wrapper.text()).toContain('启用')
    expect(wrapper.find('#profile-username').exists()).toBe(false)
    expect(wrapper.find('#profile-email').exists()).toBe(false)
    expect(wrapper.find('input[name="username"]').exists()).toBe(false)
    expect(wrapper.find('input[name="email"]').exists()).toBe(false)
  })

  it('shows unavailable identity and retries GET /users/me', async () => {
    const { wrapper, auth } = await mountApp('/profile', (store) => {
      authenticate(store)
      store.me = null
      store.identityStatus = IdentityStatus.UNAVAILABLE
    })
    expect(wrapper.text()).toContain('账号信息暂时无法确认')
    expect(wrapper.text()).not.toContain('爱丽丝')
    vi.mocked(fetchCurrentUser).mockResolvedValue({
      userId: 3,
      account: 'alice',
      username: '爱丽丝',
      email: 'alice@example.com',
      role: UserRole.USER,
      status: UserStatus.ENABLED,
      createdAt: '2026-01-01T00:00:00',
    })
    await wrapper.findAll('button').find((button) => button.text().includes('重新加载账号信息'))?.trigger('click')
    await flushPromises()
    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)
    expect(auth.identityStatus).toBe(IdentityStatus.RESOLVED)
    expect(wrapper.text()).toContain('爱丽丝')
  })

  it('validates the password form locally before PUT', async () => {
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入当前密码')
    expect(wrapper.text()).toContain('请输入新密码')
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass1')
    await wrapper.get('#profile-confirm-password').setValue('mismatch')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的密码不一致')
  })

  it('shows the backend current-password error inline and keeps the inputs', async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '请输入正确的现存密码',
        kind: ErrorKind.VALIDATION,
      }),
    )
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('请输入正确的现存密码')
    expect((wrapper.get('#profile-current-password').element as HTMLInputElement).value).toBe('oldpass1')
    expect(changePassword).toHaveBeenCalledTimes(1)
  })

  it('shows the same-password backend error', async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '新密码不能与原密码相同',
        kind: ErrorKind.VALIDATION,
      }),
    )
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    await wrapper.get('#profile-current-password').setValue('samepass')
    await wrapper.get('#profile-new-password').setValue('samepass')
    await wrapper.get('#profile-confirm-password').setValue('samepass')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('新密码不能与原密码相同')
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'samepass',
      newPassword: 'samepass',
    })
  })

  it('clears auth, keeps submit records, and goes to login after success', async () => {
    persistSubmitRecord({
      ...submitParts,
      requestId: 'a'.repeat(36),
      answerContent: 'answer',
      createdAt: '2026-09-21T00:00:00.000Z',
      updatedAt: '2026-09-21T00:00:00.000Z',
    })
    vi.mocked(changePassword).mockResolvedValue(null)
    const { wrapper, router, auth } = await mountApp('/profile', (store) => authenticate(store))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(auth.accessToken).toBeNull()
    expect(auth.me).toBeNull()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('login')
    })
    expect(wrapper.text()).toContain('密码已修改，请使用新密码重新登录')
    expect(readSubmitRecord(submitParts)?.answerContent).toBe('answer')
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'oldpass1',
      newPassword: 'newpass12',
    })
  })

  it('does not automatically PUT again after a network or 500 failure', async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('密码修改结果暂时无法确认')
    expect(changePassword).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('返回登录')
  })

  it('sends only one PUT when the submit control is activated twice', async () => {
    let resolvePassword!: (value: null) => void
    vi.mocked(changePassword).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePassword = resolve
        }),
    )
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    const form = wrapper.get('form')
    await form.trigger('submit')
    await form.trigger('submit')
    resolvePassword(null)
    await flushPromises()
    expect(changePassword).toHaveBeenCalledTimes(1)
  })

  it('allows a current password shorter than 8 characters', async () => {
    vi.mocked(changePassword).mockResolvedValue(null)
    const { wrapper } = await mountApp('/profile', (auth) => authenticate(auth))
    await wrapper.get('#profile-current-password').setValue('short')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'short',
      newPassword: 'newpass12',
    })
  })

  it('ignores a stale password success after another user logs in', async () => {
    const pending = deferred<null>()
    vi.mocked(changePassword).mockReturnValue(pending.promise)
    const { wrapper, router, auth } = await mountApp('/profile', (store) => authenticate(store))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    await router.push('/profile')
    await flushPromises()
    pending.resolve(null)
    await flushPromises()
    expect(auth.accessToken).toBe('token')
    expect(auth.userId).toBe(11)
    expect(router.currentRoute.value.name).toBe('profile')
    expect(wrapper.text()).not.toContain('密码已修改，请使用新密码重新登录')
  })

  it('does not show a stale password error on the next user', async () => {
    const pending = deferred<null>()
    vi.mocked(changePassword).mockReturnValue(pending.promise)
    const { wrapper, router, auth } = await mountApp('/profile', (store) => authenticate(store))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    await router.push('/profile')
    await flushPromises()
    pending.reject(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '请输入正确的现存密码',
        kind: ErrorKind.VALIDATION,
      }),
    )
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('profile')
    expect(auth.userId).toBe(11)
    expect(wrapper.text()).not.toContain('请输入正确的现存密码')
    expect(wrapper.text()).toContain('鲍勃')
  })

  it('does not redirect the next user after a stale password network failure', async () => {
    const pending = deferred<null>()
    vi.mocked(changePassword).mockReturnValue(pending.promise)
    const { wrapper, router, auth } = await mountApp('/profile', (store) => authenticate(store))
    await wrapper.get('#profile-current-password').setValue('oldpass1')
    await wrapper.get('#profile-new-password').setValue('newpass12')
    await wrapper.get('#profile-confirm-password').setValue('newpass12')
    await wrapper.get('form').trigger('submit')
    auth.logout()
    authenticate(auth, UserRole.USER, {
      userId: 11,
      account: 'bob',
      username: '鲍勃',
      email: 'bob@example.com',
    })
    await router.push('/profile')
    await flushPromises()
    pending.reject(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('profile')
    expect(auth.accessToken).toBe('token')
    expect(wrapper.text()).not.toContain('密码修改结果暂时无法确认')
  })
})
