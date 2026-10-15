import { isValidRequestId } from '@/utils/requestId'
import {
  RECOVERY_BLOCKED_REASONS,
  type RecoveryBlockedReason,
  type SubmitRecord,
} from '@/features/interview/types/submitRecord'

export const SUBMIT_RECORD_KEY_PREFIX = 'interview.submit'

export class SubmitRecordPersistError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SubmitRecordPersistError'
  }
}

export interface SubmitRecordKeyParts {
  userId: number
  sessionId: number
  interviewQuestionId: number
}

function getStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function buildSubmitRecordKey(parts: SubmitRecordKeyParts): string {
  return `${SUBMIT_RECORD_KEY_PREFIX}.${parts.userId}.${parts.sessionId}.${parts.interviewQuestionId}`
}

function isRecordObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isRecoveryBlockedReason(value: unknown): value is RecoveryBlockedReason {
  return typeof value === 'string' && (RECOVERY_BLOCKED_REASONS as readonly string[]).includes(value)
}

function isSubmitRecord(value: unknown): value is SubmitRecord {
  if (!isRecordObject(value)) {
    return false
  }
  const reasonValid =
    value.recoveryBlockedReason === undefined || isRecoveryBlockedReason(value.recoveryBlockedReason)
  const blockedAtValid =
    value.recoveryBlockedAt === undefined || typeof value.recoveryBlockedAt === 'string'
  return (
    typeof value.userId === 'number' &&
    typeof value.sessionId === 'number' &&
    typeof value.interviewQuestionId === 'number' &&
    typeof value.requestId === 'string' &&
    isValidRequestId(value.requestId) &&
    typeof value.answerContent === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.updatedAt === 'string' &&
    (value.dispatchedAt === undefined || typeof value.dispatchedAt === 'string') &&
    reasonValid &&
    blockedAtValid
  )
}

export function readSubmitRecord(
  parts: SubmitRecordKeyParts,
  storage: Storage | null = getStorage(),
): SubmitRecord | null {
  if (!storage) {
    return null
  }
  const raw = storage.getItem(buildSubmitRecordKey(parts))
  if (!raw) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!isSubmitRecord(parsed)) {
      storage.removeItem(buildSubmitRecordKey(parts))
      return null
    }
    if (
      parsed.userId !== parts.userId ||
      parsed.sessionId !== parts.sessionId ||
      parsed.interviewQuestionId !== parts.interviewQuestionId
    ) {
      storage.removeItem(buildSubmitRecordKey(parts))
      return null
    }
    return parsed
  } catch {
    storage.removeItem(buildSubmitRecordKey(parts))
    return null
  }
}

export function persistSubmitRecord(
  record: SubmitRecord,
  storage: Storage | null = getStorage(),
): void {
  if (!storage) {
    throw new SubmitRecordPersistError('本机恢复存储不可用，答案未提交')
  }
  if (!isValidRequestId(record.requestId)) {
    throw new SubmitRecordPersistError('requestId 不符合后端长度契约')
  }
  if (!isSubmitRecord(record)) {
    throw new SubmitRecordPersistError('本机恢复记录不合法，答案未提交')
  }
  const key = buildSubmitRecordKey(record)
  const serialized = JSON.stringify(record)
  try {
    storage.setItem(key, serialized)
    if (storage.getItem(key) !== serialized) {
      throw new SubmitRecordPersistError('本机恢复存储写入校验失败，答案未提交')
    }
  } catch (error) {
    if (error instanceof SubmitRecordPersistError) {
      throw error
    }
    throw new SubmitRecordPersistError('本机恢复存储写入失败，答案未提交')
  }
}

export function markSubmitRecordDispatched(
  parts: SubmitRecordKeyParts,
  dispatchedAt: string,
  storage: Storage | null = getStorage(),
): SubmitRecord {
  const existing = readSubmitRecord(parts, storage)
  if (!existing) {
    throw new SubmitRecordPersistError('找不到待提交记录，无法标记已发送')
  }
  const next: SubmitRecord = {
    ...existing,
    dispatchedAt,
    updatedAt: dispatchedAt,
  }
  persistSubmitRecord(next, storage)
  return next
}

export function markSubmitRecordRecoveryBlocked(
  parts: SubmitRecordKeyParts,
  reason: RecoveryBlockedReason,
  blockedAt: string,
  storage: Storage | null = getStorage(),
): SubmitRecord {
  const existing = readSubmitRecord(parts, storage)
  if (!existing) {
    throw new SubmitRecordPersistError('找不到待提交记录，无法标记恢复阻断')
  }
  const next: SubmitRecord = {
    ...existing,
    recoveryBlockedReason: reason,
    recoveryBlockedAt: blockedAt,
    updatedAt: blockedAt,
  }
  persistSubmitRecord(next, storage)
  return next
}

export function deleteSubmitRecord(
  parts: SubmitRecordKeyParts,
  storage: Storage | null = getStorage(),
): void {
  storage?.removeItem(buildSubmitRecordKey(parts))
}
