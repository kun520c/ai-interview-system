export const REQUEST_ID_MAX_LENGTH = 64

export function createRequestId(): string {
  const requestId = crypto.randomUUID()
  if (requestId.length === 0 || requestId.length > REQUEST_ID_MAX_LENGTH) {
    throw new Error('requestId 不符合后端长度契约')
  }
  return requestId
}

export function isValidRequestId(requestId: string): boolean {
  return requestId.length > 0 && requestId.length <= REQUEST_ID_MAX_LENGTH
}
