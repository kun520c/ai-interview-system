import { apiGet, apiPost } from '@/api/httpClient'
import { endpoints } from '@/api/endpoints'
import type {
  CreateInterviewRequest,
  InterviewDetailResponse,
  InterviewReportResponse,
  InterviewSessionResponse,
  SubmitInterviewAnswerRequest,
  SubmitInterviewAnswerResponse,
} from '@/types/interview'

export function createInterview(request: CreateInterviewRequest): Promise<InterviewSessionResponse> {
  return apiPost<InterviewSessionResponse>(endpoints.interviews, request)
}

export const createOrResumeInterview = createInterview

export function fetchCurrentInterview(): Promise<InterviewSessionResponse | null> {
  return apiGet<InterviewSessionResponse | null>(endpoints.currentInterview)
}

export function submitInterviewAnswer(
  sessionId: number,
  request: SubmitInterviewAnswerRequest,
): Promise<SubmitInterviewAnswerResponse> {
  return apiPost<SubmitInterviewAnswerResponse>(endpoints.interviewAnswers(sessionId), request)
}

export function fetchInterviewDetail(sessionId: number): Promise<InterviewDetailResponse> {
  return apiGet<InterviewDetailResponse>(endpoints.interviewDetail(sessionId))
}

export function fetchInterviewReport(sessionId: number): Promise<InterviewReportResponse> {
  return apiGet<InterviewReportResponse>(endpoints.interviewReport(sessionId))
}

export function generateInterviewReport(sessionId: number): Promise<InterviewReportResponse> {
  return apiPost<InterviewReportResponse>(endpoints.interviewReport(sessionId))
}
