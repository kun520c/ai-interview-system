import { EvaluationLevel } from '@/types/enums'

export const EVALUATION_LEVEL_LABEL: Record<EvaluationLevel, string> = {
  [EvaluationLevel.EXCELLENT]: '优秀',
  [EvaluationLevel.GOOD]: '良好',
  [EvaluationLevel.FAIR]: '一般',
  [EvaluationLevel.WEAK]: '薄弱',
}

export function evaluationLevelFromScore(totalScore: number): EvaluationLevel {
  if (totalScore >= 90) {
    return EvaluationLevel.EXCELLENT
  }
  if (totalScore >= 80) {
    return EvaluationLevel.GOOD
  }
  if (totalScore >= 60) {
    return EvaluationLevel.FAIR
  }
  return EvaluationLevel.WEAK
}

export function displayEvaluationLevel(level: EvaluationLevel): string {
  return EVALUATION_LEVEL_LABEL[level]
}

export function displayReportResult(result: string | null | undefined): string {
  if (!result) {
    return ''
  }
  if ((Object.values(EvaluationLevel) as string[]).includes(result)) {
    return EVALUATION_LEVEL_LABEL[result as EvaluationLevel]
  }
  return result
}
