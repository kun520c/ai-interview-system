export const ErrorKind = {
  VALIDATION: 'validation',
  UNAUTHENTICATED: 'unauthenticated',
  FORBIDDEN: 'forbidden',
  NOT_FOUND: 'not_found',
  CONFLICT: 'conflict',
  PAYLOAD_TOO_LARGE: 'payload_too_large',
  EXTERNAL_SERVICE: 'external_service',
  UNEXPECTED: 'unexpected',
  NETWORK: 'network',
  UNKNOWN: 'unknown',
} as const
export type ErrorKind = (typeof ErrorKind)[keyof typeof ErrorKind]

export function classifyHttpStatus(httpStatus: number): ErrorKind {
  if (httpStatus === 0) {
    return ErrorKind.NETWORK
  }
  if (httpStatus === 400) {
    return ErrorKind.VALIDATION
  }
  if (httpStatus === 401) {
    return ErrorKind.UNAUTHENTICATED
  }
  if (httpStatus === 403) {
    return ErrorKind.FORBIDDEN
  }
  if (httpStatus === 404) {
    return ErrorKind.NOT_FOUND
  }
  if (httpStatus === 409) {
    return ErrorKind.CONFLICT
  }
  if (httpStatus === 413) {
    return ErrorKind.PAYLOAD_TOO_LARGE
  }
  if (httpStatus === 502) {
    return ErrorKind.EXTERNAL_SERVICE
  }
  if (httpStatus >= 500) {
    return ErrorKind.UNEXPECTED
  }
  return ErrorKind.UNKNOWN
}

export class ApiError extends Error {
  readonly httpStatus: number
  readonly code: number
  readonly kind: ErrorKind
  readonly cause?: unknown

  constructor(options: {
    httpStatus: number
    code: number
    message: string
    kind?: ErrorKind
    cause?: unknown
  }) {
    super(options.message)
    this.name = 'ApiError'
    this.httpStatus = options.httpStatus
    this.code = options.code
    this.kind = options.kind ?? classifyHttpStatus(options.httpStatus)
    this.cause = options.cause
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
