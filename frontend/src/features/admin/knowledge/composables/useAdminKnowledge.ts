import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ErrorKind, isApiError } from '@/api/apiError'
import {
  getSessionGeneration,
  isCurrentIdentityScope,
  subscribeSessionScope,
} from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import {
  processKnowledgeDocument,
  uploadKnowledgeDocument,
} from '@/features/admin/knowledge/api/knowledgeAdminApi'
import {
  KNOWLEDGE_ADMIN_COPY,
  KNOWLEDGE_NOT_FOUND,
  KNOWLEDGE_OVERSIZE,
  KNOWLEDGE_PROCESS_BUSY,
  KNOWLEDGE_PROCESS_FORBIDDEN_STATE,
  KNOWLEDGE_PROCESS_READY,
  KNOWLEDGE_SERVICE_UNAVAILABLE,
  KNOWLEDGE_SOURCE_MAX,
  KNOWLEDGE_TITLE_MAX,
} from '@/features/admin/knowledge/copy/knowledgeAdminCopy'
import {
  KNOWLEDGE_RECENT_MAX,
  KnowledgeRecentStorageError,
  probeKnowledgeRecentWritable,
  readKnowledgeRecent,
  upsertKnowledgeRecent,
  type KnowledgeRecentWriteResult,
} from '@/features/admin/knowledge/storage/knowledgeRecentStorage'
import { validateKnowledgeFile } from '@/features/admin/knowledge/utils/knowledgeFileRules'
import { isUncertainMutationFailure } from '@/features/interview/utils/uncertainMutation'
import { IdentityStatus } from '@/types/auth'
import { KnowledgeProcessingStatus, QuestionCategory } from '@/types/enums'
import type { LocalKnowledgeDocument } from '@/types/knowledge'

