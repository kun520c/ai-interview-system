export const AUTH_LIMITS = {
  accountMax: 50,
  usernameMax: 50,
  passwordMin: 8,
  passwordMax: 72,
  emailMax: 100,
} as const

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function trimmed(value: string): string {
  return value.trim()
}

export function validateAccount(value: string): string | null {
  const account = trimmed(value)
  if (!account) {
    return '请输入账号'
  }
  if (account.length > AUTH_LIMITS.accountMax) {
    return `账号不能超过${AUTH_LIMITS.accountMax}个字符`
  }
  return null
}

export function validateUsername(value: string): string | null {
  const username = trimmed(value)
  if (!username) {
    return '请输入用户名'
  }
  if (username.length > AUTH_LIMITS.usernameMax) {
    return `用户名不能超过${AUTH_LIMITS.usernameMax}个字符`
  }
  return null
}

export function validatePassword(value: string): string | null {
  if (!value) {
    return '请输入密码'
  }
  if (value.length < AUTH_LIMITS.passwordMin || value.length > AUTH_LIMITS.passwordMax) {
    return `密码长度为${AUTH_LIMITS.passwordMin}到${AUTH_LIMITS.passwordMax}个字符`
  }
  return null
}

export function validateCurrentPasswordForChange(value: string): string | null {
  if (!value) {
    return '请输入当前密码'
  }
  if (value.length > AUTH_LIMITS.passwordMax) {
    return `当前密码不能超过${AUTH_LIMITS.passwordMax}个字符`
  }
  return null
}

export function validateEmail(value: string): string | null {
  const email = trimmed(value)
  if (!email) {
    return '请输入邮箱'
  }
  if (email.length > AUTH_LIMITS.emailMax) {
    return `邮箱不能超过${AUTH_LIMITS.emailMax}个字符`
  }
  if (!EMAIL_PATTERN.test(email)) {
    return '请输入有效邮箱'
  }
  return null
}

export function validateConfirmPassword(password: string, confirmPassword: string): string | null {
  if (!confirmPassword) {
    return '请再次输入密码'
  }
  if (confirmPassword !== password) {
    return '两次输入的密码不一致'
  }
  return null
}
