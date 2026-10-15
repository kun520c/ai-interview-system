import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { registerAccount } from '@/features/auth/api/authApi'
import { AUTH_STORAGE_KEY } from '@/features/auth/storage/authStorage'
import { mountApp } from './helpers/mountApp'

vi.mock('@/features/auth/api/authApi', () => ({
  login: vi.fn(),
  registerAccount: vi.fn(),
}))

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

describe('RegisterView', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(registerAccount).mockReset()
  })

  it('blocks submit when required fields are empty', async () => {
    const { wrapper } = await mountApp('/register')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(registerAccount).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入账号')
    expect(wrapper.text()).toContain('请输入用户名')
    expect(wrapper.text()).toContain('请输入密码')
    expect(wrapper.text()).toContain('请输入邮箱')
  })

  it('keeps confirmPassword as local form state and does not send it', async () => {
    vi.mocked(registerAccount).mockResolvedValue(null)
    const { wrapper, router, auth } = await mountApp('/register')
    await wrapper.get('#register-account').setValue('alice')
    await wrapper.get('#register-username').setValue('爱丽丝')
    await wrapper.get('#register-password').setValue('password1')
    await wrapper.get('#register-confirm-password').setValue('password1')
    await wrapper.get('#register-email').setValue('alice@example.com')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => {
      expect(registerAccount).toHaveBeenCalledTimes(1)
      expect(router.currentRoute.value.name).toBe('login')
    })
    expect(registerAccount).toHaveBeenCalledWith({
      account: 'alice',
      username: '爱丽丝',
      password: 'password1',
      email: 'alice@example.com',
    })
    expect(registerAccount.mock.calls[0]?.[0]).not.toHaveProperty('confirmPassword')
    expect(auth.accessToken).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.account).toBe('alice')
    expect((wrapper.get('#login-account').element as HTMLInputElement).value).toBe('alice')
  })

  it('shows the unified duplicate public error without guessing the field', async () => {
    vi.mocked(registerAccount).mockRejectedValue(
      new ApiError({
        httpStatus: 400,
        code: 400,
        message: '账号或邮箱已存在',
        kind: ErrorKind.VALIDATION,
      }),
    )
    const { wrapper, auth } = await mountApp('/register')
    await wrapper.get('#register-account').setValue('alice')
    await wrapper.get('#register-username').setValue('爱丽丝')
    await wrapper.get('#register-password').setValue('password1')
    await wrapper.get('#register-confirm-password').setValue('password1')
    await wrapper.get('#register-email').setValue('alice@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('账号或邮箱已存在')
    expect(auth.accessToken).toBeNull()
    expect((wrapper.get('#register-account').element as HTMLInputElement).value).toBe('alice')
    expect((wrapper.get('#register-email').element as HTMLInputElement).value).toBe('alice@example.com')
  })

  it('rejects a mismatched confirm password locally', async () => {
    const { wrapper } = await mountApp('/register')
    await wrapper.get('#register-account').setValue('alice')
    await wrapper.get('#register-username').setValue('爱丽丝')
    await wrapper.get('#register-password').setValue('password1')
    await wrapper.get('#register-confirm-password').setValue('password2')
    await wrapper.get('#register-email').setValue('alice@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(registerAccount).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的密码不一致')
  })
})
