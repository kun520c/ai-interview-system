let unauthorizedHandler: (() => void) | null = null
let unauthorizedInFlight = false

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

export function resetUnauthorizedGate(): void {
  unauthorizedInFlight = false
}

export function notifyUnauthorized(): void {
  if (unauthorizedInFlight) {
    return
  }
  unauthorizedInFlight = true
  if (!unauthorizedHandler) {
    resetUnauthorizedGate()
    return
  }
  unauthorizedHandler()
}
