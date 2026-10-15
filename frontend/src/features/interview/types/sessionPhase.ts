export const SessionPhase = {
  BOOTSTRAP: 'BOOTSTRAP',
  ANSWERING: 'ANSWERING',
  SUBMITTING: 'SUBMITTING',
  EVALUATION_PENDING: 'EVALUATION_PENDING',
  EVALUATION_GATE: 'EVALUATION_GATE',
  RECOVERY_BLOCKED: 'RECOVERY_BLOCKED',
  ERROR: 'ERROR',
} as const
export type SessionPhase = (typeof SessionPhase)[keyof typeof SessionPhase]
