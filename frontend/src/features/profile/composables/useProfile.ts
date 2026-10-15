import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ErrorKind, isApiError } from '@/api/apiError'
import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import { changePassword } from '@/features/auth/api/userApi'
import {
  PASSWORD_CHANGED_LOGIN_NOTICE,
  armPasswordChangedNotice,
  passwordChangedNoticeState,
} from '@/features/auth/navigation/passwordChangedNotice'
import { AUTH_LIMITS, validateConfirmPassword, validateCurrentPasswordForChange, validatePassword } from '@/features/auth/validation/authFormRules'
import { PROFILE_COPY } from '@/features/profile/copy/profileCopy'
import { isUncertainMutationFailure } from '@/features/interview/utils/uncertainMutation'
import { IdentityStatus } from '@/types/auth'
import { UserRole, UserStatus } from '@/types/enums'

export function useProfile() {
  const auth = useAuthStore()
  const router = useRouter()
  const currentPassword = ref('')
  const newPassword = ref('')
  const confirmNewPassword = ref('')
  const submitting = ref(false)
  const identityRetrying = ref(false)
  const formError = ref<string | null>(null)
  const fieldErrors = ref<{
    currentPassword?: string
    newPassword?: string
    confirmNewPassword?: string
  }>({})
  const mutationUncertain = ref(false)

  const identityResolved = computed(() => auth.identityStatus === IdentityStatus.RESOLVED && auth.me !== null)
  const identityUnavailable = computed(() => auth.identityStatus === IdentityStatus.UNAVAILABLE)
  const me = computed(() => auth.me)

  const roleLabel = computed(() => {
    if (!auth.me) {
      return ''
    }
    return auth.me.role === UserRole.ADMIN ? PROFILE_COPY.roleAdmin : PROFILE_COPY.roleUser
  })

  const statusLabel = computed(() => {
    if (!auth.me) {
      return ''
    }
    return auth.me.status === UserStatus.DISABLED ? PROFILE_COPY.statusDisabled : PROFILE_COPY.statusEnabled
  })

  const canSubmit = computed(() => !submitting.value)

  function validateForm(): boolean {
    const next: typeof fieldErrors.value = {}
    const currentError = validateCurrentPasswordForChange(currentPassword.value)
    if (currentError) {
      next.currentPassword = currentError
    }
    const newError = validatePassword(newPassword.value)
    if (newError) {
      next.newPassword = newError === '请输入密码' ? '请输入新密码' : newError
    }
    const confirmError = validateConfirmPassword(newPassword.value, confirmNewPassword.value)
    if (confirmError) {
      next.confirmNewPassword = confirmError
    }
    fieldErrors.value = next
    return Object.keys(next).length === 0
  }

  async function retryIdentity(): Promise<void> {
    if (identityRetrying.value) {
      return
    }
    identityRetrying.value = true
    try {
      await auth.refreshMe()
    } catch {
      // refreshMe keeps UNAVAILABLE; 401 is handled centrally.
    } finally {
      identityRetrying.value = false
    }
  }

  async function submitPassword(): Promise<void> {
    if (submitting.value) {
      return
    }
    formError.value = null
    mutationUncertain.value = false
    if (!validateForm()) {
      return
    }
    submitting.value = true
    const operationUserId = auth.userId
    const operationGeneration = getSessionGeneration()
    try {
      await changePassword({
        currentPassword: currentPassword.value,
        newPassword: newPassword.value,
      })
      if (!isCurrentIdentityScope(operationUserId, operationGeneration, auth.userId)) {
        return
      }
      armPasswordChangedNotice()
      auth.onPasswordChanged()
      await router.replace({
        name: 'login',
        state: passwordChangedNoticeState(),
      })
    } catch (caught) {
      if (!isCurrentIdentityScope(operationUserId, operationGeneration, auth.userId)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        throw caught
      }
      if (isApiError(caught) && caught.kind === ErrorKind.VALIDATION) {
        formError.value = caught.message
        return
      }
      if (isUncertainMutationFailure(caught)) {
        mutationUncertain.value = true
        formError.value = PROFILE_COPY.mutationUncertain
        return
      }
      if (isApiError(caught)) {
        formError.value = caught.message
        return
      }
      mutationUncertain.value = true
      formError.value = PROFILE_COPY.mutationUncertain
    } finally {
      submitting.value = false
    }
  }

  async function goToLogin(): Promise<void> {
    auth.logout()
    await router.replace({ name: 'login' })
  }

  return {
    auth,
    me,
    identityResolved,
    identityUnavailable,
    roleLabel,
    statusLabel,
    currentPassword,
    newPassword,
    confirmNewPassword,
    submitting,
    identityRetrying,
    formError,
    fieldErrors,
    mutationUncertain,
    canSubmit,
    passwordMax: AUTH_LIMITS.passwordMax,
    passwordChangedNotice: PASSWORD_CHANGED_LOGIN_NOTICE,
    retryIdentity,
    submitPassword,
    goToLogin,
  }
}
