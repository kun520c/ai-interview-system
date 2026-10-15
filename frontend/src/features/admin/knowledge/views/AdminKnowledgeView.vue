<script setup lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import AuthField from '@/features/auth/components/AuthField.vue'
import { useAdminKnowledge } from '@/features/admin/knowledge/composables/useAdminKnowledge'
import { KNOWLEDGE_ADMIN_COPY } from '@/features/admin/knowledge/copy/knowledgeAdminCopy'
import { displayQuestionCategory } from '@/features/admin/questions/copy/questionDisplay'
import { CATEGORY_DISPLAY } from '@/features/interview/copy/categoryDisplay'
import { KnowledgeProcessingStatus, QuestionCategory } from '@/types/enums'
import { formatInterviewDate } from '@/utils/formatInterviewDate'
import type { LocalKnowledgeDocument } from '@/types/knowledge'

const {
  title,
  category,
  source,
  file,
  uploading,
  processingId,
  formError,
  formNoticeTone,
  lastUploadedId,
  documents,
  canUpload,
  titleMax,
  sourceMax,
  setFile,
  upload,
  processDocument,
} = useAdminKnowledge()

const categories = Object.values(QuestionCategory)

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  setFile(input.files?.[0] ?? null)
}

function statusLabel(document: LocalKnowledgeDocument): string {
  if (document.recoveryState === 'UNCONFIRMED') {
    return KNOWLEDGE_ADMIN_COPY.processUncertain
  }
  if (document.recoveryState === 'UNAVAILABLE') {
    return KNOWLEDGE_ADMIN_COPY.notFound
  }
  if (document.processingStatus === KnowledgeProcessingStatus.READY) {
    return KNOWLEDGE_ADMIN_COPY.ready
  }
  if (document.processingStatus === KnowledgeProcessingStatus.PROCESSING) {
    return KNOWLEDGE_ADMIN_COPY.processingStatus
  }
  if (document.processingStatus === KnowledgeProcessingStatus.FAILED) {
    return KNOWLEDGE_ADMIN_COPY.failedStatus
  }
  return KNOWLEDGE_ADMIN_COPY.uploadedStatus
}

function canProcess(document: LocalKnowledgeDocument): boolean {
  if (document.recoveryState === 'UNAVAILABLE') {
    return false
  }
  if (document.processingStatus === KnowledgeProcessingStatus.READY) {
    return false
  }
  return true
}

function processLabel(document: LocalKnowledgeDocument): string {
  if (processingId.value === document.documentId) {
    return KNOWLEDGE_ADMIN_COPY.processing
  }
  if (document.recoveryState === 'UNCONFIRMED') {
    return KNOWLEDGE_ADMIN_COPY.retryProcess
  }
  if (document.processingStatus === KnowledgeProcessingStatus.UPLOADED) {
    return KNOWLEDGE_ADMIN_COPY.startProcess
  }
  return KNOWLEDGE_ADMIN_COPY.retryProcess
}
</script>

<template>
  <section class="knowledge-page">
    <PageHeader :title="KNOWLEDGE_ADMIN_COPY.title" :description="KNOWLEDGE_ADMIN_COPY.description" />

    <section class="upload-block" aria-labelledby="knowledge-upload-title">
      <h2 id="knowledge-upload-title">{{ KNOWLEDGE_ADMIN_COPY.uploadTitle }}</h2>
      <StatusNotice v-if="formError" :tone="formNoticeTone" role="alert">
        <p>{{ formError }}</p>
      </StatusNotice>
      <StatusNotice
        v-if="lastUploadedId && !formError"
        tone="success"
        role="status"
      >
        <p>{{ KNOWLEDGE_ADMIN_COPY.uploaded }}</p>
      </StatusNotice>
      <form class="upload-form" novalidate @submit.prevent="upload()">
        <AuthField :label="KNOWLEDGE_ADMIN_COPY.file" for-id="knowledge-file">
          <input
            id="knowledge-file"
            class="file-input"
            type="file"
            accept=".md,.markdown,.txt,text/markdown,text/plain"
            :disabled="!canUpload"
            @change="onFileChange"
          />
          <p class="u-muted">{{ KNOWLEDGE_ADMIN_COPY.fileHint }}</p>
          <p v-if="file" class="file-name">{{ file.name }}</p>
        </AuthField>
        <AuthField :label="KNOWLEDGE_ADMIN_COPY.titleLabel" for-id="knowledge-title">
          <BaseInput id="knowledge-title" v-model="title" :maxlength="titleMax" :disabled="!canUpload" />
        </AuthField>
        <AuthField :label="KNOWLEDGE_ADMIN_COPY.category" for-id="knowledge-category">
          <select id="knowledge-category" v-model="category" class="form-select" :disabled="!canUpload">
            <option v-for="item in categories" :key="item" :value="item">
              {{ CATEGORY_DISPLAY[item] }}
            </option>
          </select>
        </AuthField>
        <AuthField :label="KNOWLEDGE_ADMIN_COPY.source" for-id="knowledge-source">
          <BaseInput id="knowledge-source" v-model="source" :maxlength="sourceMax" :disabled="!canUpload" />
        </AuthField>
        <BaseButton type="submit" :loading="uploading" :disabled="!canUpload">
          {{ uploading ? KNOWLEDGE_ADMIN_COPY.uploading : KNOWLEDGE_ADMIN_COPY.upload }}
        </BaseButton>
      </form>
    </section>

    <section class="recent-block" aria-labelledby="knowledge-recent-title">
      <h2 id="knowledge-recent-title">{{ KNOWLEDGE_ADMIN_COPY.recentTitle }}</h2>
      <p class="u-muted">{{ KNOWLEDGE_ADMIN_COPY.recentHint }}</p>
      <p v-if="documents.length === 0" class="u-muted">{{ KNOWLEDGE_ADMIN_COPY.emptyRecent }}</p>
      <ul v-else class="recent-list">
        <li v-for="document in documents" :key="document.documentId" class="recent-item">
          <div class="recent-copy">
            <p class="recent-title">{{ document.title }}</p>
            <p class="recent-meta">
              {{ displayQuestionCategory(document.category) }} · {{ document.fileName }} ·
              {{ formatInterviewDate(document.updatedAt) }}
            </p>
            <p class="recent-status" role="status">{{ statusLabel(document) }}</p>
          </div>
          <BaseButton
            v-if="canProcess(document)"
            :disabled="processingId !== null"
            :loading="processingId === document.documentId"
            @click="processDocument(document.documentId)"
          >
            {{ processLabel(document) }}
          </BaseButton>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.knowledge-page {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 32px;
}

.upload-block,
.recent-block {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.upload-block h2,
.recent-block h2 {
  font-size: 18px;
  font-weight: 650;
}

.upload-form {
  display: grid;
  gap: 16px;
  max-width: 560px;
}

.file-input,
.form-select,
.upload-form :deep(.base-input),
.upload-form :deep(.base-button),
.recent-item :deep(.base-button) {
  min-height: 44px;
}

.file-input {
  width: 100%;
  padding: 8px 0;
}

.form-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}

.file-name {
  font-size: 13px;
  overflow-wrap: anywhere;
}

.recent-list {
  display: grid;
  gap: 12px;
}

.recent-item {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 0;
  border-top: 1px solid var(--border);
}

.recent-title {
  font-weight: 650;
  overflow-wrap: anywhere;
}

.recent-meta,
.recent-status {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-2);
}

@media (max-width: 720px) {
  .upload-form {
    max-width: none;
  }

  .recent-item {
    display: grid;
  }
}
</style>
