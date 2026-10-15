import { describe, expect, it } from 'vitest'

import { DecisionAction } from '@/types/enums'
import { resolveSessionBootstrap } from '@/features/interview/utils/sessionBootstrap'
import { currentSession, evaluationGate, followUpQuestion } from './helpers/interviewFixtures'
import type { SubmitRecord } from '@/features/interview/types/submitRecord'
import { RecoveryBlockedReason } from '@/features/interview/types/submitRecord'
const record: SubmitRecord = {
  userId: 3,
  sessionId: 21,
  interviewQuestionId: 88,
  requestId: '11111111-2222-4333-8444-555555555555',
  answerContent: 'exact answer',
  createdAt: '2026-09-21T01:00:00.000Z',
  updatedAt: '2026-09-21T01:00:00.000Z',
}

describe('sessionBootstrap', () => {
  it('redirects when current is null and there is no FINISH gate', () => {
    expect(resolveSessionBootstrap({ session: null, gate: null, submitRecord: null })).toEqual({
      kind: 'redirect-dashboard',
    })
  })

  it('restores a FINISH gate when current is null', () => {
    const gate = evaluationGate({ nextAction: DecisionAction.FINISH, nextQuestion: null })
    expect(resolveSessionBootstrap({ session: null, gate, submitRecord: null })).toEqual({
      kind: 'finish-gate',
      gate,
    })
  })

  it('restores evaluation when current still points at the answered question', () => {
    const gate = evaluationGate()
    const result = resolveSessionBootstrap({
      session: currentSession(),
      gate,
      submitRecord: null,
    })
    expect(result).toEqual({ kind: 'evaluation-gate', gate })
  })

  it('ignores a stale gate after the server moved forward', () => {
    const result = resolveSessionBootstrap({
      session: currentSession({ currentQuestion: followUpQuestion() }),
      gate: evaluationGate(),
      submitRecord: null,
    })
    expect(result).toEqual({ kind: 'stale-gate', notice: true })
  })

  it('restores dispatched pending for the current question only', () => {
    const result = resolveSessionBootstrap({
      session: currentSession(),
      gate: null,
      submitRecord: { ...record, dispatchedAt: '2026-09-21T01:01:00.000Z' },
    })
    expect(result.kind).toBe('pending')
  })

  it('prefers a recovery-blocked marker over dispatchedAt', () => {
    const blocked: SubmitRecord = {
      ...record,
      dispatchedAt: '2026-09-21T01:01:00.000Z',
      recoveryBlockedReason: RecoveryBlockedReason.OTHER_REQUEST_ID,
      recoveryBlockedAt: '2026-09-21T01:02:00.000Z',
    }
    const result = resolveSessionBootstrap({
      session: currentSession(),
      gate: null,
      submitRecord: blocked,
    })
    expect(result).toEqual({ kind: 'recovery-blocked', record: blocked })
  })

  it('does not apply a blocked Q1 record to Q2', () => {
    const result = resolveSessionBootstrap({
      session: currentSession({ currentQuestion: followUpQuestion() }),
      gate: null,
      submitRecord: {
        ...record,
        dispatchedAt: '2026-09-21T01:01:00.000Z',
        recoveryBlockedReason: RecoveryBlockedReason.OTHER_REQUEST_ID,
        recoveryBlockedAt: '2026-09-21T01:02:00.000Z',
      },
    })
    expect(result).toEqual({ kind: 'answering', draft: '', record: null })
  })

  it('does not apply a Q1 record to Q2', () => {
    const result = resolveSessionBootstrap({
      session: currentSession({ currentQuestion: followUpQuestion() }),
      gate: null,
      submitRecord: record,
    })
    expect(result).toEqual({ kind: 'answering', draft: '', record: null })
  })

  it('does not invent a question when currentQuestion is null', () => {
    expect(
      resolveSessionBootstrap({
        session: currentSession({ currentQuestion: null }),
        gate: null,
        submitRecord: null,
      }),
    ).toEqual({ kind: 'recovering-question' })
  })
})
