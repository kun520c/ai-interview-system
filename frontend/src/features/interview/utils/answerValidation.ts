import { utf8ByteLength } from '@/utils/utf8ByteLength'

export const ANSWER_MAX_UTF8_BYTES = 65_535

export const AnswerValidationIssue = {
  BLANK: 'blank',
  OVERSIZED: 'oversized',
} as const
export type AnswerValidationIssue =
  (typeof AnswerValidationIssue)[keyof typeof AnswerValidationIssue]

export function validateAnswerContent(answerContent: string): AnswerValidationIssue | null {
  if (answerContent.trim().length === 0) {
    return AnswerValidationIssue.BLANK
  }
  if (utf8ByteLength(answerContent) > ANSWER_MAX_UTF8_BYTES) {
    return AnswerValidationIssue.OVERSIZED
  }
  return null
}
