import { describe, expect, it } from 'vitest'

import {
  AnswerConflictKind,
  ANSWER_CONFLICT_MESSAGE,
  classifyAnswerConflict,
} from '@/features/interview/utils/submissionRecovery'

describe('submissionRecovery', () => {
  it('classifies each frozen 409 message', () => {
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.EVALUATING)).toBe(AnswerConflictKind.EVALUATING)
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.OTHER_REQUEST_ID)).toBe(
      AnswerConflictKind.OTHER_REQUEST_ID,
    )
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.REQUEST_ID_OTHER_QUESTION)).toBe(
      AnswerConflictKind.REQUEST_ID_OTHER_QUESTION,
    )
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.DIFFERENT_CONTENT)).toBe(
      AnswerConflictKind.DIFFERENT_CONTENT,
    )
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.WRONG_CURRENT)).toBe(AnswerConflictKind.WRONG_CURRENT)
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.QUESTION_NOT_SUBMITTABLE)).toBe(
      AnswerConflictKind.WRONG_CURRENT,
    )
    expect(classifyAnswerConflict(ANSWER_CONFLICT_MESSAGE.SESSION_NOT_SUBMITTABLE)).toBe(
      AnswerConflictKind.SESSION_NOT_PROGRESS,
    )
  })
})
