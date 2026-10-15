import { DecisionAction, EvaluationLevel, EvaluationPhase, InterviewQuestionType, InterviewSessionStatus, QuestionCategory } from '@/types/enums'
import type { InterviewEvaluationResponse, InterviewQuestionResponse } from '@/types/interview'
import type { EvaluationGate } from '@/features/interview/types/evaluationGate'

export const EVALUATION_GATE_KEY_PREFIX = 'interview.evaluationGate'
export const EVALUATION_GATE_LATEST_KEY_PREFIX = 'interview.evaluationGate.latestSessionId'

export function buildEvaluationGateKey(sessionId: number): string {
  return `${EVALUATION_GATE_KEY_PREFIX}.${sessionId}`
}

export function buildEvaluationGateLatestKey(userId: number): string {
  return `${EVALUATION_GATE_LATEST_KEY_PREFIX}.${userId}`
}

function getSessionStorage(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

function isEnumValue<T extends string>(value: unknown, allowed: readonly T[]): value is T {
  return typeof value === 'string' && (allowed as readonly string[]).includes(value)
}

function isPositiveFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0
}

export function isInterviewQuestionResponse(value: unknown): value is InterviewQuestionResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const question = value as Partial<InterviewQuestionResponse>
  return (
    typeof question.interviewQuestionId === 'number' &&
    Number.isFinite(question.interviewQuestionId) &&
    isEnumValue(question.category, Object.values(QuestionCategory)) &&
    typeof question.knowledgePoint === 'string' &&
    isEnumValue(question.questionType, Object.values(InterviewQuestionType)) &&
    typeof question.content === 'string'
  )
}

export function isInterviewEvaluationResponse(value: unknown): value is InterviewEvaluationResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const evaluation = value as Partial<InterviewEvaluationResponse>
  return (
    isEnumValue(evaluation.evaluationPhase, Object.values(EvaluationPhase)) &&
    typeof evaluation.correctnessScore === 'number' &&
    typeof evaluation.completenessScore === 'number' &&
    typeof evaluation.depthScore === 'number' &&
    typeof evaluation.clarityScore === 'number' &&
    typeof evaluation.practiceScore === 'number' &&
    typeof evaluation.totalScore === 'number' &&
    isEnumValue(evaluation.level, Object.values(EvaluationLevel)) &&
    Array.isArray(evaluation.strengths) &&
    evaluation.strengths.every((item) => typeof item === 'string') &&
    Array.isArray(evaluation.missingPoints) &&
    evaluation.missingPoints.every((item) => typeof item === 'string') &&
    typeof evaluation.correction === 'string'
  )
}

export function isEvaluationGate(value: unknown): value is EvaluationGate {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const gate = value as Partial<EvaluationGate>
  const nextQuestionValid =
    gate.nextQuestion === null || isInterviewQuestionResponse(gate.nextQuestion)
  return (
    isPositiveFiniteNumber(gate.userId) &&
    isPositiveFiniteNumber(gate.sessionId) &&
    typeof gate.createdAt === 'string' &&
    isInterviewQuestionResponse(gate.answeredQuestion) &&
    typeof gate.answerContent === 'string' &&
    isInterviewEvaluationResponse(gate.evaluation) &&
    isEnumValue(gate.nextAction, Object.values(DecisionAction)) &&
    isEnumValue(gate.sessionStatus, Object.values(InterviewSessionStatus)) &&
    nextQuestionValid
  )
}

export function readEvaluationGate(
  sessionId: number,
  expectedUserId: number,
  storage: Storage | null = getSessionStorage(),
): EvaluationGate | null {
  if (!storage) {
    return null
  }
  const key = buildEvaluationGateKey(sessionId)
  const raw = storage.getItem(key)
  if (!raw) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!isEvaluationGate(parsed) || parsed.sessionId !== sessionId) {
      storage.removeItem(key)
      return null
    }
    if (parsed.userId !== expectedUserId) {
      return null
    }
    return parsed
  } catch {
    storage.removeItem(key)
    return null
  }
}

export function readLatestEvaluationGateSessionId(
  expectedUserId: number,
  storage: Storage | null = getSessionStorage(),
): number | null {
  if (!storage) {
    return null
  }
  const key = buildEvaluationGateLatestKey(expectedUserId)
  const raw = storage.getItem(key)
  if (!raw) {
    return null
  }
  const sessionId = Number(raw)
  if (!Number.isInteger(sessionId) || sessionId <= 0) {
    storage.removeItem(key)
    return null
  }
  return sessionId
}

export function readLatestEvaluationGate(
  expectedUserId: number,
  storage: Storage | null = getSessionStorage(),
): EvaluationGate | null {
  const sessionId = readLatestEvaluationGateSessionId(expectedUserId, storage)
  if (sessionId === null) {
    return null
  }
  return readEvaluationGate(sessionId, expectedUserId, storage)
}

export function writeEvaluationGate(
  sessionId: number,
  gate: EvaluationGate,
  storage: Storage | null = getSessionStorage(),
): boolean {
  if (!storage) {
    return false
  }
  if (gate.sessionId !== sessionId || !isEvaluationGate(gate)) {
    return false
  }
  try {
    storage.setItem(buildEvaluationGateKey(sessionId), JSON.stringify(gate))
    storage.setItem(buildEvaluationGateLatestKey(gate.userId), String(sessionId))
    return true
  } catch {
    return false
  }
}

export function clearEvaluationGate(
  sessionId: number,
  expectedUserId: number | null,
  storage: Storage | null = getSessionStorage(),
): void {
  if (!storage) {
    return
  }
  storage.removeItem(buildEvaluationGateKey(sessionId))
  if (expectedUserId === null) {
    return
  }
  if (readLatestEvaluationGateSessionId(expectedUserId, storage) === sessionId) {
    storage.removeItem(buildEvaluationGateLatestKey(expectedUserId))
  }
}
