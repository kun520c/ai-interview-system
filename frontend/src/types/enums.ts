export const UserRole = {
  USER: 'USER',
  ADMIN: 'ADMIN',
} as const
export type UserRole = (typeof UserRole)[keyof typeof UserRole]

export const UserStatus = {
  ENABLED: 'ENABLED',
  DISABLED: 'DISABLED',
} as const
export type UserStatus = (typeof UserStatus)[keyof typeof UserStatus]

export const QuestionDifficulty = {
  EASY: 'EASY',
  MEDIUM: 'MEDIUM',
  HARD: 'HARD',
} as const
export type QuestionDifficulty = (typeof QuestionDifficulty)[keyof typeof QuestionDifficulty]

export const QuestionCategory = {
  JAVA_BASIC: 'JAVA_BASIC',
  JAVA_COLLECTION: 'JAVA_COLLECTION',
  JVM: 'JVM',
  JAVA_CONCURRENCY: 'JAVA_CONCURRENCY',
  SPRING: 'SPRING',
  MYSQL: 'MYSQL',
  REDIS: 'REDIS',
  NETWORK: 'NETWORK',
  PROJECT_SCENARIO: 'PROJECT_SCENARIO',
} as const
export type QuestionCategory = (typeof QuestionCategory)[keyof typeof QuestionCategory]

export const InterviewSessionStatus = {
  CREATED: 'CREATED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const
export type InterviewSessionStatus =
  (typeof InterviewSessionStatus)[keyof typeof InterviewSessionStatus]

export const InterviewQuestionType = {
  MAIN: 'MAIN',
  FOLLOW_UP: 'FOLLOW_UP',
} as const
export type InterviewQuestionType =
  (typeof InterviewQuestionType)[keyof typeof InterviewQuestionType]

export const InterviewQuestionStatus = {
  PENDING: 'PENDING',
  WAITING_ANSWER: 'WAITING_ANSWER',
  ANSWERED: 'ANSWERED',
  SKIPPED: 'SKIPPED',
} as const
export type InterviewQuestionStatus =
  (typeof InterviewQuestionStatus)[keyof typeof InterviewQuestionStatus]

export const InterviewAnswerStatus = {
  SUBMITTED: 'SUBMITTED',
  EVALUATING: 'EVALUATING',
  EVALUATED: 'EVALUATED',
  FAILED: 'FAILED',
} as const
export type InterviewAnswerStatus =
  (typeof InterviewAnswerStatus)[keyof typeof InterviewAnswerStatus]

export const EvaluationPhase = {
  INITIAL: 'INITIAL',
  FINAL: 'FINAL',
} as const
export type EvaluationPhase = (typeof EvaluationPhase)[keyof typeof EvaluationPhase]

export const DecisionAction = {
  FOLLOW_UP: 'FOLLOW_UP',
  NEXT_MAIN: 'NEXT_MAIN',
  FINISH: 'FINISH',
} as const
export type DecisionAction = (typeof DecisionAction)[keyof typeof DecisionAction]

export const EvaluationLevel = {
  EXCELLENT: 'EXCELLENT',
  GOOD: 'GOOD',
  FAIR: 'FAIR',
  WEAK: 'WEAK',
} as const
export type EvaluationLevel = (typeof EvaluationLevel)[keyof typeof EvaluationLevel]

export const InterviewReportStatus = {
  NOT_STARTED: 'NOT_STARTED',
  GENERATING: 'GENERATING',
  READY: 'READY',
  FAILED: 'FAILED',
} as const
export type InterviewReportStatus =
  (typeof InterviewReportStatus)[keyof typeof InterviewReportStatus]

export const QuestionStatus = {
  ENABLED: 'ENABLED',
  DISABLED: 'DISABLED',
} as const
export type QuestionStatus = (typeof QuestionStatus)[keyof typeof QuestionStatus]

export const QuestionPointType = {
  CORE: 'CORE',
  KEY: 'KEY',
  PRINCIPLE: 'PRINCIPLE',
  INTERNAL: 'INTERNAL',
  ADVANCED: 'ADVANCED',
} as const
export type QuestionPointType = (typeof QuestionPointType)[keyof typeof QuestionPointType]

export const KnowledgeProcessingStatus = {
  UPLOADED: 'UPLOADED',
  PROCESSING: 'PROCESSING',
  READY: 'READY',
  FAILED: 'FAILED',
  DISABLED: 'DISABLED',
} as const
export type KnowledgeProcessingStatus =
  (typeof KnowledgeProcessingStatus)[keyof typeof KnowledgeProcessingStatus]

export const KnowledgeFileType = {
  MARKDOWN: 'MARKDOWN',
  TEXT: 'TEXT',
} as const
export type KnowledgeFileType = (typeof KnowledgeFileType)[keyof typeof KnowledgeFileType]
