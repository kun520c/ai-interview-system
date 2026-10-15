import { describe, expect, it } from 'vitest'
import axios, { AxiosError, type AxiosHeaders } from 'axios'

import { ApiError, classifyHttpStatus, ErrorKind } from '@/api/apiError'
import { normalizeAxiosError } from '@/api/httpClient'

function axiosResultError(status: number, message: string): AxiosError {
  const error = new AxiosError('Request failed')
  error.response = {
    status,
    statusText: 'Error',
    data: { code: status, message, data: null },
    headers: {},
    config: { headers: {} as AxiosHeaders },
  }
  return error
}

describe('ApiError classification', () => {
  it('maps the frozen HTTP taxonomy', () => {
    expect(classifyHttpStatus(400)).toBe(ErrorKind.VALIDATION)
    expect(classifyHttpStatus(401)).toBe(ErrorKind.UNAUTHENTICATED)
    expect(classifyHttpStatus(403)).toBe(ErrorKind.FORBIDDEN)
    expect(classifyHttpStatus(404)).toBe(ErrorKind.NOT_FOUND)
    expect(classifyHttpStatus(409)).toBe(ErrorKind.CONFLICT)
    expect(classifyHttpStatus(413)).toBe(ErrorKind.PAYLOAD_TOO_LARGE)
    expect(classifyHttpStatus(500)).toBe(ErrorKind.UNEXPECTED)
    expect(classifyHttpStatus(502)).toBe(ErrorKind.EXTERNAL_SERVICE)
    expect(classifyHttpStatus(0)).toBe(ErrorKind.NETWORK)
  })

  it('unwraps Result envelope fields from Axios errors', () => {
    const error = normalizeAxiosError(axiosResultError(400, '账号或密码错误'))
    expect(error).toBeInstanceOf(ApiError)
    expect(error.httpStatus).toBe(400)
    expect(error.code).toBe(400)
    expect(error.message).toBe('账号或密码错误')
    expect(error.kind).toBe(ErrorKind.VALIDATION)
  })

  it('classifies missing responses as network errors', () => {
    const error = normalizeAxiosError(new AxiosError('Network Error'))
    expect(error.kind).toBe(ErrorKind.NETWORK)
    expect(error.httpStatus).toBe(0)
    expect(error.message).toBe('网络异常，请稍后重试')
  })

  it('does not treat 403 as unauthenticated', () => {
    const error = normalizeAxiosError(axiosResultError(403, '权限不足，无法访问资源'))
    expect(error.kind).toBe(ErrorKind.FORBIDDEN)
  })
})

describe('axios identity', () => {
  it('keeps axios.isAxiosError working for constructed errors', () => {
    expect(axios.isAxiosError(new AxiosError('x'))).toBe(true)
  })
})
