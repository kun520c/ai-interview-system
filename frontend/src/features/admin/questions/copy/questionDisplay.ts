import { QuestionCategory, QuestionDifficulty, QuestionPointType, QuestionStatus } from '@/types/enums'
import { CATEGORY_DISPLAY } from '@/features/interview/copy/categoryDisplay'

export const DIFFICULTY_DISPLAY: Record<QuestionDifficulty, string> = {
  [QuestionDifficulty.EASY]: '简单',
  [QuestionDifficulty.MEDIUM]: '中等',
  [QuestionDifficulty.HARD]: '困难',
}

export const QUESTION_STATUS_DISPLAY: Record<QuestionStatus, string> = {
  [QuestionStatus.ENABLED]: '启用',
  [QuestionStatus.DISABLED]: '停用',
}

export const POINT_TYPE_DISPLAY: Record<QuestionPointType, string> = {
  [QuestionPointType.CORE]: '核心',
  [QuestionPointType.KEY]: '关键',
  [QuestionPointType.PRINCIPLE]: '原理',
  [QuestionPointType.INTERNAL]: '内部',
  [QuestionPointType.ADVANCED]: '进阶',
}

export function displayQuestionCategory(category: QuestionCategory): string {
  return CATEGORY_DISPLAY[category] ?? category
}

export function displayDifficulty(difficulty: QuestionDifficulty): string {
  return DIFFICULTY_DISPLAY[difficulty] ?? difficulty
}

export function displayQuestionStatus(status: QuestionStatus): string {
  return QUESTION_STATUS_DISPLAY[status] ?? status
}

export function displayPointType(pointType: QuestionPointType): string {
  return POINT_TYPE_DISPLAY[pointType] ?? pointType
}
