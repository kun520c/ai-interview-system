const RESUME_NOTICE_STATE_KEY = 'interviewResumeNotice'
const RESUME_NOTICE_USER_KEY = 'interviewResumeNoticeUserId'

interface PendingResumeNotice {
  userId: number
  pending: true
}

let pendingResumeNotice: PendingResumeNotice | null = null

export function armInterviewResumeNotice(userId: number): void {
  pendingResumeNotice = { userId, pending: true }
}

export function consumeInterviewResumeNotice(
  expectedUserId: number | null | undefined,
  historyState?: unknown,
): boolean {
  const fromPending =
    pendingResumeNotice?.pending === true &&
    expectedUserId != null &&
    pendingResumeNotice.userId === expectedUserId
  pendingResumeNotice = null
  const fromHistory = consumeHistoryResumeNotice(expectedUserId, historyState)
  return fromPending || fromHistory
}

export function resetInterviewResumeNoticeForTests(): void {
  pendingResumeNotice = null
}

export function peekInterviewResumeNoticeForTests(): boolean {
  return pendingResumeNotice?.pending === true
}

export function interviewResumeNoticeState(userId: number): {
  interviewResumeNotice: true
  interviewResumeNoticeUserId: number
} {
  return {
    interviewResumeNotice: true,
    interviewResumeNoticeUserId: userId,
  }
}

function consumeHistoryResumeNotice(
  expectedUserId: number | null | undefined,
  historyState?: unknown,
): boolean {
  const state =
    historyState ?? (typeof history === 'undefined' ? null : history.state)
  if (!isRecord(state) || state[RESUME_NOTICE_STATE_KEY] !== true) {
    return false
  }
  const owned =
    expectedUserId != null &&
    typeof state[RESUME_NOTICE_USER_KEY] === 'number' &&
    state[RESUME_NOTICE_USER_KEY] === expectedUserId
  delete state[RESUME_NOTICE_STATE_KEY]
  delete state[RESUME_NOTICE_USER_KEY]
  if (typeof history !== 'undefined' && typeof history.replaceState === 'function') {
    const next = { ...state }
    delete next[RESUME_NOTICE_STATE_KEY]
    delete next[RESUME_NOTICE_USER_KEY]
    try {
      history.replaceState(next, '')
    } catch {
      // History may be unavailable in some test histories.
    }
  }
  return owned
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
