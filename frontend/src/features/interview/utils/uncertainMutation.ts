import { ErrorKind, isApiError } from '@/api/apiError'

export function isUncertainMutationFailure(error: unknown): boolean {
  if (!isApiError(error)) {
    return true
  }
  if (error.httpStatus === 0 || error.httpStatus >= 500) {
    return true
  }
  return (
    error.kind === ErrorKind.NETWORK ||
    error.kind === ErrorKind.UNEXPECTED ||
    error.kind === ErrorKind.EXTERNAL_SERVICE ||
    error.kind === ErrorKind.UNKNOWN
  )
}
