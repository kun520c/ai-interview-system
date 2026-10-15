<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError, ErrorKind } from '@/api/apiError'
import { useAuthStore } from '@/app/stores/auth'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import AuthField from '@/features/auth/components/AuthField.vue'
import PasswordField from '@/features/auth/components/PasswordField.vue'
import { AUTH_LIMITS, validateAccount, validatePassword } from '@/features/auth/validation/authFormRules'
import { resolveSafeRedirect } from '@/utils/safeRedirect'
import {
  consumePasswordChangedNotice,
  PASSWORD_CHANGED_LOGIN_NOTICE,
} from '@/features/auth/navigation/passwordChangedNotice'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const account = ref('')
const password = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)
const loginNotice = ref<string | null>(null)
const fieldErrors = ref<{ account?: string; password?: string }>({})
const formErrorRef = ref<HTMLElement | null>(null)

watch(
  () => route.query.account,
  (value) => {
    if (typeof value === 'string' && value.length > 0 && !account.value) {
      account.value = value.slice(0, AUTH_LIMITS.accountMax)
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (consumePasswordChangedNotice(router.options.history.state)) {
    loginNotice.value = PASSWORD_CHANGED_LOGIN_NOTICE
  }
})

function validateForm(): boolean {
  const nextErrors: { account?: string; password?: string } = {}
  const accountError = validateAccount(account.value)
  const passwordError = validatePassword(password.value)
  if (accountError) {
    nextErrors.account = accountError
  }
  if (passwordError) {
    nextErrors.password = passwordError
  }
  fieldErrors.value = nextErrors
  return !accountError && !passwordError
}

function loginFailureMessage(error: unknown): string {
  if (isApiError(error) && error.kind === ErrorKind.VALIDATION) {
    return error.message
  }
  if (isApiError(error) && error.kind === ErrorKind.UNAUTHENTICATED) {
    return '登录状态无效，请重新登录'
  }
  return '登录请求暂时失败'
}

async function focusFormError(): Promise<void> {
  await nextTick()
  formErrorRef.value?.focus()
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
  try {
    await auth.login({
      account: account.value.trim(),
      password: password.value,
    })
    await router.replace(resolveSafeRedirect(route.query.redirect))
  } catch (error) {
    formError.value = loginFailureMessage(error)
    await focusFormError()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-panel">
    <header class="auth-header">
      <h1>登录</h1>
      <p class="u-muted">使用账号进入面试工作台</p>
    </header>
    <div
      v-if="loginNotice"
      class="auth-form-error"
      role="status"
    >
      <StatusNotice tone="info">{{ loginNotice }}</StatusNotice>
    </div>
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
        for-id="login-account"
        :error="fieldErrors.account"
      >
        <BaseInput
          id="login-account"
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
        label="密码"
        for-id="login-password"
        :error="fieldErrors.password"
      >
        <PasswordField
          id="login-password"
          v-model="password"
          name="password"
          autocomplete="current-password"
          :maxlength="AUTH_LIMITS.passwordMax"
          :disabled="submitting"
          :invalid="invalid"
          :described-by="describedBy"
        />
      </AuthField>
      <BaseButton type="submit" :loading="submitting" :disabled="submitting">
        登录
      </BaseButton>
    </form>
    <p class="auth-switch">
      还没有账号？
      <RouterLink :to="{ name: 'register' }">去注册</RouterLink>
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
