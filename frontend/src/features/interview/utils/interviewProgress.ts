import { InterviewQuestionType } from '@/types/enums'
import type { InterviewSessionResponse } from '@/types/interview'

export function currentMainNumber(session: InterviewSessionResponse): number {
  return session.completedQuestionCount + 1
}

export function isFollowUpQuestion(session: InterviewSessionResponse): boolean {
  return session.currentQuestion?.questionType === InterviewQuestionType.FOLLOW_UP
}

export function progressLabel(session: InterviewSessionResponse): string {
  const mainNumber = currentMainNumber(session)
  const followUp = isFollowUpQuestion(session) ? ' · 追问' : ''
  return `第 ${mainNumber} / ${session.plannedQuestionCount} 题${followUp}`
}
