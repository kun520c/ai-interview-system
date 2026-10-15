import type { UserRole, UserStatus } from '@/types/enums'

export interface CurrentUserResponse {
  userId: number
  account: string
  username: string
  email: string
  role: UserRole
  status: UserStatus
  createdAt: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}
