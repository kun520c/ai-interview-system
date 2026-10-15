import { apiPost } from '@/api/httpClient'
import { endpoints } from '@/api/endpoints'
import type { LoginRequest, LoginResponse, RegisterRequest } from '@/types/auth'

export function registerAccount(request: RegisterRequest): Promise<null> {
  return apiPost<null>(endpoints.register, request)
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiPost<LoginResponse>(endpoints.login, request)
}
