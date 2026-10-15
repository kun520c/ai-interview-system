import type { UserRole } from '@/types/enums'

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  account: string
  username: string
  password: string
  email: string
}

export interface LoginResponse {
  userId: number
  account: string
  username: string
  role: UserRole
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export interface AuthCredential {
  accessToken: string
  tokenType: string
  expiresAt: number
  userId: number
}

export const IdentityStatus = {
  ANONYMOUS: 'anonymous',
  UNKNOWN: 'unknown',
  RESOLVED: 'resolved',
  UNAVAILABLE: 'unavailable',
} as const
export type IdentityStatus = (typeof IdentityStatus)[keyof typeof IdentityStatus]

/**
 * Distinguishes a successful credential issuance from /users/me hydration.
 * POST /login 200 means the credential is valid locally even if /me fails transiently.
 */
export const LoginOutcomeKind = {
  AUTHENTICATED: 'authenticated',
  AUTHENTICATED_IDENTITY_UNAVAILABLE: 'authenticated_identity_unavailable',
} as const
export type LoginOutcomeKind = (typeof LoginOutcomeKind)[keyof typeof LoginOutcomeKind]

export interface LoginOutcome {
  kind: LoginOutcomeKind
  credentialAccepted: true
  identityStatus: typeof IdentityStatus.RESOLVED | typeof IdentityStatus.UNAVAILABLE
  response: LoginResponse
}
