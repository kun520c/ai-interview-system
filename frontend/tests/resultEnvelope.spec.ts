import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/apiError'
import { isResultEnvelope, unwrapResultData } from '@/api/resultEnvelope'
import type { InterviewSessionResponse } from '@/types/interview'

describe('Result envelope validation', () => {
  it('accepts code/message with explicit null data', () => {
    const body = { code: 200, message: 'success', data: null }
    expect(isResultEnvelope(body)).toBe(true)
    expect(unwrapResultData<null>(body, 200)).toBeNull()
  })

  it('preserves null for InterviewSessionResponse | null', () => {
    const body = { code: 200, message: 'success', data: null }
    const data = unwrapResultData<InterviewSessionResponse | null>(body, 200)
    expect(data).toBeNull()
  })

  it('rejects a 200 envelope that omits the data property', () => {
    const body = { code: 200, message: 'success' }
    expect(isResultEnvelope(body)).toBe(false)
    expect(() => unwrapResultData<unknown>(body, 200)).toThrow(ApiError)
    try {
      unwrapResultData<unknown>(body, 200)
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).message).toBe('响应格式无效')
    }
  })
})
