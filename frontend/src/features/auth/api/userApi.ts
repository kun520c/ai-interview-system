import { apiGet, apiPut } from '@/api/httpClient'
import { endpoints } from '@/api/endpoints'
import type { ChangePasswordRequest, CurrentUserResponse } from '@/types/user'

export function fetchCurrentUser(): Promise<CurrentUserResponse> {
  return apiGet<CurrentUserResponse>(endpoints.me)
}

export function changePassword(request: ChangePasswordRequest): Promise<null> {
  return apiPut<null>(endpoints.changePassword, request)
}
