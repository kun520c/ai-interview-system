<script setup lang="ts">
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import {
  AdminQuestionDetailState,
  useAdminQuestionDetail,
} from '@/features/admin/questions/composables/useAdminQuestionDetail'
import { QUESTION_ADMIN_COPY } from '@/features/admin/questions/copy/questionAdminCopy'
import {
  displayDifficulty,
  displayPointType,
  displayQuestionCategory,
  displayQuestionStatus,
} from '@/features/admin/questions/copy/questionDisplay'
import { QuestionStatus } from '@/types/enums'
import { formatInterviewDate } from '@/utils/formatInterviewDate'

const { pageState, detail, statusBusy, statusMessage, statusUncertain, retry, updateStatus } =
  useAdminQuestionDetail()
</script>

<template>
  <section class="question-detail-page">
    <template v-if="pageState === AdminQuestionDetailState.INVALID_ID || pageState === AdminQuestionDetailState.NOT_FOUND">
      <EmptyState :title="QUESTION_ADMIN_COPY.missing">
        <template #action>
          <RouterLink class="back-link" :to="{ name: 'admin-questions' }">
            {{ QUESTION_ADMIN_COPY.backToList }}
          </RouterLink>
        </template>
      </EmptyState>
    </template>

    <template v-else-if="pageState === AdminQuestionDetailState.LOADING">
      <div class="detail-skeleton" aria-busy="true" aria-label="正在加载题目">
        <span class="skeleton-block is-title"></span>
        <span class="skeleton-block"></span>
      </div>
    </template>

    <StatusNotice v-else-if="pageState === AdminQuestionDetailState.ERROR" tone="danger" role="alert">
      <p>{{ QUESTION_ADMIN_COPY.loadError }}</p>
      <BaseButton variant="ghost" @click="retry()">{{ QUESTION_ADMIN_COPY.retry }}</BaseButton>
    </StatusNotice>

    <template v-else-if="detail">
      <PageHeader :title="QUESTION_ADMIN_COPY.detailTitle" :description="displayQuestionCategory(detail.category)">
        <template #actions>
          <div class="detail-nav">
            <RouterLink class="back-link" :to="{ name: 'admin-questions' }">
              {{ QUESTION_ADMIN_COPY.backToList }}
            </RouterLink>
            <RouterLink
              class="back-link is-primary"
              :to="{ name: 'admin-question-edit', params: { questionId: String(detail.id) } }"
            >
              {{ QUESTION_ADMIN_COPY.edit }}
            </RouterLink>
          </div>
        </template>
      </PageHeader>

      <StatusNotice v-if="statusMessage" :tone="statusUncertain ? 'warning' : 'danger'" role="alert">
        <p>{{ statusMessage }}</p>
      </StatusNotice>

      <dl class="meta-list">
        <div>
          <dt>{{ QUESTION_ADMIN_COPY.knowledgePoint }}</dt>
          <dd>{{ detail.knowledgePoint }}</dd>
        </div>
        <div>
          <dt>{{ QUESTION_ADMIN_COPY.difficulty }}</dt>
          <dd>{{ displayDifficulty(detail.difficulty) }}</dd>
        </div>
        <div>
          <dt>{{ QUESTION_ADMIN_COPY.status }}</dt>
          <dd>{{ displayQuestionStatus(detail.status) }}</dd>
        </div>
        <div>
          <dt>{{ QUESTION_ADMIN_COPY.createdAt }}</dt>
          <dd>{{ formatInterviewDate(detail.createdAt) }}</dd>
        </div>
        <div>
          <dt>{{ QUESTION_ADMIN_COPY.updatedAt }}</dt>
          <dd>{{ formatInterviewDate(detail.updatedAt) }}</dd>
        </div>
      </dl>

      <section class="detail-block">
        <h2>{{ QUESTION_ADMIN_COPY.content }}</h2>
        <p>{{ detail.content }}</p>
      </section>

      <section class="detail-block">
        <h2>{{ QUESTION_ADMIN_COPY.referenceAnswer }}</h2>
        <pre>{{ detail.referenceAnswer }}</pre>
      </section>

      <section class="detail-block">
        <h2>{{ QUESTION_ADMIN_COPY.scoringPoints }}</h2>
        <ol class="point-list">
          <li v-for="(point, index) in detail.scoringPoints" :key="index">
            <p class="point-meta">
              {{ displayPointType(point.pointType) }} · {{ QUESTION_ADMIN_COPY.weight }} {{ point.weight }}
            </p>
            <p>{{ point.content }}</p>
          </li>
        </ol>
      </section>

      <div class="status-actions">
        <BaseButton
          v-if="detail.status === QuestionStatus.ENABLED"
          variant="ghost"
          :disabled="statusBusy"
          :loading="statusBusy"
          @click="updateStatus(QuestionStatus.DISABLED)"
        >
          {{ QUESTION_ADMIN_COPY.disable }}
        </BaseButton>
        <BaseButton
          v-else
          :disabled="statusBusy"
          :loading="statusBusy"
          @click="updateStatus(QuestionStatus.ENABLED)"
        >
          {{ QUESTION_ADMIN_COPY.enable }}
        </BaseButton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.question-detail-page {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 24px;
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

.detail-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
}

.detail-skeleton {
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
  width: 40%;
}

.meta-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.meta-list dt {
  font-size: 12px;
  color: var(--text-2);
}

.meta-list dd {
  margin-top: 4px;
  overflow-wrap: anywhere;
}

.detail-block h2 {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 650;
}

.detail-block p,
.detail-block pre,
.point-list p {
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.detail-block pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: var(--font);
}

.point-list {
  display: grid;
  gap: 16px;
  padding-left: 20px;
}

.point-meta {
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--text-2);
}

.status-actions :deep(.base-button) {
  min-height: 44px;
}

@media (max-width: 1024px) {
  .meta-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .meta-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
