import { apiGet, apiPost, apiPut } from '@/api/httpClient'
import { endpoints } from '@/api/endpoints'
import type {
  AdminQuestionDetailResponse,
  AdminQuestionPageQuery,
  AdminQuestionPageResponse,
  CreateQuestionRequest,
  CreateQuestionResponse,
  UpdateQuestionRequest,
  UpdateQuestionStatusRequest,
} from '@/types/question'

export function fetchAdminQuestions(
  query: AdminQuestionPageQuery,
): Promise<AdminQuestionPageResponse> {
  return apiGet<AdminQuestionPageResponse>(endpoints.adminQuestions, { params: query })
}

export function fetchAdminQuestion(questionId: number): Promise<AdminQuestionDetailResponse> {
  return apiGet<AdminQuestionDetailResponse>(endpoints.adminQuestion(questionId))
}

export function createAdminQuestion(request: CreateQuestionRequest): Promise<CreateQuestionResponse> {
  return apiPost<CreateQuestionResponse>(endpoints.adminQuestions, request)
}

export function updateAdminQuestion(
  questionId: number,
  request: UpdateQuestionRequest,
): Promise<null> {
  return apiPut<null>(endpoints.adminQuestion(questionId), request)
}

export function updateAdminQuestionStatus(
  questionId: number,
  request: UpdateQuestionStatusRequest,
): Promise<null> {
  return apiPut<null>(endpoints.adminQuestionStatus(questionId), request)
}
