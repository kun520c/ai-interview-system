import { describe, expect, it } from 'vitest'

import {
  buildSubmitRecordKey,
  deleteSubmitRecord,
  markSubmitRecordRecoveryBlocked,
  persistSubmitRecord,
  readSubmitRecord,
  SubmitRecordPersistError,
} from '@/features/interview/storage/submitRecordStorage'
import type { SubmitRecord } from '@/features/interview/types/submitRecord'
import { RecoveryBlockedReason } from '@/features/interview/types/submitRecord'
import { createMemoryStorage } from './helpers/memoryStorage'

const parts = { userId: 7, sessionId: 21, interviewQuestionId: 88 }

function makeRecord(overrides: Partial<SubmitRecord> = {}): SubmitRecord {
  return {
    userId: parts.userId,
    sessionId: parts.sessionId,
    interviewQuestionId: parts.interviewQuestionId,
    requestId: '11111111-2222-4333-8444-555555555555',
    answerContent: 'HashMap 不是并发安全的；ConcurrentHashMap 才是。',
    createdAt: '2026-09-21T01:00:00',
    updatedAt: '2026-09-21T01:00:00',
    ...overrides,
  }
}

describe('submitRecord storage', () => {
  it('namespaces keys by user, session and question', () => {
    expect(buildSubmitRecordKey(parts)).toBe('interview.submit.7.21.88')
  })

  it('round-trips the exact answerContent', () => {
    const storage = createMemoryStorage()
    const record = makeRecord({
      answerContent: '  leading space\nnew line\t tab  ',
    })
    persistSubmitRecord(record, storage)
    expect(readSubmitRecord(parts, storage)?.answerContent).toBe(record.answerContent)
  })

  it('requires a durable write before HTTP dispatch can proceed', () => {
    const storage = createMemoryStorage()
    const record = makeRecord()
    const persistThenDispatch = (): string => {
      persistSubmitRecord(record, storage)
      return 'dispatched'
    }
    expect(persistThenDispatch()).toBe('dispatched')
    expect(readSubmitRecord(parts, storage)?.requestId).toBe(record.requestId)
  })

  it('does not dispatch when persist fails', () => {
    const storage = createMemoryStorage()
    storage.setItem = () => {
      throw new Error('quota')
    }
    const persistThenDispatch = (): string => {
      persistSubmitRecord(makeRecord(), storage)
      return 'dispatched'
    }
    expect(persistThenDispatch).toThrow(SubmitRecordPersistError)
  })

  it('rejects a persist that cannot be read back', () => {
    const storage = createMemoryStorage()
    storage.setItem = () => undefined
    expect(() => persistSubmitRecord(makeRecord(), storage)).toThrow(SubmitRecordPersistError)
  })

  it('treats lost or invalid records as missing', () => {
    const storage = createMemoryStorage()
    expect(readSubmitRecord(parts, storage)).toBeNull()
    storage.setItem(buildSubmitRecordKey(parts), '{bad')
    expect(readSubmitRecord(parts, storage)).toBeNull()
  })

  it('reuses the same requestId after a later read', () => {
    const storage = createMemoryStorage()
    const record = makeRecord()
    persistSubmitRecord(record, storage)
    const restored = readSubmitRecord(parts, storage)
    expect(restored?.requestId).toBe(record.requestId)
    expect(restored?.answerContent).toBe(record.answerContent)
  })

  it('deletes a record without touching other keys', () => {
    const storage = createMemoryStorage()
    persistSubmitRecord(makeRecord(), storage)
    storage.setItem('interview.submit.7.21.89', 'other')
    deleteSubmitRecord(parts, storage)
    expect(readSubmitRecord(parts, storage)).toBeNull()
    expect(storage.getItem('interview.submit.7.21.89')).toBe('other')
  })

  it('persists a recovery-blocked marker without changing requestId or answerContent', () => {
    const storage = createMemoryStorage()
    const record = makeRecord({
      dispatchedAt: '2026-09-21T01:01:00.000Z',
    })
    persistSubmitRecord(record, storage)
    const blocked = markSubmitRecordRecoveryBlocked(
      parts,
      RecoveryBlockedReason.OTHER_REQUEST_ID,
      '2026-09-21T01:02:00.000Z',
      storage,
    )
    expect(blocked.requestId).toBe(record.requestId)
    expect(blocked.answerContent).toBe(record.answerContent)
    expect(blocked.dispatchedAt).toBe('2026-09-21T01:01:00.000Z')
    expect(blocked.recoveryBlockedReason).toBe(RecoveryBlockedReason.OTHER_REQUEST_ID)
    expect(readSubmitRecord(parts, storage)?.recoveryBlockedReason).toBe(
      RecoveryBlockedReason.OTHER_REQUEST_ID,
    )
  })

  it('rejects an invalid recoveryBlockedReason and a non-string recoveryBlockedAt', () => {
    const storage = createMemoryStorage()
    storage.setItem(
      buildSubmitRecordKey(parts),
      JSON.stringify({
        ...makeRecord(),
        recoveryBlockedReason: 'NOT_A_REASON',
      }),
    )
    expect(readSubmitRecord(parts, storage)).toBeNull()

    storage.setItem(
      buildSubmitRecordKey(parts),
      JSON.stringify({
        ...makeRecord(),
        recoveryBlockedAt: 12,
      }),
    )
    expect(readSubmitRecord(parts, storage)).toBeNull()
  })
})
