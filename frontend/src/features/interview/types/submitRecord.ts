export const RecoveryBlockedReason = {
  OTHER_REQUEST_ID: 'OTHER_REQUEST_ID',
  REQUEST_ID_OTHER_QUESTION: 'REQUEST_ID_OTHER_QUESTION',
  DIFFERENT_CONTENT: 'DIFFERENT_CONTENT',
} as const
export type RecoveryBlockedReason =
  (typeof RecoveryBlockedReason)[keyof typeof RecoveryBlockedReason]

export const RECOVERY_BLOCKED_REASONS: readonly RecoveryBlockedReason[] = [
  RecoveryBlockedReason.OTHER_REQUEST_ID,
  RecoveryBlockedReason.REQUEST_ID_OTHER_QUESTION,
  RecoveryBlockedReason.DIFFERENT_CONTENT,
]

export interface SubmitRecord {
  userId: number
  sessionId: number
  interviewQuestionId: number
  requestId: string
  answerContent: string
  createdAt: string
  updatedAt: string
  /**
   * Client identity lock: from this point the request is treated as
   * possibly dispatched. Not server confirmation, delivery proof, or answer status.
   */
  dispatchedAt?: string
  recoveryBlockedReason?: RecoveryBlockedReason
  recoveryBlockedAt?: string
}

export function isRecoveryBlockedSubmitRecord(record: SubmitRecord): boolean {
  return record.recoveryBlockedReason !== undefined
}
