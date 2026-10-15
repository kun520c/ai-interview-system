import { apiGet } from '@/api/httpClient'
import { endpoints } from '@/api/endpoints'
import type { InterviewHistoryPageResponse } from '@/types/interview'

export function fetchInterviewHistory(
  page = 1,
  pageSize = 10,
): Promise<InterviewHistoryPageResponse> {
  return apiGet<InterviewHistoryPageResponse>(endpoints.interviewHistory, {
    params: { page, pageSize },
  })
}
