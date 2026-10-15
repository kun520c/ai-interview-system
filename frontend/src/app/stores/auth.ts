import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { fetchCurrentUser } from '@/features/auth/api/userApi'
import { login as loginRequest } from '@/features/auth/api/authApi'
import {
  clearAuthCredential,
  readAuthCredential,
  writeAuthCredential,
} from '@/features/auth/storage/authStorage'
import { notifySessionIdentityChanged } from '@/app/session/sessionScope'
import { resetUnauthorizedGate } from '@/api/unauthorized'
import { isApiError } from '@/api/apiError'
import { IdentityStatus, LoginOutcomeKind } from '@/types/auth'
import { UserRole } from '@/types/enums'
import type { AuthCredential, LoginOutcome, LoginRequest, LoginResponse } from '@/types/auth'
import type { CurrentUserResponse } from '@/types/user'

function toCredential(response: LoginResponse, now = Date.now()): AuthCredential {
  return {
    accessToken: response.accessToken,
    tokenType: response.tokenType || 'Bearer',
    expiresAt: now + response.expiresInSeconds * 1000,
    userId: response.userId,
  }
}

export const useAuthStore = defineStore('auth', () => {
  const ready = ref(false)
  const accessToken = ref<string | null>(null)
  const tokenType = ref('Bearer')
  const expiresAt = ref<number | null>(null)
  const userId = ref<number | null>(null)
  const me = ref<CurrentUserResponse | null>(null)
  const seedRole = ref<(typeof UserRole)[keyof typeof UserRole] | null>(null)
  const identityStatus = ref<IdentityStatus>(IdentityStatus.ANONYMOUS)
  let initializePromise: Promise<void> | null = null
  let publishedUserId: number | null = null

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(
    () => identityStatus.value === IdentityStatus.RESOLVED && me.value?.role === UserRole.ADMIN,
  )

  function publishIdentity(nextUserId: number | null): void {
    if (publishedUserId === nextUserId) {
      return
    }
    publishedUserId = nextUserId
    notifySessionIdentityChanged(nextUserId)
  }

  function applyCredential(credential: AuthCredential): void {
    accessToken.value = credential.accessToken
    tokenType.value = credential.tokenType
    expiresAt.value = credential.expiresAt
    userId.value = credential.userId
    identityStatus.value = IdentityStatus.UNKNOWN
    publishIdentity(credential.userId)
  }

  function clearSession(): void {
    accessToken.value = null
    tokenType.value = 'Bearer'
    expiresAt.value = null
    userId.value = null
    me.value = null
    seedRole.value = null
    identityStatus.value = IdentityStatus.ANONYMOUS
    clearAuthCredential()
    publishedUserId = null
    notifySessionIdentityChanged(null)
  }

  async function refreshMe(): Promise<void> {
    try {
      me.value = await fetchCurrentUser()
      seedRole.value = me.value.role
      userId.value = me.value.userId
      identityStatus.value = IdentityStatus.RESOLVED
      publishIdentity(me.value.userId)
    } catch (error) {
      if (accessToken.value && !(isApiError(error) && error.kind === 'unauthenticated')) {
        identityStatus.value = IdentityStatus.UNAVAILABLE
      }
      throw error
    }
  }

  async function runInitialize(): Promise<void> {
    const credential = readAuthCredential()
    if (!credential) {
      identityStatus.value = IdentityStatus.ANONYMOUS
      ready.value = true
      return
    }
    applyCredential(credential)
    try {
      await refreshMe()
    } catch (error) {
      if (isApiError(error) && error.kind === 'unauthenticated') {
        clearSession()
      } else if (accessToken.value) {
        identityStatus.value = IdentityStatus.UNAVAILABLE
      }
    } finally {
      ready.value = true
    }
  }

  function initialize(): Promise<void> {
    if (ready.value) {
      return Promise.resolve()
    }
    if (!initializePromise) {
      initializePromise = runInitialize().finally(() => {
        initializePromise = null
      })
    }
    return initializePromise
  }

  async function login(request: LoginRequest): Promise<LoginOutcome> {
    const response = await loginRequest(request)
    const credential = toCredential(response)
    writeAuthCredential(credential)
    applyCredential(credential)
    seedRole.value = response.role
    resetUnauthorizedGate()
    try {
      await refreshMe()
      return {
        kind: LoginOutcomeKind.AUTHENTICATED,
        credentialAccepted: true,
        identityStatus: IdentityStatus.RESOLVED,
        response,
      }
    } catch (error) {
      if (isApiError(error) && error.kind === 'unauthenticated') {
        throw error
      }
      if (accessToken.value) {
        identityStatus.value = IdentityStatus.UNAVAILABLE
        return {
          kind: LoginOutcomeKind.AUTHENTICATED_IDENTITY_UNAVAILABLE,
          credentialAccepted: true,
          identityStatus: IdentityStatus.UNAVAILABLE,
          response,
        }
      }
      throw error
    }
  }

  function logout(): void {
    clearSession()
  }

  function onPasswordChanged(): void {
    clearSession()
  }

  function handleUnauthorized(): void {
    clearSession()
  }

  return {
    ready,
    accessToken,
    tokenType,
    expiresAt,
    userId,
    me,
    seedRole,
    identityStatus,
    isAuthenticated,
    isAdmin,
    initialize,
    login,
    logout,
    refreshMe,
    onPasswordChanged,
    handleUnauthorized,
  }
})
