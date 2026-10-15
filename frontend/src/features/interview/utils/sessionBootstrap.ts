import { DecisionAction } from '@/types/enums'
import type { EvaluationGate } from '@/features/interview/types/evaluationGate'
import type { SubmitRecord } from '@/features/interview/types/submitRecord'
import { isRecoveryBlockedSubmitRecord } from '@/features/interview/types/submitRecord'
import type { InterviewSessionResponse } from '@/types/interview'
import { SessionPhase } from '@/features/interview/types/sessionPhase'

export type SessionBootstrapResult =
  | { kind: 'redirect-dashboard' }
  | { kind: 'finish-gate'; gate: EvaluationGate }
  | { kind: 'recovering-question' }
  | { kind: 'evaluation-gate'; gate: EvaluationGate }
  | { kind: 'stale-gate'; notice: true }
  | { kind: 'recovery-blocked'; record: SubmitRecord }
  | { kind: 'pending'; record: SubmitRecord }
  | { kind: 'answering'; draft: string; record: SubmitRecord | null }

export function resolveSessionBootstrap(input: {
  session: InterviewSessionResponse | null
  gate: EvaluationGate | null
  submitRecord: SubmitRecord | null
}): SessionBootstrapResult {
  const { session, gate, submitRecord } = input

  if (!session) {
    if (gate?.nextAction === DecisionAction.FINISH) {
      return { kind: 'finish-gate', gate }
    }
    return { kind: 'redirect-dashboard' }
  }

  if (!session.currentQuestion) {
    return { kind: 'recovering-question' }
  }

  const currentQuestionId = session.currentQuestion.interviewQuestionId
  if (gate && gate.sessionId === session.sessionId) {
    if (gate.answeredQuestion.interviewQuestionId === currentQuestionId) {
      return { kind: 'evaluation-gate', gate }
    }
    return { kind: 'stale-gate', notice: true }
  }

  if (
    submitRecord &&
    submitRecord.sessionId === session.sessionId &&
    submitRecord.interviewQuestionId === currentQuestionId
  ) {
    if (isRecoveryBlockedSubmitRecord(submitRecord)) {
      return { kind: 'recovery-blocked', record: submitRecord }
    }
    if (submitRecord.dispatchedAt) {
      return { kind: 'pending', record: submitRecord }
    }
    return { kind: 'answering', draft: submitRecord.answerContent, record: submitRecord }
  }

  return { kind: 'answering', draft: '', record: null }
}

export function phaseFromBootstrap(result: SessionBootstrapResult): SessionPhase {
  switch (result.kind) {
    case 'finish-gate':
    case 'evaluation-gate':
      return SessionPhase.EVALUATION_GATE
    case 'recovery-blocked':
      return SessionPhase.RECOVERY_BLOCKED
    case 'pending':
      return SessionPhase.EVALUATION_PENDING
    case 'recovering-question':
      return SessionPhase.ERROR
    case 'redirect-dashboard':
      return SessionPhase.BOOTSTRAP
    default:
      return SessionPhase.ANSWERING
  }
}
