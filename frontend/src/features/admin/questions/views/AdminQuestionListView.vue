<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import HistoryPagination from '@/features/history/components/HistoryPagination.vue'
import { useAdminQuestionList } from '@/features/admin/questions/composables/useAdminQuestionList'
import { QUESTION_ADMIN_COPY } from '@/features/admin/questions/copy/questionAdminCopy'
import {
  displayDifficulty,
  displayQuestionCategory,
  displayQuestionStatus,
} from '@/features/admin/questions/copy/questionDisplay'
import { ADMIN_QUESTION_KEYWORD_MAX } from '@/features/admin/questions/utils/questionQuery'
import { CATEGORY_DISPLAY } from '@/features/interview/copy/categoryDisplay'
import { QuestionCategory, QuestionDifficulty, QuestionStatus } from '@/types/enums'
import { formatInterviewDate } from '@/utils/formatInterviewDate'

const { data, loading, error, parsed, keywordDraft, retry, applyKeyword, setCategory, setDifficulty, setStatus, goToPage } =
  useAdminQuestionList()

const records = computed(() => data.value?.records ?? [])
const page = computed(() => data.value?.pageNum ?? parsed.value.pageNum)
const totalPages = computed(() => data.value?.totalPages ?? 0)
const empty = computed(() => !loading.value && !error.value && records.value.length === 0)
const categories = Object.values(QuestionCategory)
const difficulties = Object.values(QuestionDifficulty)
const statuses = Object.values(QuestionStatus)
</script>

<template>
  <section class="question-list-page">
    <PageHeader :title="QUESTION_ADMIN_COPY.listTitle" :description="QUESTION_ADMIN_COPY.listDescription">
      <template #actions>
        <RouterLink class="create-link" :to="{ name: 'admin-question-create' }">
          {{ QUESTION_ADMIN_COPY.create }}
        </RouterLink>
      </template>
    </PageHeader>

    <form class="filter-bar" @submit.prevent="applyKeyword()">
      <label class="filter-field">
        <span>{{ QUESTION_ADMIN_COPY.category }}</span>
        <select
          :value="parsed.category ?? ''"
          @change="setCategory(($event.target as HTMLSelectElement).value)"
        >
          <option value="">{{ QUESTION_ADMIN_COPY.all }}</option>
          <option v-for="category in categories" :key="category" :value="category">
            {{ CATEGORY_DISPLAY[category] }}
          </option>
        </select>
      </label>
      <label class="filter-field">
        <span>{{ QUESTION_ADMIN_COPY.difficulty }}</span>
        <select
          :value="parsed.difficulty ?? ''"
          @change="setDifficulty(($event.target as HTMLSelectElement).value)"
        >
          <option value="">{{ QUESTION_ADMIN_COPY.all }}</option>
          <option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
            {{ displayDifficulty(difficulty) }}
          </option>
        </select>
      </label>
      <label class="filter-field">
        <span>{{ QUESTION_ADMIN_COPY.status }}</span>
        <select
          :value="parsed.status ?? ''"
          @change="setStatus(($event.target as HTMLSelectElement).value)"
        >
          <option value="">{{ QUESTION_ADMIN_COPY.all }}</option>
          <option v-for="status in statuses" :key="status" :value="status">
            {{ displayQuestionStatus(status) }}
          </option>
        </select>
      </label>
      <label class="filter-field is-keyword">
        <span>{{ QUESTION_ADMIN_COPY.keyword }}</span>
        <BaseInput
          v-model="keywordDraft"
          :maxlength="ADMIN_QUESTION_KEYWORD_MAX"
          :placeholder="QUESTION_ADMIN_COPY.keywordPlaceholder"
        />
      </label>
      <BaseButton type="submit">{{ QUESTION_ADMIN_COPY.applyFilters }}</BaseButton>
    </form>

    <div v-if="loading" class="list-skeleton" aria-busy="true" aria-label="正在加载题库">
      <span class="skeleton-block"></span>
      <span class="skeleton-block is-short"></span>
    </div>

    <StatusNotice v-else-if="error" tone="danger" role="alert">
      <p>{{ QUESTION_ADMIN_COPY.loadError }}</p>
      <BaseButton variant="ghost" @click="retry()">{{ QUESTION_ADMIN_COPY.retry }}</BaseButton>
    </StatusNotice>

    <EmptyState v-else-if="empty" :title="QUESTION_ADMIN_COPY.empty" :description="QUESTION_ADMIN_COPY.emptyHint">
      <template #action>
        <RouterLink class="create-link" :to="{ name: 'admin-question-create' }">
          {{ QUESTION_ADMIN_COPY.create }}
        </RouterLink>
      </template>
    </EmptyState>

    <template v-else>
      <div class="table-wrap">
        <table class="question-table">
          <thead>
            <tr>
              <th>{{ QUESTION_ADMIN_COPY.category }}</th>
              <th class="col-secondary">{{ QUESTION_ADMIN_COPY.knowledgePoint }}</th>
              <th>{{ QUESTION_ADMIN_COPY.difficulty }}</th>
              <th>{{ QUESTION_ADMIN_COPY.content }}</th>
              <th>{{ QUESTION_ADMIN_COPY.status }}</th>
              <th class="col-secondary">{{ QUESTION_ADMIN_COPY.updatedAt }}</th>
              <th>{{ QUESTION_ADMIN_COPY.actions }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td :data-label="QUESTION_ADMIN_COPY.category">{{ displayQuestionCategory(item.category) }}</td>
              <td class="col-secondary" :data-label="QUESTION_ADMIN_COPY.knowledgePoint">
                {{ item.knowledgePoint }}
              </td>
              <td :data-label="QUESTION_ADMIN_COPY.difficulty">{{ displayDifficulty(item.difficulty) }}</td>
              <td class="content-cell" :data-label="QUESTION_ADMIN_COPY.content">{{ item.questionContent }}</td>
              <td :data-label="QUESTION_ADMIN_COPY.status">{{ displayQuestionStatus(item.status) }}</td>
              <td class="col-secondary" :data-label="QUESTION_ADMIN_COPY.updatedAt">
                {{ formatInterviewDate(item.updatedAt) }}
              </td>
              <td class="actions-cell" :data-label="QUESTION_ADMIN_COPY.actions">
                <RouterLink :to="{ name: 'admin-question-detail', params: { questionId: String(item.id) } }">
                  {{ QUESTION_ADMIN_COPY.view }}
                </RouterLink>
                <RouterLink :to="{ name: 'admin-question-edit', params: { questionId: String(item.id) } }">
                  {{ QUESTION_ADMIN_COPY.edit }}
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <HistoryPagination
        :page="page"
        :total-pages="totalPages"
        :disabled="loading"
        aria-label="题库分页"
        @previous="goToPage(page - 1)"
        @next="goToPage(page + 1)"
      />
    </template>
  </section>
</template>

<style scoped>
.question-list-page {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 20px;
}

.create-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  color: var(--primary);
}

