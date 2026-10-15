import { ApiError } from '@/api/apiError'
import type { Result } from '@/types/result'

export function isResultEnvelope<T>(value: unknown): value is Result<T> {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  if (!('data' in value)) {
    return false
  }
  const record = value as { code?: unknown; message?: unknown }
  return typeof record.code === 'number' && typeof record.message === 'string'
}

export function unwrapResultData<T>(body: unknown, httpStatus: number): T {
  if (!isResultEnvelope<T>(body)) {
    throw new ApiError({
      httpStatus,
      code: httpStatus,
      message: '响应格式无效',
    })
  }
  if (body.code !== 200) {
    throw new ApiError({
      httpStatus,
      code: body.code,
      message: body.message,
    })
  }
  return body.data
}