export function useAdminKnowledge() {
  const auth = useAuthStore()
  const router = useRouter()
  const title = ref('')
  const category = ref<QuestionCategory>(QuestionCategory.JAVA_BASIC)
  const source = ref('')
  const file = ref<File | null>(null)
  const fileInputKey = ref(0)
  const uploading = ref(false)
  const processingId = ref<number | null>(null)
  const formError = ref<string | null>(null)
  const uploadUncertain = ref(false)
  const uploadPersistFailed = ref(false)
  const lastUploadedId = ref<number | null>(null)
  const documents = ref<LocalKnowledgeDocument[]>([])
  let mounted = true

  const userId = computed(() => auth.userId)
  const canUpload = computed(() => !uploading.value && !uploadPersistFailed.value)
  const formNoticeTone = computed(() =>
    uploadUncertain.value || uploadPersistFailed.value || formError.value === KNOWLEDGE_ADMIN_COPY.processPersistFailed
      ? 'warning'
      : 'danger',
  )

  function currentIdentityMatches(operationUserId: number | null, operationGeneration: number): boolean {
    return mounted && isCurrentIdentityScope(operationUserId, operationGeneration, auth.userId)
  }

  function reloadRecent(): void {
    if (auth.identityStatus !== IdentityStatus.RESOLVED || userId.value === null) {
      documents.value = []
      return
    }
    documents.value = readKnowledgeRecent(userId.value)
  }

  function persistCaptured(
    operationUserId: number,
    document: LocalKnowledgeDocument,
  ): KnowledgeRecentWriteResult {
    try {
      return upsertKnowledgeRecent(operationUserId, document)
    } catch (caught) {
      const existing = readKnowledgeRecent(operationUserId).filter(
        (item) => item.documentId !== document.documentId,
      )
      return {
        ok: false,
        error:
          caught instanceof KnowledgeRecentStorageError
            ? caught
            : new KnowledgeRecentStorageError('unknown', '本机恢复记录保存失败', { cause: caught }),
        documents: [document, ...existing].slice(0, KNOWLEDGE_RECENT_MAX),
      }
    }
  }

  function adoptCapturedDocument(
    operationUserId: number,
    operationGeneration: number,
    document: LocalKnowledgeDocument,
  ): KnowledgeRecentWriteResult {
    const persistResult = persistCaptured(operationUserId, document)
    if (!currentIdentityMatches(operationUserId, operationGeneration)) {
      return persistResult
    }
    documents.value = persistResult.documents.length > 0 ? persistResult.documents : [document]
    return persistResult
  }

  function resetIdentityScopedUi(): void {
    formError.value = null
    uploadUncertain.value = false
    uploadPersistFailed.value = false
    lastUploadedId.value = null
    uploading.value = false
    processingId.value = null
    reloadRecent()
  }

  function setFile(next: File | null): void {
    file.value = next
    formError.value = null
    uploadUncertain.value = false
  }

  async function upload(): Promise<void> {
    if (uploading.value || userId.value === null || uploadPersistFailed.value) {
      return
    }
    formError.value = null
    uploadUncertain.value = false
    const trimmedTitle = title.value.trim()
    if (!trimmedTitle) {
      formError.value = '文档标题不能为空'
      return
    }
    if (trimmedTitle.length > KNOWLEDGE_TITLE_MAX) {
      formError.value = '文档标题长度不能超过200个字符'
      return
    }
    const trimmedSource = source.value.trim()
    if (trimmedSource.length > KNOWLEDGE_SOURCE_MAX) {
      formError.value = '文档来源长度不能超过255个字符'
      return
    }
    const fileError = validateKnowledgeFile(file.value)
    if (fileError) {
      formError.value = fileError
      return
    }
    const currentFile = file.value
    if (!currentFile) {
      return
    }
    const operationUserId = auth.userId
    const operationGeneration = getSessionGeneration()
    if (operationUserId === null) {
      return
    }
    if (!probeKnowledgeRecentWritable(operationUserId)) {
      formError.value = KNOWLEDGE_ADMIN_COPY.storageUnavailable
      return
    }
    uploading.value = true
    const uploadedCategory = category.value
    try {
      const response = await uploadKnowledgeDocument({
        file: currentFile,
        title: trimmedTitle,
        category: uploadedCategory,
        ...(trimmedSource ? { source: trimmedSource } : {}),
      })
      const document: LocalKnowledgeDocument = {
        documentId: response.documentId,
        title: trimmedTitle,
        category: uploadedCategory,
        fileName: response.fileName,
        processingStatus: response.processingStatus,
        updatedAt: new Date().toISOString(),
        fileType: response.fileType,
        documentVersion: response.documentVersion,
        recoveryState: null,
      }
      const persistResult = persistCaptured(operationUserId, document)
      if (!currentIdentityMatches(operationUserId, operationGeneration)) {
        return
      }
      lastUploadedId.value = response.documentId
      documents.value = persistResult.documents.length > 0 ? persistResult.documents : [document]
      if (!persistResult.ok) {
        uploadPersistFailed.value = true
        formError.value = KNOWLEDGE_ADMIN_COPY.uploadPersistFailed
      }
    } catch (caught) {
      if (!currentIdentityMatches(operationUserId, operationGeneration)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        throw caught
      }
      if (isApiError(caught) && caught.kind === ErrorKind.FORBIDDEN) {
        await router.replace({ name: 'forbidden' })
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.PAYLOAD_TOO_LARGE) {
        formError.value = KNOWLEDGE_OVERSIZE
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.VALIDATION) {
        formError.value = caught.message
        return
      }
      if (isUncertainMutationFailure(caught)) {
        uploadUncertain.value = true
        formError.value = KNOWLEDGE_ADMIN_COPY.uploadUncertain
        return
      }
      if (isApiError(caught)) {
        formError.value = caught.message
        return
      }
      throw caught
    } finally {
      if (currentIdentityMatches(operationUserId, operationGeneration)) {
        uploading.value = false
      }
    }
  }

  async function processDocument(documentId: number): Promise<void> {
    if (processingId.value !== null || userId.value === null) {
      return
    }
    const current = documents.value.find((item) => item.documentId === documentId)
    if (!current || current.processingStatus === KnowledgeProcessingStatus.READY) {
      return
    }
    const operationUserId = auth.userId
    const operationGeneration = getSessionGeneration()
    if (operationUserId === null) {
      return
    }
    processingId.value = documentId
    try {
      await processKnowledgeDocument(documentId)
      const next: LocalKnowledgeDocument = {
        ...current,
        processingStatus: KnowledgeProcessingStatus.READY,
        recoveryState: null,
        updatedAt: new Date().toISOString(),
      }
      const persistResult = adoptCapturedDocument(operationUserId, operationGeneration, next)
      if (!currentIdentityMatches(operationUserId, operationGeneration)) {
        return
      }
      if (!persistResult.ok) {
        formError.value = KNOWLEDGE_ADMIN_COPY.processPersistFailed
      }
    } catch (caught) {
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        if (currentIdentityMatches(operationUserId, operationGeneration)) {
          throw caught
        }
        return
      }
      if (!currentIdentityMatches(operationUserId, operationGeneration)) {
        if (!(isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED)) {
          const recoveryDocument = recoverProcessDocument(caught, current)
          if (recoveryDocument) {
            persistCaptured(operationUserId, recoveryDocument)
          }
        }
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.FORBIDDEN) {
        await router.replace({ name: 'forbidden' })
        return
      }
      const recoveryDocument = recoverProcessDocument(caught, current)
      if (recoveryDocument) {
        adoptCapturedDocument(operationUserId, operationGeneration, recoveryDocument)
        return
      }
      if (isApiError(caught)) {
        adoptCapturedDocument(operationUserId, operationGeneration, {
          ...current,
          recoveryState: 'UNCONFIRMED',
          updatedAt: new Date().toISOString(),
        })
      }
    } finally {
      if (processingId.value === documentId) {
        processingId.value = null
      }
    }
  }

  function recoverProcessDocument(
    caught: unknown,
    current: LocalKnowledgeDocument,
  ): LocalKnowledgeDocument | null {
    const updatedAt = new Date().toISOString()
    if (isApiError(caught) && caught.message === KNOWLEDGE_PROCESS_READY) {
      return {
        ...current,
        processingStatus: KnowledgeProcessingStatus.READY,
        recoveryState: null,
        updatedAt,
      }
    }
    if (isApiError(caught) && caught.message === KNOWLEDGE_PROCESS_BUSY) {
      return {
        ...current,
        processingStatus: KnowledgeProcessingStatus.PROCESSING,
        recoveryState: null,
        updatedAt,
      }
    }
    if (isApiError(caught) && caught.message === KNOWLEDGE_PROCESS_FORBIDDEN_STATE) {
      return { ...current, recoveryState: 'UNCONFIRMED', updatedAt }
    }
    if (isApiError(caught) && caught.message === KNOWLEDGE_SERVICE_UNAVAILABLE) {
      return { ...current, recoveryState: 'UNCONFIRMED', updatedAt }
    }
    if (isApiError(caught) && (caught.kind === ErrorKind.NOT_FOUND || caught.message === KNOWLEDGE_NOT_FOUND)) {
      return { ...current, recoveryState: 'UNAVAILABLE', updatedAt }
    }
    if (isUncertainMutationFailure(caught) || (isApiError(caught) && caught.httpStatus >= 500)) {
      return { ...current, recoveryState: 'UNCONFIRMED', updatedAt }
    }
    return null
  }

  watch(
    () => auth.userId,
    () => {
      if (!mounted) {
        return
      }
      resetIdentityScopedUi()
    },
  )

  onMounted(() => {
    mounted = true
    reloadRecent()
  })

  const stopSessionScope = subscribeSessionScope(() => {
    if (!mounted) {
      return
    }
    resetIdentityScopedUi()
  })

  onUnmounted(() => {
    mounted = false
    stopSessionScope()
  })

  return {
    title,
    category,
    source,
    file,
    fileInputKey,
    uploading,
    processingId,
    formError,
    formNoticeTone,
    uploadUncertain,
    uploadPersistFailed,
    lastUploadedId,
    documents,
    canUpload,
    titleMax: KNOWLEDGE_TITLE_MAX,
    sourceMax: KNOWLEDGE_SOURCE_MAX,
    setFile,
    upload,
    processDocument,
    reloadRecent,
  }
}
