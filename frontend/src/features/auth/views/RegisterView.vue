<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { isApiError, ErrorKind } from '@/api/apiError'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import { registerAccount } from '@/features/auth/api/authApi'
import AuthField from '@/features/auth/components/AuthField.vue'
import PasswordField from '@/features/auth/components/PasswordField.vue'
import {
  AUTH_LIMITS,
  validateAccount,
  validateConfirmPassword,
  validateEmail,
  validatePassword,
  validateUsername,
} from '@/features/auth/validation/authFormRules'
import type { RegisterRequest } from '@/types/auth'

const router = useRouter()

const account = ref('')
const username = ref('')
const password = ref('')
/** LOCAL_FORM_STATE only. Never included in RegisterRequest. */
const confirmPassword = ref('')
const email = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)
const fieldErrors = ref<{
  account?: string
  username?: string
  password?: string
  confirmPassword?: string
  email?: string
}>({})
const formErrorRef = ref<HTMLElement | null>(null)

function validateForm(): boolean {
  const nextErrors: typeof fieldErrors.value = {}
  const accountError = validateAccount(account.value)
  const usernameError = validateUsername(username.value)
  const passwordError = validatePassword(password.value)
  const confirmError = validateConfirmPassword(password.value, confirmPassword.value)
  const emailError = validateEmail(email.value)
  if (accountError) nextErrors.account = accountError
  if (usernameError) nextErrors.username = usernameError
  if (passwordError) nextErrors.password = passwordError
  if (confirmError) nextErrors.confirmPassword = confirmError
  if (emailError) nextErrors.email = emailError
  fieldErrors.value = nextErrors
  return Object.keys(nextErrors).length === 0
}

function registerFailureMessage(error: unknown): string {
  if (isApiError(error) && error.kind === ErrorKind.VALIDATION) {
    return error.message
  }
  return '注册请求暂时失败'
}

async function onSubmit(): Promise<void> {
  if (submitting.value) {
    return
  }
  formError.value = null
  if (!validateForm()) {
    return
  }
  submitting.value = true
  const request: RegisterRequest = {
    account: account.value.trim(),
    username: username.value.trim(),
    password: password.value,
    email: email.value.trim(),
  }
  try {
    await registerAccount(request)
    await router.push({
      name: 'login',
      query: { account: request.account },
    })
  } catch (error) {
    formError.value = registerFailureMessage(error)
    await nextTick()
    formErrorRef.value?.focus()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-panel">
    <header class="auth-header">
      <h1>注册</h1>
      <p class="u-muted">创建账号后需要登录才能进入工作台</p>
    </header>
    <div
      v-if="formError"
      ref="formErrorRef"
      class="auth-form-error"
      role="alert"
      tabindex="-1"
    >
      <StatusNotice tone="danger">{{ formError }}</StatusNotice>
    </div>
    <form class="auth-form" novalidate @submit.prevent="onSubmit">
      <AuthField
        v-slot="{ invalid, describedBy }"
        label="账号"
        for-id="register-account"
        :error="fieldErrors.account"
      >
        <BaseInput
          id="register-account"
          v-model="account"
          name="account"
          autocomplete="username"
          :maxlength="AUTH_LIMITS.accountMax"
          :disabled="submitting"
          :aria-invalid="invalid ? 'true' : undefined"
          :aria-describedby="describedBy"
        />
      </AuthField>
      <AuthField
        v-slot="{ invalid, describedBy }"
        label="用户名"
        for-id="register-username"
        :error="fieldErrors.username"
      >
        <BaseInput
          id="register-username"
          v-model="username"
          name="username"
          autocomplete="nickname"
          :maxlength="AUTH_LIMITS.usernameMax"
          :disabled="submitting"
          :aria-invalid="invalid ? 'true' : undefined"
          :aria-describedby="describedBy"
        />
      </AuthField>
      <AuthField
        v-slot="{ invalid, describedBy }"
        label="密码"
        for-id="register-password"
        :error="fieldErrors.password"
      >
        <PasswordField
          id="register-password"
          v-model="password"
          name="password"
          autocomplete="new-password"
          :maxlength="AUTH_LIMITS.passwordMax"
          :disabled="submitting"
          :invalid="invalid"
          :described-by="describedBy"
        />
      </AuthField>
      <AuthField
        v-slot="{ invalid, describedBy }"
        label="确认密码"
        for-id="register-confirm-password"
        :error="fieldErrors.confirmPassword"
      >
        <PasswordField
          id="register-confirm-password"
          v-model="confirmPassword"
          name="confirmPassword"
          autocomplete="new-password"
          :maxlength="AUTH_LIMITS.passwordMax"
          :disabled="submitting"
          :invalid="invalid"
          :described-by="describedBy"
          visibility-noun="确认密码"
        />
      </AuthField>
      <AuthField
        v-slot="{ invalid, describedBy }"
        label="邮箱"
        for-id="register-email"
        :error="fieldErrors.email"
      >
        <BaseInput
          id="register-email"
          v-model="email"
          type="email"
          name="email"
          autocomplete="email"
          :maxlength="AUTH_LIMITS.emailMax"
          :disabled="submitting"
          :aria-invalid="invalid ? 'true' : undefined"
          :aria-describedby="describedBy"
        />
      </AuthField>
      <BaseButton type="submit" :loading="submitting" :disabled="submitting">
        注册
      </BaseButton>
    </form>
    <p class="auth-switch">
      已有账号？
      <RouterLink :to="{ name: 'login' }">去登录</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.auth-panel {
  display: grid;
  gap: 24px;
}

.auth-header h1 {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
}

.auth-header p {
  margin-top: 8px;
  font-size: 14px;
}

.auth-form {
  display: grid;
  gap: 16px;
}

.auth-form :deep(.base-input),
.auth-form :deep(.base-button) {
  min-height: 44px;
}

.auth-form :deep(.base-button) {
  width: 100%;
}

.auth-form-error:focus {
  outline: none;
}

.auth-switch {
  font-size: 14px;
  color: var(--text-2);
}

.auth-switch a {
  color: var(--primary);
  font-weight: 600;
}

.auth-switch a:hover {
  color: var(--primary-hover);
}
</style>
