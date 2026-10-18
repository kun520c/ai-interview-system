<script setup lang="ts">
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import AuthField from '@/features/auth/components/AuthField.vue'
import {
  AdminQuestionFormMode,
  AdminQuestionFormState,
  useAdminQuestionForm,
} from '@/features/admin/questions/composables/useAdminQuestionForm'
import { QUESTION_ADMIN_COPY } from '@/features/admin/questions/copy/questionAdminCopy'
import { displayDifficulty, displayPointType, displayQuestionCategory } from '@/features/admin/questions/copy/questionDisplay'
import { POINT_TYPE_OPTIONS, QUESTION_KNOWLEDGE_POINT_MAX } from '@/features/admin/questions/utils/questionFormRules'
import { QuestionCategory, QuestionDifficulty } from '@/types/enums'

const {
  mode,
  pageState,
  form,
  fieldErrors,
  submitting,
  mutationUncertain,
  formMessage,
  weightSum,
  weightValid,
  canRemovePoint,
  canSubmit,
  addPoint,
  removePoint,
  submit,
  retry,
} = useAdminQuestionForm()

const categories = Object.values(QuestionCategory)
const difficulties = Object.values(QuestionDifficulty)
</script>

<template>
  <section class="question-form-page">
    <template v-if="pageState === AdminQuestionFormState.INVALID_ID || pageState === AdminQuestionFormState.NOT_FOUND">
      <EmptyState :title="QUESTION_ADMIN_COPY.missing">
        <template #action>
          <RouterLink class="back-link" :to="{ name: 'admin-questions' }">
            {{ QUESTION_ADMIN_COPY.backToList }}
          </RouterLink>
        </template>
      </EmptyState>
    </template>

    <template v-else-if="pageState === AdminQuestionFormState.LOADING">
      <div class="form-skeleton" aria-busy="true" aria-label="正在加载题目">
        <span class="skeleton-block is-title"></span>
        <span class="skeleton-block"></span>
      </div>
    </template>

    <StatusNotice v-else-if="pageState === AdminQuestionFormState.ERROR" tone="danger" role="alert">
      <p>{{ QUESTION_ADMIN_COPY.loadError }}</p>
      <BaseButton variant="ghost" @click="retry()">{{ QUESTION_ADMIN_COPY.retry }}</BaseButton>
    </StatusNotice>

    <template v-else>
      <PageHeader
        :title="mode === AdminQuestionFormMode.EDIT ? QUESTION_ADMIN_COPY.editTitle : QUESTION_ADMIN_COPY.createTitle"
      >
        <template #actions>
          <RouterLink class="back-link" :to="{ name: 'admin-questions' }">
            {{ QUESTION_ADMIN_COPY.backToList }}
          </RouterLink>
        </template>
      </PageHeader>

      <StatusNotice v-if="formMessage" :tone="mutationUncertain ? 'warning' : 'danger'" role="alert">
        <p>{{ formMessage }}</p>
        <div v-if="mutationUncertain" class="uncertain-actions">
          <RouterLink class="back-link is-primary" :to="{ name: 'admin-questions' }">
            {{ QUESTION_ADMIN_COPY.backToListCta }}
          </RouterLink>
        </div>
      </StatusNotice>

      <form class="question-form" novalidate @submit.prevent="submit()">
        <AuthField :label="QUESTION_ADMIN_COPY.category" for-id="question-category">
          <select id="question-category" v-model="form.category" class="form-select" :disabled="submitting">
            <option v-for="category in categories" :key="category" :value="category">
              {{ displayQuestionCategory(category) }}
            </option>
          </select>
        </AuthField>

        <AuthField
          v-slot="{ invalid, describedBy }"
          :label="QUESTION_ADMIN_COPY.knowledgePoint"
          for-id="question-knowledge-point"
          :error="fieldErrors.knowledgePoint"
        >
          <BaseInput
            id="question-knowledge-point"
            v-model="form.knowledgePoint"
            :maxlength="QUESTION_KNOWLEDGE_POINT_MAX"
            :disabled="submitting"
            :aria-invalid="invalid ? 'true' : undefined"
            :aria-describedby="describedBy"
          />
        </AuthField>

        <AuthField :label="QUESTION_ADMIN_COPY.difficulty" for-id="question-difficulty">
          <select id="question-difficulty" v-model="form.difficulty" class="form-select" :disabled="submitting">
            <option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
              {{ displayDifficulty(difficulty) }}
            </option>
          </select>
        </AuthField>

        <AuthField
          v-slot="{ invalid, describedBy }"
          :label="QUESTION_ADMIN_COPY.content"
          for-id="question-content"
          :error="fieldErrors.questionContent"
        >
          <BaseTextarea
            id="question-content"
            v-model="form.questionContent"
            :disabled="submitting"
            :invalid="invalid"
            :described-by="describedBy"
            :rows="6"
          />
        </AuthField>

        <AuthField
          v-slot="{ invalid, describedBy }"
          :label="QUESTION_ADMIN_COPY.referenceAnswer"
          for-id="question-reference"
          :error="fieldErrors.referenceAnswer"
        >
          <BaseTextarea
            id="question-reference"
            v-model="form.referenceAnswer"
            :disabled="submitting"
            :invalid="invalid"
            :described-by="describedBy"
            :rows="6"
          />
        </AuthField>

        <section class="points-block" aria-labelledby="scoring-points-title">
          <div class="points-header">
            <h2 id="scoring-points-title">{{ QUESTION_ADMIN_COPY.scoringPoints }}</h2>
            <p :class="weightValid ? 'u-muted' : 'u-danger'" role="status">
              {{ QUESTION_ADMIN_COPY.weightTotal }}：{{ weightSum }} / 100
            </p>
          </div>
          <p class="u-muted">{{ QUESTION_ADMIN_COPY.weightHint }}</p>
          <p v-if="fieldErrors.scoringPoints" class="field-error" role="alert">{{ fieldErrors.scoringPoints }}</p>

          <article v-for="(point, index) in form.scoringPoints" :key="index" class="point-card">
            <div class="point-row">
              <label class="point-field">
                <span>类型</span>
                <select v-model="point.pointType" class="form-select" :disabled="submitting">
                  <option v-for="pointType in POINT_TYPE_OPTIONS" :key="pointType" :value="pointType">
                    {{ displayPointType(pointType) }}
                  </option>
                </select>
              </label>
              <label class="point-field is-weight">
                <span>{{ QUESTION_ADMIN_COPY.weight }}</span>
                <input
                  v-model.number="point.weight"
                  class="weight-input"
                  type="number"
                  min="1"
                  max="100"
                  :disabled="submitting"
                />
              </label>
            </div>
            <label class="point-field">
              <span>内容</span>
              <BaseTextarea v-model="point.content" :disabled="submitting" :rows="4" />
            </label>
            <BaseButton
              variant="ghost"
              :disabled="!canRemovePoint || submitting"
              @click="removePoint(index)"
            >
              {{ QUESTION_ADMIN_COPY.removePoint }}
            </BaseButton>
          </article>

          <BaseButton variant="ghost" :disabled="submitting" @click="addPoint()">
            {{ QUESTION_ADMIN_COPY.addPoint }}
          </BaseButton>
        </section>

        <div class="form-actions">
          <BaseButton type="submit" :loading="submitting" :disabled="!canSubmit">
            {{
              submitting
                ? QUESTION_ADMIN_COPY.submitting
                : mode === AdminQuestionFormMode.EDIT
                  ? QUESTION_ADMIN_COPY.saveEdit
                  : QUESTION_ADMIN_COPY.saveCreate
            }}
          </BaseButton>
          <BaseButton
            v-if="mutationUncertain"
            variant="ghost"
            :disabled="submitting"
            @click="submit()"
          >
            {{ QUESTION_ADMIN_COPY.submitAgain }}
          </BaseButton>
        </div>
      </form>
    </template>
  </section>
