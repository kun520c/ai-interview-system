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
    expect(validatePassword('short')).toContain('不能少于8位')
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
  it.each([
    ['ASCII minimum', 'b5Test!9'],
    ['ASCII 72 bytes', 'x'.repeat(72)],
    ['CJK 72 bytes', '界'.repeat(24)],
    ['emoji 72 bytes', '🚀'.repeat(18)],
    ['combining 72 bytes', 'e\u0301'.repeat(24)],
    ['surrounding spaces', '  b5Test!9  '],
    ['non-breaking space', '\u00a0'.repeat(8)],
    ['figure space', '\u2007'.repeat(8)],
    ['narrow non-breaking space', '\u202f'.repeat(8)],
    ['ideographic space allowed by existing NotBlank', '\u3000'.repeat(8)],
  ])('accepts %s without altering input', (_label, value) => {
    expect(validatePassword(value!)).toBeNull()
    expect(validateCurrentPasswordForChange(value!)).toBeNull()
  })

  it.each([
    ['ASCII 73 bytes', 'x'.repeat(73)],
    ['mixed 73 bytes', '界'.repeat(24) + 'x'],
    ['CJK 75 bytes', '界'.repeat(25)],
    ['emoji 76 bytes', '🚀'.repeat(19)],
    ['combining 75 bytes', 'e\u0301'.repeat(25)],
  ])('rejects %s by UTF-8 byte count', (_label, value) => {
    expect(validatePassword(value!)).toContain('72字节')
    expect(validateCurrentPasswordForChange(value!)).toContain('72字节')
  })

  it('rejects blank passwords while retaining the current-password minimum', () => {
    expect(validatePassword(' '.repeat(8))).toBe('请输入密码')
    expect(validateCurrentPasswordForChange(' '.repeat(8))).toBe('请输入当前密码')
    expect(validatePassword('\u001c'.repeat(8))).toBe('请输入密码')
    expect(validateCurrentPasswordForChange('\u001c'.repeat(8))).toBe('请输入当前密码')
    expect(validatePassword('\u0000'.repeat(8))).toBe('请输入密码')
    expect(validateCurrentPasswordForChange('a')).toBeNull()
  })

})
