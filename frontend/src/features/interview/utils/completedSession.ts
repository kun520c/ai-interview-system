import { InterviewSessionStatus } from '@/types/enums'

export function isActiveInterviewStatus(status: InterviewSessionStatus): boolean {
  return status === InterviewSessionStatus.CREATED || status === InterviewSessionStatus.IN_PROGRESS
}

export function isCompletedInterviewStatus(status: InterviewSessionStatus): boolean {
  return status === InterviewSessionStatus.COMPLETED
}
