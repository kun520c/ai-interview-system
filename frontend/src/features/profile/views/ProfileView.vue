<script setup lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import AuthField from '@/features/auth/components/AuthField.vue'
import PasswordField from '@/features/auth/components/PasswordField.vue'
import { PROFILE_COPY } from '@/features/profile/copy/profileCopy'
import { useProfile } from '@/features/profile/composables/useProfile'
import { formatInterviewDate } from '@/utils/formatInterviewDate'

const {
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
  passwordMax,
  retryIdentity,
  submitPassword,
  goToLogin,
} = useProfile()
</script>

<template>
  <section class="profile-page">
    <PageHeader :title="PROFILE_COPY.title" :description="PROFILE_COPY.description" />

    <StatusNotice v-if="identityUnavailable" tone="warning" role="alert">
      <p>{{ PROFILE_COPY.identityUnavailable }}</p>
      <BaseButton variant="ghost" :disabled="identityRetrying" @click="retryIdentity()">
        {{ PROFILE_COPY.retryIdentity }}
      </BaseButton>
    </StatusNotice>

    <section v-else-if="identityResolved && me" class="identity-block" aria-labelledby="profile-identity-title">
      <h2 id="profile-identity-title">{{ PROFILE_COPY.identityTitle }}</h2>
      <dl class="identity-list">
        <div>
          <dt>{{ PROFILE_COPY.account }}</dt>
          <dd>{{ me.account }}</dd>
        </div>
        <div>
          <dt>{{ PROFILE_COPY.username }}</dt>
          <dd>{{ me.username }}</dd>
        </div>
        <div>
          <dt>{{ PROFILE_COPY.email }}</dt>
          <dd>{{ me.email }}</dd>
        </div>
        <div>
          <dt>{{ PROFILE_COPY.role }}</dt>
          <dd>{{ roleLabel }}</dd>
        </div>
        <div>
          <dt>{{ PROFILE_COPY.status }}</dt>
          <dd>{{ statusLabel }}</dd>
        </div>
        <div>
          <dt>{{ PROFILE_COPY.createdAt }}</dt>
          <dd>{{ formatInterviewDate(me.createdAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="password-block" aria-labelledby="profile-password-title">
      <h2 id="profile-password-title">{{ PROFILE_COPY.passwordTitle }}</h2>

      <StatusNotice v-if="formError" :tone="mutationUncertain ? 'warning' : 'danger'" role="alert">
        <p>{{ formError }}</p>
        <div v-if="mutationUncertain" class="uncertain-actions">
          <BaseButton variant="ghost" @click="goToLogin()">{{ PROFILE_COPY.backToLogin }}</BaseButton>
        </div>
      </StatusNotice>

      <form class="password-form" novalidate @submit.prevent="submitPassword()">
        <AuthField
          v-slot="{ invalid, describedBy }"
          :label="PROFILE_COPY.currentPassword"
          for-id="profile-current-password"
          :error="fieldErrors.currentPassword"
        >
          <PasswordField
            id="profile-current-password"
            v-model="currentPassword"
            name="currentPassword"
            autocomplete="current-password"
            :maxlength="passwordMax"
            :disabled="submitting"
            :invalid="invalid"
            :described-by="describedBy"
            visibility-noun="当前密码"
          />
        </AuthField>
        <AuthField
          v-slot="{ invalid, describedBy }"
          :label="PROFILE_COPY.newPassword"
          for-id="profile-new-password"
          :error="fieldErrors.newPassword"
        >
          <PasswordField
            id="profile-new-password"
            v-model="newPassword"
            name="newPassword"
            autocomplete="new-password"
            :maxlength="passwordMax"
            :disabled="submitting"
            :invalid="invalid"
            :described-by="describedBy"
            visibility-noun="新密码"
          />
        </AuthField>
        <AuthField
          v-slot="{ invalid, describedBy }"
          :label="PROFILE_COPY.confirmNewPassword"
          for-id="profile-confirm-password"
          :error="fieldErrors.confirmNewPassword"
        >
          <PasswordField
            id="profile-confirm-password"
            v-model="confirmNewPassword"
            name="confirmNewPassword"
            autocomplete="new-password"
            :maxlength="passwordMax"
            :disabled="submitting"
            :invalid="invalid"
            :described-by="describedBy"
            visibility-noun="确认密码"
          />
        </AuthField>
        <BaseButton type="submit" :loading="submitting" :disabled="!canSubmit">
          {{ submitting ? PROFILE_COPY.submitting : PROFILE_COPY.submit }}
        </BaseButton>
      </form>
    </section>
  </section>
</template>

<style scoped>
.profile-page {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 32px;
}

.identity-block, .password-block {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.identity-block h2, .password-block h2 {
  font-size: 18px;
  font-weight: 650;
}

.identity-list {
  display: grid;
  gap: 12px 32px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.identity-list div {
  min-width: 0;
}

.identity-list dt {
  font-size: 12px;
  color: var(--text-2);
}

.identity-list dd {
  margin-top: 4px;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.password-form {
  display: grid;
  gap: 16px;
  max-width: 480px;
}

.password-form :deep(.base-input), .password-form :deep(.base-button) {
  min-height: 44px;
}

.uncertain-actions {
  margin-top: 12px;
}

@media (max-width: 720px) {
  .identity-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .password-form {
    max-width: none;
  }

}

.identity-block, .password-block {
  padding: 26px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.identity-list {
  border-top: 1px solid var(--border);
  padding-top: 16px;
}

.identity-list dd {
  margin-left: 0;
}

@media (max-width:720px) {
  .identity-block, .password-block {
    padding: 20px;
  }

}
</style>