</template>

<style scoped>
.question-form-page {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 20px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  color: var(--text);
}

.back-link.is-primary {
  color: var(--primary);
}

.form-skeleton {
  display: grid;
  gap: 12px;
}

.skeleton-block {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-block.is-title {
  height: 24px;
  width: 36%;
}

.question-form {
  display: grid;
  gap: 16px;
  max-width: 760px;
}

.form-select, .weight-input, .question-form :deep(.base-input), .question-form :deep(.base-button) {
  min-height: 44px;
}

.form-select, .weight-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.uncertain-actions {
  margin-top: 12px;
}

.points-block {
  display: grid;
  gap: 12px;
}

.points-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
}

.points-header h2 {
  font-size: 16px;
  font-weight: 650;
}

.field-error {
  font-size: 13px;
  color: var(--danger);
}

.point-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.point-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: 12px;
}

.point-field {
  display: grid;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 720px) {
  .question-form {
    max-width: none;
  }

  .point-row {
    grid-template-columns: minmax(0, 1fr);
  }

}

.question-form {
  max-width: 960px;
  padding: 28px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  gap: 20px;
}

.point-card {
  background: var(--surface-subtle);
}

.form-actions {
  border-top: 1px solid var(--border);
  padding-top: 20px;
}

@media (max-width:720px) {
  .question-form {
    padding: 18px;
  }

}
</style>
