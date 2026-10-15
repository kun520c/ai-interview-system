import { QuestionCategory } from '@/types/enums'

export const CATEGORY_DISPLAY: Record<QuestionCategory, string> = {
  [QuestionCategory.JAVA_BASIC]: 'Java 基础',
  [QuestionCategory.JAVA_COLLECTION]: 'Java 集合',
  [QuestionCategory.JVM]: 'JVM',
  [QuestionCategory.JAVA_CONCURRENCY]: 'Java 并发',
  [QuestionCategory.SPRING]: 'Spring',
  [QuestionCategory.MYSQL]: 'MySQL',
  [QuestionCategory.REDIS]: 'Redis',
  [QuestionCategory.NETWORK]: '计算机网络',
  [QuestionCategory.PROJECT_SCENARIO]: '项目场景',
}

export function displayCategory(category: QuestionCategory): string {
  return CATEGORY_DISPLAY[category] ?? category
}
