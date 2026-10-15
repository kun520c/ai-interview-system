import { QuestionCategory, QuestionDifficulty } from '@/types/enums'

export interface InterviewPlanCopy {
  difficulty: QuestionDifficulty
  mainQuestionCount: number
  categories: QuestionCategory[]
  helper: string
}

export const INTERVIEW_PLANS: Record<QuestionDifficulty, InterviewPlanCopy> = {
  [QuestionDifficulty.EASY]: {
    difficulty: QuestionDifficulty.EASY,
    mainQuestionCount: 5,
    categories: [
      QuestionCategory.JAVA_BASIC,
      QuestionCategory.JAVA_COLLECTION,
      QuestionCategory.SPRING,
      QuestionCategory.MYSQL,
      QuestionCategory.NETWORK,
    ],
    helper: '基础覆盖',
  },
  [QuestionDifficulty.MEDIUM]: {
    difficulty: QuestionDifficulty.MEDIUM,
    mainQuestionCount: 7,
    categories: [
      QuestionCategory.JAVA_BASIC,
      QuestionCategory.JAVA_COLLECTION,
      QuestionCategory.JVM,
      QuestionCategory.JAVA_CONCURRENCY,
      QuestionCategory.SPRING,
      QuestionCategory.MYSQL,
      QuestionCategory.REDIS,
    ],
    helper: '范围更完整',
  },
  [QuestionDifficulty.HARD]: {
    difficulty: QuestionDifficulty.HARD,
    mainQuestionCount: 9,
    categories: [
      QuestionCategory.JAVA_BASIC,
      QuestionCategory.JAVA_COLLECTION,
      QuestionCategory.JVM,
      QuestionCategory.JAVA_CONCURRENCY,
      QuestionCategory.SPRING,
      QuestionCategory.MYSQL,
      QuestionCategory.REDIS,
      QuestionCategory.NETWORK,
      QuestionCategory.PROJECT_SCENARIO,
    ],
    helper: '覆盖全部核心领域与项目场景',
  },
}

export const PEER_DIFFICULTIES: QuestionDifficulty[] = [
  QuestionDifficulty.EASY,
  QuestionDifficulty.MEDIUM,
  QuestionDifficulty.HARD,
]
