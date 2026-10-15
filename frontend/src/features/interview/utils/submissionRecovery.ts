import { ErrorKind, isApiError } from '@/api/apiError'

export const AnswerConflictKind = {
  EVALUATING: 'EVALUATING',
  OTHER_REQUEST_ID: 'OTHER_REQUEST_ID',
  REQUEST_ID_OTHER_QUESTION: 'REQUEST_ID_OTHER_QUESTION',
  DIFFERENT_CONTENT: 'DIFFERENT_CONTENT',
  WRONG_CURRENT: 'WRONG_CURRENT',
  SESSION_NOT_PROGRESS: 'SESSION_NOT_PROGRESS',
  UNKNOWN_CONFLICT: 'UNKNOWN_CONFLICT',
} as const
export type AnswerConflictKind = (typeof AnswerConflictKind)[keyof typeof AnswerConflictKind]

export const ANSWER_CONFLICT_MESSAGE = {
  EVALUATING: '答案正在评估中，请稍后重试',
  OTHER_REQUEST_ID: '当前问题已经使用其他requestId提交过答案',
  REQUEST_ID_OTHER_QUESTION: 'requestId已用于其他问题',
  DIFFERENT_CONTENT: 'requestId已用于不同的答案内容',
  WRONG_CURRENT: '提交的问题不是当前问题',
  QUESTION_NOT_SUBMITTABLE: '当前问题不可提交答案',
  SESSION_NOT_SUBMITTABLE: '当前InterviewSession不可提交答案',
} as const

export const EVALUATION_SERVICE_UNAVAILABLE = '面试评价服务当前不可用'

export function classifyAnswerConflict(message: string): AnswerConflictKind {
  if (message === ANSWER_CONFLICT_MESSAGE.EVALUATING) {
    return AnswerConflictKind.EVALUATING
  }
  if (message === ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID) {
    return AnswerConflictKind.OTHER_REQUEST_ID
  }
  if (message === ANSWER_CONFLICT_MESSAGE.REQUEST_ID_OTHER_QUESTION) {
    return AnswerConflictKind.REQUEST_ID_OTHER_QUESTION
  }
  if (message === ANSWER_CONFLICT_MESSAGE.DIFFERENT_CONTENT) {
    return AnswerConflictKind.DIFFERENT_CONTENT
  }
  if (
    message === ANSWER_CONFLICT_MESSAGE.WRONG_CURRENT ||
    message === ANSWER_CONFLICT_MESSAGE.QUESTION_NOT_SUBMITTABLE
  ) {
    return AnswerConflictKind.WRONG_CURRENT
  }
  if (message === ANSWER_CONFLICT_MESSAGE.SESSION_NOT_SUBMITTABLE) {
    return AnswerConflictKind.SESSION_NOT_PROGRESS
  }
  return AnswerConflictKind.UNKNOWN_CONFLICT
}

export function isEvaluatingConflict(error: unknown): boolean {
  return isApiError(error) && error.kind === ErrorKind.CONFLICT && classifyAnswerConflict(error.message) === AnswerConflictKind.EVALUATING
}

export function isEvaluationServiceUnavailable(error: unknown): boolean {
  return isApiError(error) && error.kind === ErrorKind.VALIDATION && error.message === EVALUATION_SERVICE_UNAVAILABLE
}
