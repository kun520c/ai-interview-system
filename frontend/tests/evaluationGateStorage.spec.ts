import { describe, expect, it } from 'vitest'

import {
  buildEvaluationGateLatestKey,
  clearEvaluationGate,
  readEvaluationGate,
  readLatestEvaluationGate,
  writeEvaluationGate,
} from '@/features/interview/storage/evaluationGateStorage'
import { createMemoryStorage } from './helpers/memoryStorage'
import { evaluationGate } from './helpers/interviewFixtures'

const USER_A = 3
const USER_B = 11

describe('evaluationGateStorage', () => {
  it('round-trips a valid gate including exact answerContent and userId', () => {
    const storage = createMemoryStorage()
    const gate = evaluationGate({ userId: USER_A, answerContent: '  keep spaces  ' })
    expect(writeEvaluationGate(21, gate, storage)).toBe(true)
    expect(readEvaluationGate(21, USER_A, storage)?.answerContent).toBe('  keep spaces  ')
    expect(readEvaluationGate(21, USER_A, storage)?.nextAction).toBe('FOLLOW_UP')
    expect(readEvaluationGate(21, USER_A, storage)?.userId).toBe(USER_A)
  })

  it('removes corrupt or incomplete gates instead of trusting them', () => {
    const storage = createMemoryStorage()
    storage.setItem('interview.evaluationGate.21', '{"evaluation":{}}')
    expect(readEvaluationGate(21, USER_A, storage)).toBeNull()
    expect(storage.getItem('interview.evaluationGate.21')).toBeNull()
  })

  it('rejects a legacy gate without userId without crashing', () => {
    const storage = createMemoryStorage()
    const { userId: _userId, ...legacy } = evaluationGate()
    storage.setItem('interview.evaluationGate.21', JSON.stringify(legacy))
    expect(readEvaluationGate(21, USER_A, storage)).toBeNull()
    expect(storage.getItem('interview.evaluationGate.21')).toBeNull()
  })

  it('does not return a gate owned by a different user and does not delete it', () => {
    const storage = createMemoryStorage()
    const gate = evaluationGate({ userId: USER_A, answerContent: 'A only' })
    expect(writeEvaluationGate(21, gate, storage)).toBe(true)
    expect(readEvaluationGate(21, USER_B, storage)).toBeNull()
    expect(readEvaluationGate(21, USER_A, storage)?.answerContent).toBe('A only')
  })

  it('scopes the latest pointer per user', () => {
    const storage = createMemoryStorage()
    expect(writeEvaluationGate(21, evaluationGate({ userId: USER_A, sessionId: 21 }), storage)).toBe(true)
    expect(writeEvaluationGate(22, evaluationGate({ userId: USER_B, sessionId: 22 }), storage)).toBe(true)
    expect(storage.getItem(buildEvaluationGateLatestKey(USER_A))).toBe('21')
    expect(storage.getItem(buildEvaluationGateLatestKey(USER_B))).toBe('22')
    expect(readLatestEvaluationGate(USER_A, storage)?.sessionId).toBe(21)
    expect(readLatestEvaluationGate(USER_B, storage)?.sessionId).toBe(22)
  })

  it('ignores the legacy global latest pointer', () => {
    const storage = createMemoryStorage()
    expect(writeEvaluationGate(21, evaluationGate({ userId: USER_A }), storage)).toBe(true)
    storage.setItem('interview.evaluationGate.latestSessionId', '21')
    expect(readLatestEvaluationGate(USER_B, storage)).toBeNull()
    expect(readLatestEvaluationGate(USER_A, storage)?.userId).toBe(USER_A)
  })

  it('does not throw when sessionStorage is unavailable', () => {
    const gate = evaluationGate()
    expect(writeEvaluationGate(21, gate, null)).toBe(false)
    expect(readEvaluationGate(21, USER_A, null)).toBeNull()
    expect(() => clearEvaluationGate(21, USER_A, null)).not.toThrow()
  })
})
