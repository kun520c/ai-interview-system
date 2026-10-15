import type {
  QuestionCategory,
  QuestionDifficulty,
  QuestionPointType,
  QuestionStatus,
} from '@/types/enums'

export interface AdminQuestionListItem {
  id: number
  category: QuestionCategory
  knowledgePoint: string
  difficulty: QuestionDifficulty
  questionContent: string
  status: QuestionStatus
  updatedAt: string
}

export interface AdminQuestionPageResponse {
  records: AdminQuestionListItem[]
  total: number
  pageNum: number
  pageSize: number
  totalPages: number
}

export interface AdminScoringPointDetail {
  pointType: QuestionPointType
  content: string
  weight: number
}

export interface AdminQuestionDetailResponse {
  id: number
  category: QuestionCategory
  knowledgePoint: string
  difficulty: QuestionDifficulty
  content: string
  referenceAnswer: string
  status: QuestionStatus
  createdAt: string
  updatedAt: string
  scoringPoints: AdminScoringPointDetail[]
}

export interface ScoringPointRequest {
  pointType: QuestionPointType
  content: string
  weight: number
}

export interface CreateQuestionRequest {
  category: QuestionCategory
  knowledgePoint: string
  difficulty: QuestionDifficulty
  questionContent: string
  referenceAnswer: string
  scoringPoints: ScoringPointRequest[]
}

export type UpdateQuestionRequest = CreateQuestionRequest

export interface CreateQuestionResponse {
  questionId: number
}

export interface UpdateQuestionStatusRequest {
  status: QuestionStatus
}

export interface AdminQuestionPageQuery {
  pageNum?: number
  pageSize?: number
  category?: QuestionCategory
  difficulty?: QuestionDifficulty
  status?: QuestionStatus
  keyword?: string
}
