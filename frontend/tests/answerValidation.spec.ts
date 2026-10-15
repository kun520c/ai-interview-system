import { describe, expect, it } from 'vitest'

import { AnswerValidationIssue, validateAnswerContent } from '@/features/interview/utils/answerValidation'
import { utf8ByteLength } from '@/utils/utf8ByteLength'

describe('answerValidation', () => {
  it('treats whitespace-only content as blank without trimming the snapshot contract', () => {
    expect(validateAnswerContent('   \n\t')).toBe(AnswerValidationIssue.BLANK)
    expect(validateAnswerContent('  HashMap  ')).toBeNull()
  })

  it('rejects UTF-8 byte length over 65535, not string length', () => {
    expect(utf8ByteLength('测')).toBe(3)
    expect(validateAnswerContent('a'.repeat(65_535))).toBeNull()
    expect(validateAnswerContent('a'.repeat(65_536))).toBe(AnswerValidationIssue.OVERSIZED)
    expect(validateAnswerContent('测'.repeat(21_846))).toBe(AnswerValidationIssue.OVERSIZED)
  })
})
