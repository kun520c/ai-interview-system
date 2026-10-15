import { QuestionCategory, QuestionDifficulty, QuestionPointType, QuestionStatus } from '@/types/enums'
import { utf8ByteLength } from '@/utils/utf8ByteLength'
import type { ScoringPointRequest } from '@/types/question'

export const QUESTION_KNOWLEDGE_POINT_MAX = 100
export const QUESTION_TEXT_MAX_UTF8_BYTES = 65_535

export const POINT_TYPE_OPTIONS: QuestionPointType[] = [
  QuestionPointType.CORE,
  QuestionPointType.KEY,
  QuestionPointType.PRINCIPLE,
  QuestionPointType.INTERNAL,
  QuestionPointType.ADVANCED,
]

export function emptyScoringPoint(): ScoringPointRequest {
  return {
    pointType: QuestionPointType.CORE,
    content: '',
    weight: 1,
  }
}

export function scoringWeightSum(points: ScoringPointRequest[]): number {
  return points.reduce((sum, point) => sum + (Number.isFinite(point.weight) ? point.weight : 0), 0)
}

export function validateKnowledgePoint(value: string): string | null {
  const knowledgePoint = value.trim()
  if (!knowledgePoint) {
    return '知识点不能为空'
  }
  if (knowledgePoint.length > QUESTION_KNOWLEDGE_POINT_MAX) {
    return '知识点长度不能超过100位'
  }
  return null
}

export function validateQuestionText(value: string, label: string): string | null {
  if (!value.trim()) {
    return `${label}不能为空`
  }
  if (utf8ByteLength(value) > QUESTION_TEXT_MAX_UTF8_BYTES) {
    return `${label}不能超过65535个UTF-8字节`
  }
  return null
}

export function validateScoringPoints(points: ScoringPointRequest[]): string | null {
  if (points.length === 0) {
    return '题目至少需要一个评分点'
  }
  for (const [index, point] of points.entries()) {
    if (!POINT_TYPE_OPTIONS.includes(point.pointType)) {
      return `评分点 ${index + 1} 类型无效`
    }
    const contentError = validateQuestionText(point.content, `评分点 ${index + 1} 内容`)
    if (contentError) {
      return contentError
    }
    if (!Number.isInteger(point.weight) || point.weight < 1 || point.weight > 100) {
      return `评分点 ${index + 1} 权重必须是 1 到 100 的整数`
    }
  }
  if (scoringWeightSum(points) !== 100) {
    return '评分点权重合计必须等于 100'
  }
  return null
}

export function isQuestionCategory(value: string): value is QuestionCategory {
  return (Object.values(QuestionCategory) as string[]).includes(value)
}

export function isQuestionDifficulty(value: string): value is QuestionDifficulty {
  return (Object.values(QuestionDifficulty) as string[]).includes(value)
}

export function isQuestionStatus(value: string): value is QuestionStatus {
  return (Object.values(QuestionStatus) as string[]).includes(value)
}
