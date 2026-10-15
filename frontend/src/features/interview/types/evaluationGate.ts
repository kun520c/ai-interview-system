import type { InterviewEvaluationResponse, InterviewQuestionResponse } from '@/types/interview'
import type { DecisionAction, InterviewSessionStatus } from '@/types/enums'
import { DecisionAction as DecisionActionValues, InterviewSessionStatus as SessionStatusValues } from '@/types/enums'

export interface EvaluationGate {
  /** Client storage ownership only. Never sent to the backend. */
  userId: number
  sessionId: number
  createdAt: string
  answeredQuestion: InterviewQuestionResponse
  answerContent: string
  evaluation: InterviewEvaluationResponse
  nextAction: DecisionAction
  nextQuestion: InterviewQuestionResponse | null
  sessionStatus: InterviewSessionStatus
}

export const EVALUATION_GATE_NEXT_ACTIONS: readonly DecisionAction[] = [
  DecisionActionValues.FOLLOW_UP,
  DecisionActionValues.NEXT_MAIN,
  DecisionActionValues.FINISH,
]

export const EVALUATION_GATE_SESSION_STATUSES: readonly InterviewSessionStatus[] = [
  SessionStatusValues.CREATED,
  SessionStatusValues.IN_PROGRESS,
  SessionStatusValues.COMPLETED,
  SessionStatusValues.CANCELLED,
]
