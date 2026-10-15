export interface SessionScopeEvent {
  generation: number
  userId: number | null
}

type SessionScopeListener = (event: SessionScopeEvent) => void

let generation = 0
const listeners = new Set<SessionScopeListener>()

export function getSessionGeneration(): number {
  return generation
}

export function isCurrentIdentityScope(
  operationUserId: number | null | undefined,
  operationGeneration: number | undefined,
  currentUserId: number | null,
): boolean {
  return (
    operationUserId != null &&
    currentUserId != null &&
    operationUserId === currentUserId &&
    operationGeneration === generation
  )
}

export function subscribeSessionScope(listener: SessionScopeListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function notifySessionIdentityChanged(userId: number | null): number {
  generation += 1
  const event: SessionScopeEvent = { generation, userId }
  for (const listener of listeners) {
    listener(event)
  }
  return generation
}

export function resetSessionScopeForTests(): void {
  generation = 0
  listeners.clear()
}
