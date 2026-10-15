import type { LocationQuery, LocationQueryValue } from 'vue-router'

import {
  QuestionCategory,
  QuestionDifficulty,
  QuestionStatus,
} from '@/types/enums'

export const ADMIN_QUESTION_DEFAULT_PAGE = 1
export const ADMIN_QUESTION_DEFAULT_PAGE_SIZE = 10
export const ADMIN_QUESTION_MAX_PAGE_SIZE = 50
export const ADMIN_QUESTION_KEYWORD_MAX = 100

export interface AdminQuestionListQuery {
  pageNum: number
  pageSize: number
  category: QuestionCategory | null
  difficulty: QuestionDifficulty | null
  status: QuestionStatus | null
  keyword: string | null
  canonical: boolean
}

function firstQueryValue(value: LocationQueryValue | LocationQueryValue[] | undefined): string | null {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : null
  }
  return typeof value === 'string' ? value : null
}

function parsePositiveInteger(raw: string | null): number | null {
  if (raw === null) {
    return null
  }
  if (!/^[1-9]\d*$/.test(raw)) {
    return null
  }
  const parsed = Number(raw)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function parseEnum<T extends string>(
  raw: string | null,
  allowed: readonly T[],
): T | null {
  if (raw === null) {
    return null
  }
  return allowed.includes(raw as T) ? (raw as T) : null
}

export function parseAdminQuestionQuery(query: LocationQuery): AdminQuestionListQuery {
  const rawPageNum = firstQueryValue(query.pageNum)
  const rawPageSize = firstQueryValue(query.pageSize)
  const rawCategory = firstQueryValue(query.category)
  const rawDifficulty = firstQueryValue(query.difficulty)
  const rawStatus = firstQueryValue(query.status)
  const rawKeyword = firstQueryValue(query.keyword)

  const parsedPageNum = parsePositiveInteger(rawPageNum)
  const parsedPageSize = parsePositiveInteger(rawPageSize)
  const pageNum = parsedPageNum ?? ADMIN_QUESTION_DEFAULT_PAGE
  let pageSize = parsedPageSize ?? ADMIN_QUESTION_DEFAULT_PAGE_SIZE
  if (pageSize > ADMIN_QUESTION_MAX_PAGE_SIZE) {
    pageSize = ADMIN_QUESTION_MAX_PAGE_SIZE
  }

  const category = parseEnum(rawCategory, Object.values(QuestionCategory))
  const difficulty = parseEnum(rawDifficulty, Object.values(QuestionDifficulty))
  const status = parseEnum(rawStatus, Object.values(QuestionStatus))

  let keyword: string | null = null
  if (rawKeyword !== null) {
    const trimmed = rawKeyword.trim()
    if (trimmed.length > 0) {
      keyword = trimmed.slice(0, ADMIN_QUESTION_KEYWORD_MAX)
    }
  }

  const canonical =
    (rawPageNum === null ? pageNum === ADMIN_QUESTION_DEFAULT_PAGE : rawPageNum === String(pageNum)) &&
    (rawPageSize === null ? pageSize === ADMIN_QUESTION_DEFAULT_PAGE_SIZE : rawPageSize === String(pageSize)) &&
    (rawCategory === null ? category === null : rawCategory === category) &&
    (rawDifficulty === null ? difficulty === null : rawDifficulty === difficulty) &&
    (rawStatus === null ? status === null : rawStatus === status) &&
    (rawKeyword === null ? keyword === null : rawKeyword === keyword)

  return {
    pageNum,
    pageSize,
    category,
    difficulty,
    status,
    keyword,
    canonical,
  }
}

export function adminQuestionQueryRecord(query: AdminQuestionListQuery): Record<string, string> {
  const record: Record<string, string> = {
    pageNum: String(query.pageNum),
    pageSize: String(query.pageSize),
  }
  if (query.category) {
    record.category = query.category
  }
  if (query.difficulty) {
    record.difficulty = query.difficulty
  }
  if (query.status) {
    record.status = query.status
  }
  if (query.keyword) {
    record.keyword = query.keyword
  }
  return record
}

export function toAdminQuestionApiParams(query: AdminQuestionListQuery): {
  pageNum: number
  pageSize: number
  category?: QuestionCategory
  difficulty?: QuestionDifficulty
  status?: QuestionStatus
  keyword?: string
} {
  return {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    ...(query.category ? { category: query.category } : {}),
    ...(query.difficulty ? { difficulty: query.difficulty } : {}),
    ...(query.status ? { status: query.status } : {}),
    ...(query.keyword ? { keyword: query.keyword } : {}),
  }
}