.filter-bar {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  gap: 12px;
  align-items: end;
}

.filter-field {
  display: grid;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
}

.filter-field select,
.filter-bar :deep(.base-input),
.filter-bar :deep(.base-button) {
  min-height: 44px;
  width: 100%;
}

.filter-field select {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.list-skeleton,
.table-wrap {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow: hidden;
}

.list-skeleton {
  display: grid;
  gap: 12px;
  padding: 16px 20px;
}

.skeleton-block {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-block.is-short {
  width: 40%;
}

.question-table {
  width: 100%;
  border-collapse: collapse;
}

.question-table th,
.question-table td {
  padding: 14px 16px;
  text-align: left;
  vertical-align: top;
  font-size: 14px;
}

.question-table th {
  font-size: 12px;
  color: var(--text-2);
  font-weight: 650;
  border-bottom: 1px solid var(--border);
}

.question-table td {
  border-top: 1px solid var(--border);
  overflow-wrap: anywhere;
}

.content-cell {
  max-width: 420px;
}

.actions-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.actions-cell a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  color: var(--primary);
}

@media (max-width: 1024px) {
  .filter-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .col-secondary {
    display: none;
  }
}

@media (max-width: 720px) {
  .filter-bar {
    grid-template-columns: minmax(0, 1fr);
  }

  .question-table thead {
    display: none;
  }

  .question-table tr {
    display: grid;
    gap: 8px;
    padding: 16px;
  }

  .question-table td {
    display: grid;
    gap: 4px;
    padding: 0;
    border: 0;
  }

  .question-table td::before {
    content: attr(data-label);
    font-size: 12px;
    color: var(--text-2);
    font-weight: 650;
  }

  .col-secondary {
    display: grid;
  }

  .content-cell {
    max-width: none;
  }
}
</style>
