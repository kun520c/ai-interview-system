import { describe, expect, it } from 'vitest'

import {
  validateAccount,
  validateConfirmPassword,
  validateCurrentPasswordForChange,
  validateEmail,
  validatePassword,
  validateUsername,
} from '@/features/auth/validation/authFormRules'

describe('auth form rules', () => {
  it('enforces frozen account, username, password and email limits', () => {
    expect(validateAccount('')).toBe('请输入账号')
    expect(validateAccount('a'.repeat(51))).toContain('50')
    expect(validateAccount('alice')).toBeNull()
    expect(validateUsername('')).toBe('请输入用户名')
    expect(validateUsername('n'.repeat(51))).toContain('50')
    expect(validatePassword('short')).toContain('8到72')
    expect(validatePassword('password1')).toBeNull()
    expect(validateEmail('')).toBe('请输入邮箱')
    expect(validateEmail('not-an-email')).toBe('请输入有效邮箱')
    expect(validateEmail('alice@example.com')).toBeNull()
    expect(validateConfirmPassword('password1', 'password2')).toBe('两次输入的密码不一致')
    expect(validateConfirmPassword('password1', 'password1')).toBeNull()
  })

  it('allows a non-blank current password shorter than 8 characters', () => {
    expect(validateCurrentPasswordForChange('')).toBe('请输入当前密码')
    expect(validateCurrentPasswordForChange('a')).toBeNull()
    expect(validateCurrentPasswordForChange('short')).toBeNull()
    expect(validateCurrentPasswordForChange('x'.repeat(72))).toBeNull()
    expect(validateCurrentPasswordForChange('x'.repeat(73))).toContain('72')
    expect(validatePassword('short')).not.toBeNull()
  })
})
