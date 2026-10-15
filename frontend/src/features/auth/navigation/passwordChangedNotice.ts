const LOGIN_NOTICE_STATE_KEY = 'passwordChangedNotice'

export const PASSWORD_CHANGED_LOGIN_NOTICE = '密码已修改，请使用新密码重新登录'

let pendingPasswordChangedNotice = false

export function armPasswordChangedNotice(): void {
  pendingPasswordChangedNotice = true
}

export function passwordChangedNoticeState(): Record<string, true> {
  return { [LOGIN_NOTICE_STATE_KEY]: true }
}

export function consumePasswordChangedNotice(historyState?: unknown): boolean {
  const fromPending = pendingPasswordChangedNotice
  pendingPasswordChangedNotice = false
  const fromHistory = consumeHistoryNotice(historyState)
  return fromPending || fromHistory
}

export function resetPasswordChangedNoticeForTests(): void {
  pendingPasswordChangedNotice = false
}

function consumeHistoryNotice(historyState?: unknown): boolean {
  const state = historyState ?? (typeof history === 'undefined' ? null : history.state)
  if (!isRecord(state) || state[LOGIN_NOTICE_STATE_KEY] !== true) {
    return false
  }
  delete state[LOGIN_NOTICE_STATE_KEY]
  if (typeof history !== 'undefined' && typeof history.replaceState === 'function') {
    const next = { ...state }
    delete next[LOGIN_NOTICE_STATE_KEY]
    try {
      history.replaceState(next, '')
    } catch {
      // Memory history in tests may not support replaceState.
    }
  }
  return true
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
