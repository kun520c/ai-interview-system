import { describe, expect, it } from 'vitest'

import {
  REQUEST_ID_MAX_LENGTH,
  createRequestId,
  isValidRequestId,
} from '@/utils/requestId'

describe('requestId contract', () => {
  it('creates a UUID no longer than 64 characters', () => {
    const requestId = createRequestId()
    expect(requestId.length).toBeGreaterThan(0)
    expect(requestId.length).toBeLessThanOrEqual(REQUEST_ID_MAX_LENGTH)
    expect(isValidRequestId(requestId)).toBe(true)
  })

  it('rejects empty and oversized ids', () => {
    expect(isValidRequestId('')).toBe(false)
    expect(isValidRequestId('x'.repeat(65))).toBe(false)
    expect(isValidRequestId('x'.repeat(64))).toBe(true)
  })
})
