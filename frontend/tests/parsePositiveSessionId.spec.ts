import { describe, expect, it } from 'vitest'

import { parsePositiveSessionId } from '@/utils/parsePositiveSessionId'

describe('parsePositiveSessionId', () => {
  it('accepts a positive integer string', () => {
    expect(parsePositiveSessionId('41')).toBe(41)
  })

  it('rejects malformed values without coercing', () => {
    expect(parsePositiveSessionId('abc')).toBeNull()
    expect(parsePositiveSessionId('0')).toBeNull()
    expect(parsePositiveSessionId('-1')).toBeNull()
    expect(parsePositiveSessionId('1.2')).toBeNull()
    expect(parsePositiveSessionId('01')).toBeNull()
    expect(parsePositiveSessionId('')).toBeNull()
  })
})
