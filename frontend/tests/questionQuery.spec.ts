import { describe, expect, it } from 'vitest'

import { parseAdminQuestionQuery, toAdminQuestionApiParams } from '@/features/admin/questions/utils/questionQuery'
import { QuestionCategory, QuestionDifficulty, QuestionStatus } from '@/types/enums'

describe('parseAdminQuestionQuery', () => {
  it('keeps a missing query canonical and uses defaults', () => {
    const parsed = parseAdminQuestionQuery({})
    expect(parsed).toMatchObject({
      pageNum: 1,
      pageSize: 10,
      category: null,
      keyword: null,
      canonical: true,
    })
  })

  it('clamps pageSize and keyword before they can be sent', () => {
    const parsed = parseAdminQuestionQuery({
      pageNum: '0',
      pageSize: '80',
      category: 'NOPE',
      keyword: 'x'.repeat(140),
    })
    expect(parsed.pageNum).toBe(1)
    expect(parsed.pageSize).toBe(50)
    expect(parsed.category).toBeNull()
    expect(parsed.keyword).toHaveLength(100)
    expect(parsed.canonical).toBe(false)
    expect(toAdminQuestionApiParams(parsed)).toEqual({
      pageNum: 1,
      pageSize: 50,
      keyword: 'x'.repeat(100),
    })
  })

  it('accepts legal filters', () => {
    const parsed = parseAdminQuestionQuery({
      pageNum: '2',
      pageSize: '10',
      category: QuestionCategory.SPRING,
      difficulty: QuestionDifficulty.HARD,
      status: QuestionStatus.DISABLED,
      keyword: 'Bean',
    })
    expect(parsed.canonical).toBe(true)
    expect(toAdminQuestionApiParams(parsed)).toEqual({
      pageNum: 2,
      pageSize: 10,
      category: QuestionCategory.SPRING,
      difficulty: QuestionDifficulty.HARD,
      status: QuestionStatus.DISABLED,
      keyword: 'Bean',
    })
  })
})
