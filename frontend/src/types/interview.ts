import type {
  DecisionAction,
  EvaluationLevel,
  EvaluationPhase,
  InterviewAnswerStatus,
  InterviewQuestionStatus,
  InterviewQuestionType,
  InterviewReportStatus,
  InterviewSessionStatus,
  QuestionCategory,
  QuestionDifficulty,
} from '@/types/enums'

export interface CreateInterviewRequest {
  difficulty: QuestionDifficulty
}

export interface InterviewQuestionResponse {
  interviewQuestionId: number
  category: QuestionCategory
  knowledgePoint: string
  questionType: InterviewQuestionType
  content: string
}

export interface InterviewSessionResponse {
  sessionId: number
  difficulty: QuestionDifficulty
  status: InterviewSessionStatus
  plannedQuestionCount: number
  completedQuestionCount: number
  currentQuestion: InterviewQuestionResponse | null
}

export interface SubmitInterviewAnswerRequest {
  interviewQuestionId: number
  answerContent: string
  requestId: string
}

export interface InterviewEvaluationResponse {
  evaluationPhase: EvaluationPhase
  correctnessScore: number
  completenessScore: number
  depthScore: number
  clarityScore: number
  practiceScore: number
  totalScore: number
  level: EvaluationLevel
  strengths: string[]
  missingPoints: string[]
  correction: string
}

export interface SubmitInterviewAnswerResponse {
  evaluation: InterviewEvaluationResponse
  nextAction: DecisionAction
  sessionStatus: InterviewSessionStatus
  nextQuestion: InterviewQuestionResponse | null
}

export interface InterviewHistoryItemResponse {
  sessionId: number
  difficulty: QuestionDifficulty
  status: InterviewSessionStatus
  reportStatus: InterviewReportStatus
  totalScore: number | null
  plannedQuestionCount: number
  completedQuestionCount: number
  startedAt: string | null
  endedAt: string | null
  createdAt: string
}

export interface InterviewHistoryPageResponse {
  page: number
  pageSize: number
  total: number
  totalPages: number
  items: InterviewHistoryItemResponse[]
}

export interface InterviewDetailAnswerResponse {
  answerContent: string
  answerStatus: InterviewAnswerStatus
  submittedAt: string
}

export interface InterviewDetailQuestionResponse {
  interviewQuestionId: number
  questionType: InterviewQuestionType
  category: QuestionCategory
  knowledgePoint: string
  questionContent: string
  planOrder: number | null
  displayOrder: number
  status: InterviewQuestionStatus
  answer: InterviewDetailAnswerResponse | null
  evaluation: InterviewEvaluationResponse | null
}

export interface InterviewDetailResponse {
  sessionId: number
  difficulty: QuestionDifficulty
  status: InterviewSessionStatus
  reportStatus: InterviewReportStatus
  totalScore: number | null
  plannedQuestionCount: number
  completedQuestionCount: number
  startedAt: string | null
  endedAt: string | null
  createdAt: string
  questions: InterviewDetailQuestionResponse[]
}

export interface InterviewReportResponse {
  sessionId: number
  reportStatus: InterviewReportStatus
  overallScore: number | null
  result: EvaluationLevel | null
  summary: string | null
  strengths: string[] | null
  weaknesses: string[] | null
  suggestions: string[] | null
}
